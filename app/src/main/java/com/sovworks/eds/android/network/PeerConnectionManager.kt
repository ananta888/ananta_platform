package com.sovworks.eds.android.network

import android.content.Context
import org.webrtc.*
import java.util.concurrent.ConcurrentHashMap

interface PeerConnectionListener {
    fun onConnectionStateChange(peerId: String, state: PeerConnection.IceConnectionState)
}

class PeerConnectionManager(
    private val context: Context,
    private val signalingClient: SignalingClient,
    private val myId: String
) : SignalingListener {

    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private var listener: PeerConnectionListener? = null
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )
    
    private val multiplexer = DataChannelMultiplexer(this)
    private val vaultFileReceiver = VaultFileReceiver(context)

    fun getMultiplexer(): DataChannelMultiplexer = multiplexer

    fun setListener(listener: PeerConnectionListener) {
        this.listener = listener
    }

    init {
        WebRTCManager.initialize(context)
        signalingClient.setListener(this)
        multiplexer.addListener(vaultFileReceiver)
    }

    @Synchronized
    private fun getOrCreatePeerConnection(peerId: String): PeerConnection? {
        peerConnections[peerId]?.let { return it }

        val pc = WebRTCManager.createPeerConnection(iceServers, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                newState?.let {
                    listener?.onConnectionStateChange(peerId, it)
                    if (it == PeerConnection.IceConnectionState.DISCONNECTED ||
                        it == PeerConnection.IceConnectionState.FAILED) {
                        // Automatischer Reconnect-Versuch nach Verzögerung
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            initiateConnection(peerId)
                        }, 3000)
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
                    multiplexer.onDataChannelCreated(peerId, it)
                }
            }
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
        })

        pc?.let { peerConnections[peerId] = it }
        return pc
    }

    fun initiateConnection(peerId: String) {
        val pc = getOrCreatePeerConnection(peerId) ?: return
        
        // DataChannels erstellen (nur eine Seite muss dies tun)
        val chatChannel = pc.createDataChannel("chat", DataChannel.Init())
        val fileChannel = pc.createDataChannel("file", DataChannel.Init())
        
        multiplexer.onDataChannelCreated(peerId, chatChannel)
        multiplexer.onDataChannelCreated(peerId, fileChannel)

        val constraints = MediaConstraints()
        
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
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
        val pc = getOrCreatePeerConnection(peerId) ?: return
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                val constraints = MediaConstraints()
                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerSdp: SessionDescription?) {
                        answerSdp?.let {
                            pc.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onSetSuccess() {
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
        val pc = peerConnections[peerId] ?: return
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, sdp)
    }

    override fun onIceCandidateReceived(peerId: String, candidate: IceCandidate) {
        val pc = peerConnections[peerId] ?: return
        pc.addIceCandidate(candidate)
    }

    fun closeConnection(peerId: String) {
        peerConnections.remove(peerId)?.dispose()
    }
}
