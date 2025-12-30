package com.sovworks.eds.android.network

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class MultiSignalingClient(private val clients: List<SignalingClient>) : SignalingClient {
    
    override fun sendOffer(peerId: String, sdp: SessionDescription) {
        clients.forEach { it.sendOffer(peerId, sdp) }
    }

    override fun sendAnswer(peerId: String, sdp: SessionDescription) {
        clients.forEach { it.sendAnswer(peerId, sdp) }
    }

    override fun sendIceCandidate(peerId: String, candidate: IceCandidate) {
        clients.forEach { it.sendIceCandidate(peerId, candidate) }
    }

    override fun sendConnectionRequest(peerId: String) {
        clients.forEach { it.sendConnectionRequest(peerId) }
    }

    override fun sendConnectionAccept(peerId: String) {
        clients.forEach { it.sendConnectionAccept(peerId) }
    }

    override fun setListener(listener: SignalingListener) {
        clients.forEach { it.setListener(listener) }
    }

    override fun shutdown() {
        clients.forEach { it.shutdown() }
    }
    
    fun getClients(): List<SignalingClient> = clients

    fun pollMessages() {
        clients.forEach { client ->
            if (client is HttpSignalingClient) {
                client.pollMessages()
            }
        }
    }
}
