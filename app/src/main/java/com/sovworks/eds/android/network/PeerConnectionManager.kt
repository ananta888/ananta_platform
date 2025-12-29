package com.sovworks.eds.android.network

import android.content.Context
import com.google.gson.Gson
import com.sovworks.eds.android.identity.IdentityManager
import com.sovworks.eds.android.trust.TrustNetworkManager
import com.sovworks.eds.android.trust.TrustNetworkPackage
import com.sovworks.eds.android.trust.TrustStore
import com.sovworks.eds.android.trust.TrustedKey
import kotlinx.coroutines.*
import android.util.Log
import org.webrtc.*
import java.util.concurrent.ConcurrentHashMap

interface PeerConnectionListener {
    fun onConnectionStateChange(peerId: String, state: PeerConnection.IceConnectionState)
}

class PeerConnectionManager(
    val context: Context,
    private val signalingClient: SignalingClient,
    private val myId: String
) : SignalingListener, DataChannelListener {
    private val tag = "PeerConnectionManager"

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val pendingIceCandidates = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private var listener: PeerConnectionListener? = null
    private var statsJob: Job? = null

    init {
        startStatsPolling()
    }

    private fun startStatsPolling() {
        statsJob = managerScope.launch {
            while (isActive) {
                delay(3000)
                peerConnections.forEach { (peerId, pc) ->
                    pc.getStats { report ->
                        processStats(peerId, report)
                    }
                }
            }
        }
    }

    private fun processStats(peerId: String, report: RTCStatsReport) {
        var latency = 0L
        var packetLoss = 0.0
        var bitrate = 0.0

        for (stats in report.statsMap.values) {
            when (stats.type) {
                "candidate-pair" -> {
                    if (stats.members["state"] == "succeeded") {
                        val rtt = stats.members["currentRoundTripTime"] as? Double ?: 0.0
                        latency = (rtt * 1000).toLong()
                    }
                }
                "inbound-rtp" -> {
                    packetLoss = (stats.members["packetsLost"] as? Int ?: 0).toDouble()
                }
                "data-channel" -> {
                    val received = (stats.members["bytesReceived"] as? Long ?: 0L).toDouble()
                    bitrate = received * 8 / 1024 // Sehr vereinfacht: Total KBits
                }
            }
        }

        PeerConnectionRegistry.updateStats(
            peerId,
            PeerConnectionRegistry.PeerStats(latency, packetLoss, bitrate)
        )

        if (latency > 0) {
            com.sovworks.eds.android.trust.TrustRankingManager.recordLatency(context, peerId, latency)
        }
    }

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )
    
    private val multiplexer = DataChannelMultiplexer(context)
    private val vaultFileReceiver = VaultFileReceiver(context)
    private val vaultFileSender = VaultFileSender(multiplexer)

    fun getMultiplexer(): DataChannelMultiplexer = multiplexer

    fun sendFile(peerId: String, file: com.sovworks.eds.fs.File) {
        vaultFileSender.sendFile(peerId, file)
    }

    fun sendFileStream(peerId: String, fileName: String, totalBytes: Long?, input: java.io.InputStream) {
        managerScope.launch {
            vaultFileSender.sendStream(peerId, fileName, totalBytes, input)
        }
    }

    fun setListener(listener: PeerConnectionListener) {
        this.listener = listener
    }

    init {
        WebRTCManager.initialize(context)
        signalingClient.setListener(this)
        multiplexer.addListener(vaultFileReceiver)
        multiplexer.addListener(this)
    }

    @Synchronized
    private fun getOrCreatePeerConnection(peerId: String): PeerConnection? {
        peerConnections[peerId]?.let { return it }

        val pc = WebRTCManager.createPeerConnection(iceServers, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                newState?.let {
                    listener?.onConnectionStateChange(peerId, it)
                    PeerConnectionRegistry.updateStatus(peerId, it.name.lowercase())
                    if (it == PeerConnection.IceConnectionState.CONNECTED) {
                        onPeerConnected(peerId)
                    }
                    if (it == PeerConnection.IceConnectionState.DISCONNECTED ||
                        it == PeerConnection.IceConnectionState.FAILED) {
                        // Automatischer Reconnect-Versuch nach Verzögerung
                        managerScope.launch {
                            delay(3000)
                            initiateConnection(peerId)
                        }
                    }
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    signalingClient.sendIceCandidate(peerId, it)
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {
                dataChannel?.let {
                    multiplexer.onDataChannelCreated(peerId, it, false)
                }
            }
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
        })

        pc?.let { peerConnections[peerId] = it }
        return pc
    }

    fun initiateConnection(peerId: String) {
        logDebug("initiateConnection -> $peerId")
        PeerConnectionRegistry.updateStatus(peerId, "connecting")
        val pc = getOrCreatePeerConnection(peerId) ?: return
        
        // DataChannels erstellen (nur eine Seite muss dies tun)
        val chatChannel = pc.createDataChannel("chat", DataChannel.Init())
        val fileChannel = pc.createDataChannel("file", DataChannel.Init())
        val discoveryChannel = pc.createDataChannel("discovery", DataChannel.Init())
        val offlineMsgChannel = pc.createDataChannel("offline_msg", DataChannel.Init())

        multiplexer.onDataChannelCreated(peerId, chatChannel, true)
        multiplexer.onDataChannelCreated(peerId, fileChannel, true)
        multiplexer.onDataChannelCreated(peerId, discoveryChannel, true)
        multiplexer.onDataChannelCreated(peerId, offlineMsgChannel, true)

        val constraints = MediaConstraints()
        
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            logDebug("sendOffer -> $peerId")
                            signalingClient.sendOffer(peerId, it)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    override fun onOfferReceived(peerId: String, sdp: SessionDescription) {     
        logDebug("onOfferReceived <- $peerId")
        val pc = getOrCreatePeerConnection(peerId) ?: return
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                drainIceCandidates(peerId, pc)
                val constraints = MediaConstraints()
                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerSdp: SessionDescription?) {
                        answerSdp?.let {
                            pc.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    logDebug("sendAnswer -> $peerId")
                                    signalingClient.sendAnswer(peerId, it)      
                                }
                                override fun onCreateFailure(p0: String?) {}
                                override fun onSetFailure(p0: String?) {}
                            }, it)
                        }
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, constraints)
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, sdp)
    }

    override fun onAnswerReceived(peerId: String, sdp: SessionDescription) {    
        logDebug("onAnswerReceived <- $peerId")
        val pc = peerConnections[peerId] ?: return
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                drainIceCandidates(peerId, pc)
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, sdp)
    }

    private fun onPeerConnected(peerId: String) {
        val identity = IdentityManager.loadIdentity(context) ?: return

        if (peerId == identity.publicKeyBase64) {
            val syncJson = com.sovworks.eds.android.identity.IdentitySyncManager.exportTrustSync(context)
            multiplexer.sendMessage(peerId, "IDENTITY_TRUST_SYNC:$syncJson")
        }

        sendTrustPackage(peerId)
    }

    override fun onIceCandidateReceived(peerId: String, candidate: IceCandidate) {
        logDebug("onIceCandidateReceived <- $peerId")
        val pc = peerConnections[peerId]
        if (pc != null && pc.remoteDescription != null) {
            pc.addIceCandidate(candidate)
        } else {
            pendingIceCandidates.getOrPut(peerId) { mutableListOf() }.add(candidate)
        }
    }

    private fun drainIceCandidates(peerId: String, pc: PeerConnection) {
        pendingIceCandidates.remove(peerId)?.forEach {
            pc.addIceCandidate(it)
        }
    }

    fun closeConnection(peerId: String) {
        logDebug("closeConnection -> $peerId")
        peerConnections.remove(peerId)?.dispose()
        PeerConnectionRegistry.updateStatus(peerId, "closed")
    }

    fun broadcastTrustPackage() {
        peerConnections.keys.forEach { peerId ->
            sendTrustPackage(peerId)
        }
    }

    private fun sendTrustPackage(peerId: String) {
        val identity = IdentityManager.loadIdentity(context) ?: return
        val trustStore = TrustStore.getInstance(context)
        val trustedKeys = trustStore.allKeys.values.filter { it.status == TrustedKey.TrustStatus.TRUSTED }
        
        val rotation = identity.getRotationCertificate()
        val rotations = if (rotation != null) listOf(rotation) else emptyList()
        val pkg = TrustNetworkManager.exportTrustNetwork(identity, trustedKeys, rotations) ?: return
        val json = Gson().toJson(pkg)
        multiplexer.sendMessage(peerId, "TRUST_PACKAGE:$json")
    }

    fun shutdown() {
        managerScope.cancel()
        peerConnections.values.forEach { it.dispose() }
        peerConnections.clear()
    }

    override fun onMessageReceived(peerId: String, message: String) {
        if (message.startsWith("TRUST_PACKAGE:")) {
            val json = message.removePrefix("TRUST_PACKAGE:")
            try {
                val pkg: TrustNetworkPackage = Gson().fromJson(json, TrustNetworkPackage::class.java)
                TrustNetworkManager.verifyAndImportNetwork(context, pkg)
            } catch (e: Exception) {
                // Ignore malformed trust packages
            }
        } else if (message.startsWith("FILE_REQUEST:")) {
            val fileName = message.substringAfter("FILE_REQUEST:")
            handleFileRequest(peerId, fileName)
        } else if (message.startsWith("IDENTITY_TRUST_SYNC:")) {
            val json = message.removePrefix("IDENTITY_TRUST_SYNC:")
            com.sovworks.eds.android.identity.IdentitySyncManager.importTrustSync(context, json)
        }
    }

    private fun handleFileRequest(peerId: String, fileName: String) {
        val lm = com.sovworks.eds.locations.LocationsManager.getLocationsManager(context)
        val location = lm.getLoadedLocations(false).filterIsInstance<com.sovworks.eds.android.locations.EncFsLocation>().firstOrNull { it.isOpen }
        
        if (location != null) {
            try {
                val currentPath = location.getCurrentPath()
                val filePath = currentPath.combine(fileName)
                if (filePath.exists() && filePath.isFile()) {
                    sendFile(peerId, filePath.getFile())
                }
            } catch (e: Exception) {
                // Ignore errors in file request handling
            }
        }
    }

    override fun onBinaryReceived(peerId: String, data: ByteArray) {}

    private fun logDebug(message: String) {
        try {
            Log.d(tag, message)
        } catch (_: Throwable) {
            // Ignore logging failures in JVM unit tests.
        }
    }
}
