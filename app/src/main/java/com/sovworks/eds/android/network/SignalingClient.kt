package com.sovworks.eds.android.network

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

/**
 * Interface für den WebRTC Signaling Mechanismus.
 */
interface SignalingClient {
    fun sendOffer(peerId: String, sdp: SessionDescription)
    fun sendAnswer(peerId: String, sdp: SessionDescription)
    fun sendIceCandidate(peerId: String, candidate: IceCandidate)
    
    fun setListener(listener: SignalingListener)
    fun shutdown() {}
}

/**
 * Listener für eingehende Signaling-Ereignisse.
 */
interface SignalingListener {
    fun onOfferReceived(peerId: String, sdp: SessionDescription)
    fun onAnswerReceived(peerId: String, sdp: SessionDescription)
    fun onIceCandidateReceived(peerId: String, candidate: IceCandidate)
}
