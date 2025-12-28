package com.sovworks.eds.android.network

import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.*
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit

class WebSocketSignalingClient(
    private val serverUrl: String,
    private val myId: String
) : SignalingClient, WebSocketListener() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // WebSocket needs no timeout
        .build()
    private val gson = Gson()
    private var listener: SignalingListener? = null
    private var webSocket: WebSocket? = null
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        connect()
    }

    private fun connect() {
        val request = Request.Builder()
            .url("$serverUrl/ws?id=$myId")
            .build()
        webSocket = client.newWebSocket(request, this)
    }

    override fun sendOffer(peerId: String, sdp: SessionDescription) {
        sendPayload(peerId, "OFFER", sdp.description)
    }

    override fun sendAnswer(peerId: String, sdp: SessionDescription) {
        sendPayload(peerId, "ANSWER", sdp.description)
    }

    override fun sendIceCandidate(peerId: String, candidate: IceCandidate) {
        val candidateMap = mapOf(
            "sdp" to candidate.sdp,
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex
        )
        sendPayload(peerId, "CANDIDATE", gson.toJson(candidateMap))
    }

    override fun setListener(listener: SignalingListener) {
        this.listener = listener
    }

    private fun sendPayload(peerId: String, type: String, data: String) {
        val bodyMap = mapOf(
            "from" to myId,
            "to" to peerId,
            "type" to type,
            "data" to data
        )
        webSocket?.send(gson.toJson(bodyMap))
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val msg = gson.fromJson(text, SignalingMessage::class.java)
        processMessage(msg)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(1000, null)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        // Simple reconnect logic
        clientScope.launch {
            delay(5000)
            connect()
        }
    }

    private fun processMessage(msg: SignalingMessage) {
        when (msg.type) {
            "OFFER" -> listener?.onOfferReceived(msg.from, SessionDescription(SessionDescription.Type.OFFER, msg.data))
            "ANSWER" -> listener?.onAnswerReceived(msg.from, SessionDescription(SessionDescription.Type.ANSWER, msg.data))
            "CANDIDATE" -> {
                val candidateMap = gson.fromJson(msg.data, Map::class.java)
                val candidate = IceCandidate(
                    candidateMap["sdpMid"] as String,
                    (candidateMap["sdpMLineIndex"] as Double).toInt(),
                    candidateMap["sdp"] as String
                )
                listener?.onIceCandidateReceived(msg.from, candidate)
            }
        }
    }

    override fun shutdown() {
        clientScope.cancel()
        webSocket?.close(1000, "Shutdown")
    }

    private data class SignalingMessage(val from: String, val type: String, val data: String)
}
