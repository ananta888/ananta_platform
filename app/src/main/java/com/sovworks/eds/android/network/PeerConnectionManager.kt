package com.sovworks.eds.android.network

import android.content.Context
import com.google.gson.Gson
import com.sovworks.eds.android.identity.IdentityManager
import com.sovworks.eds.android.trust.TrustNetworkManager
import com.sovworks.eds.android.trust.TrustNetworkPackage
import com.sovworks.eds.android.trust.TrustStore
import com.sovworks.eds.android.trust.TrustedKey
import com.sovworks.eds.android.network.PeerDirectory
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
    private val statsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val pendingIceCandidates = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private val lastConnectAttempt = ConcurrentHashMap<String, Long>()
    private val approvedPeers = ConcurrentHashMap<String, Boolean>()
    private val connectionTimeouts = ConcurrentHashMap<String, Job>()
    private var listener: PeerConnectionListener? = null
    private var statsJob: Job? = null
    private val connectBackoffMillis = 4000L
    private val connectionTimeoutMillis = 10000L

    init {
        startStatsPolling()
    }

    private fun startStatsPolling() {
        statsJob = statsScope.launch {
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

        val iceConfigs = IceServersRegistry.getConfigs()
        logDebug("iceServers -> $peerId count=${iceConfigs.size}")
        iceConfigs.forEach { config ->
            logDebug("iceServer -> $peerId urls=${config.urls.joinToString(",")} auth=${!config.username.isNullOrBlank()}")
        }

        val pc = WebRTCManager.createPeerConnection(
            IceServersRegistry.getIceServers(),
            object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                newState?.let {
                    logDebug("iceConnectionState -> $peerId = ${it.name.lowercase()}")
                    listener?.onConnectionStateChange(peerId, it)
                    val status = if (it == PeerConnection.IceConnectionState.CONNECTED ||
                        it == PeerConnection.IceConnectionState.COMPLETED
                    ) {
                        "connected"
                    } else {
                        it.name.lowercase()
                    }
                    PeerConnectionRegistry.updateStatus(peerId, status)
                    if (it == PeerConnection.IceConnectionState.CONNECTED ||
                        it == PeerConnection.IceConnectionState.COMPLETED
                    ) {
                        onPeerConnected(peerId)
                        cancelConnectionTimeout(peerId)
                    }
                    if (it == PeerConnection.IceConnectionState.DISCONNECTED ||
                        it == PeerConnection.IceConnectionState.FAILED) {
                        approvedPeers.remove(peerId)
                        cancelConnectionTimeout(peerId)
                    }
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                newState?.let {
                    logDebug("iceGatheringState -> $peerId = ${it.name.lowercase()}")
                }
            }
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    logDebug("onIceCandidate -> $peerId ${formatCandidate(it)}")
                    signalingClient.sendIceCandidate(peerId, it)
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {
                dataChannel?.let {
                    ensureTrustedKey(peerId)
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
        val identity = IdentityManager.loadIdentity(context)
        if (identity?.publicKeyBase64 == peerId) {
            logDebug("initiateConnection skipped (self) -> $peerId")
            return
        }
        if (!isApproved(peerId)) {
            logDebug("initiateConnection skipped (not approved) -> $peerId")
            return
        }
        val currentStatus = PeerConnectionRegistry.state.value
            .firstOrNull { it.peerId == peerId }
            ?.status
        if (currentStatus == "connecting" || currentStatus == "connected") {
            logDebug("initiateConnection skipped (status=$currentStatus) -> $peerId")
            return
        }
        val now = System.currentTimeMillis()
        val lastAttempt = lastConnectAttempt[peerId] ?: 0L
        if (now - lastAttempt < connectBackoffMillis) {
            logDebug("initiateConnection skipped (backoff) -> $peerId")
            return
        }
        lastConnectAttempt[peerId] = now
        logDebug("initiateConnection -> $peerId")
        ensureTrustedKey(peerId)
        PeerConnectionRegistry.updateStatus(peerId, "connecting")
        startConnectionTimeout(peerId)
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
                        override fun onSetFailure(p0: String?) {
                            logDebug("setLocalDescription (offer) failed -> $peerId reason=$p0")
                        }
                    }, it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {
                logDebug("createOffer failed -> $peerId reason=$p0")
            }
            override fun onSetFailure(p0: String?) {
                logDebug("setLocalDescription (offer) failed -> $peerId reason=$p0")
            }
        }, constraints)
    }

    fun requestConnection(peerId: String) {
        val identity = IdentityManager.loadIdentity(context)
        if (identity?.publicKeyBase64 == peerId) {
            logDebug("requestConnection skipped (self) -> $peerId")
            return
        }
        PeerConnectionRequestRegistry.markOutgoing(peerId)
        PeerConnectionRegistry.updateStatus(peerId, "requesting")
        signalingClient.sendConnectionRequest(peerId)
        logDebug("sendConnectionRequest -> $peerId")
    }

    fun acceptConnection(peerId: String) {
        PeerConnectionRequestRegistry.clearIncoming(peerId)
        approvedPeers[peerId] = true
        signalingClient.sendConnectionAccept(peerId)
        logDebug("sendConnectionAccept -> $peerId")
    }

    override fun onOfferReceived(peerId: String, sdp: SessionDescription) {
        if (!isApproved(peerId)) {
            logDebug("onOfferReceived ignored (not approved) <- $peerId")
            return
        }
        ensureTrustedKey(peerId)
        logDebug("onOfferReceived <- $peerId")
        PeerConnectionRegistry.updateStatus(peerId, "connecting")
        startConnectionTimeout(peerId)
        val polite = isPolitePeer(peerId)
        val pc = getOrCreatePeerConnection(peerId) ?: return
        if (pc.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
            if (!polite) {
                logDebug("offer glare: ignoring offer from $peerId (impolite)")
                return
            }
            logDebug("offer glare: resetting local offer for $peerId (polite)")
            resetPeerConnection(peerId)
        }
        val nextPc = getOrCreatePeerConnection(peerId) ?: return
        nextPc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                drainIceCandidates(peerId, nextPc)
                val constraints = MediaConstraints()
                nextPc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerSdp: SessionDescription?) {
                        answerSdp?.let {
                            nextPc.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    logDebug("sendAnswer -> $peerId")
                                    signalingClient.sendAnswer(peerId, it)
                                }
                                override fun onCreateFailure(p0: String?) {}
                                override fun onSetFailure(p0: String?) {
                                    logDebug("setLocalDescription (answer) failed -> $peerId reason=$p0")
                                }
                            }, it)
                        }
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(p0: String?) {
                        logDebug("createAnswer failed -> $peerId reason=$p0")
                    }
                    override fun onSetFailure(p0: String?) {
                        logDebug("setRemoteDescription (offer) failed -> $peerId reason=$p0")
                    }
                }, constraints)
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {
                logDebug("setRemoteDescription (offer) failed -> $peerId reason=$p0")
            }
        }, sdp)
    }

    override fun onAnswerReceived(peerId: String, sdp: SessionDescription) {    
        ensureTrustedKey(peerId)
        logDebug("onAnswerReceived <- $peerId")
        val pc = peerConnections[peerId] ?: return
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                drainIceCandidates(peerId, pc)
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {
                logDebug("setRemoteDescription (answer) failed -> $peerId reason=$p0")
            }
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
        if (!isApproved(peerId)) {
            logDebug("onIceCandidateReceived ignored (not approved) <- $peerId")
            return
        }
        ensureTrustedKey(peerId)
        logDebug("onIceCandidateReceived <- $peerId ${formatCandidate(candidate)}")
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
        cancelConnectionTimeout(peerId)
        peerConnections.remove(peerId)?.dispose()
        approvedPeers.remove(peerId)
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
        statsScope.cancel()
        peerConnections.values.forEach { it.dispose() }
        peerConnections.clear()
        approvedPeers.clear()
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

    override fun onConnectionRequestReceived(peerId: String) {
        ensureTrustedKey(peerId)
        PeerConnectionRequestRegistry.markIncoming(peerId)
        PeerConnectionRegistry.updateStatus(peerId, "request")
        logDebug("onConnectionRequestReceived <- $peerId")
    }

    override fun onConnectionAcceptReceived(peerId: String) {
        val state = PeerConnectionRequestRegistry.get(peerId)
        approvedPeers[peerId] = true
        PeerConnectionRequestRegistry.clearOutgoing(peerId)
        PeerConnectionRequestRegistry.clearIncoming(peerId)
        logDebug("onConnectionAcceptReceived <- $peerId")
        if (shouldInitiateAfterAccept(peerId, state)) {
            initiateConnection(peerId)
        }
    }

    override fun onRelayPayloadReceived(peerId: String, payload: String) {
        SignalingRelayBus.dispatch(peerId, payload)
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

    private fun ensureTrustedKey(peerId: String) {
        val trustStore = TrustStore.getInstance(context)
        if (trustStore.getKey(peerId) != null) return
        val publicPeerId = PeerDirectory.state.value.entries.firstOrNull { it.publicKey == peerId }?.peerIdFromServer
        val name = publicPeerId ?: "Peer"
        val key = TrustedKey(peerId, peerId, name)
        key.peerId = publicPeerId
        trustStore.addKey(key)
    }

    private fun resetPeerConnection(peerId: String) {
        cancelConnectionTimeout(peerId)
        peerConnections.remove(peerId)?.dispose()
        pendingIceCandidates.remove(peerId)
    }

    private fun isApproved(peerId: String): Boolean = approvedPeers[peerId] == true

    private fun shouldInitiateAfterAccept(
        peerId: String,
        state: PeerConnectionRequestRegistry.RequestState?
    ): Boolean {
        if (state == null) return false
        if (!state.outgoing) return false
        return !state.incoming || isPolitePeer(peerId)
    }

    private fun isPolitePeer(peerId: String): Boolean {
        val myKey = IdentityManager.loadIdentity(context)?.publicKeyBase64
        if (myKey.isNullOrBlank()) return true
        return myKey < peerId
    }

    private fun logDebug(message: String) {
        try {
            Log.d(tag, message)
        } catch (_: Throwable) {
            // Ignore logging failures in JVM unit tests.
        }
    }

    private fun startConnectionTimeout(peerId: String) {
        cancelConnectionTimeout(peerId)
        connectionTimeouts[peerId] = managerScope.launch {
            delay(connectionTimeoutMillis)
            val status = PeerConnectionRegistry.state.value
                .firstOrNull { it.peerId == peerId }
                ?.status
            if (status == "connecting" || status == "checking" || status == "new") {
                logDebug("connect timeout -> $peerId (status=$status)")
                PeerConnectionRequestRegistry.clear(peerId)
                approvedPeers.remove(peerId)
                peerConnections.remove(peerId)?.dispose()
                PeerConnectionRegistry.updateStatus(peerId, "failed")
            }
        }
    }

    private fun cancelConnectionTimeout(peerId: String) {
        connectionTimeouts.remove(peerId)?.cancel()
    }

    private fun formatCandidate(candidate: IceCandidate): String {
        val sdp = candidate.sdp ?: ""
        val type = Regex("\\btyp\\s+(\\w+)").find(sdp)?.groupValues?.get(1) ?: "unknown"
        return "type=$type sdp=${candidate.sdp}"
    }
}
