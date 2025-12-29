package com.sovworks.eds.android.network

import com.google.gson.Gson
import kotlinx.coroutines.*
import android.util.Log
import okhttp3.*
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit

class WebSocketSignalingClient(
    private val serverUrl: String,
    private val myId: String,
    private val myPublicKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // WebSocket needs no timeout
        .build(),
    private val clientScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : SignalingClient, WebSocketListener() {
    private val gson = Gson()
    private var listener: SignalingListener? = null
    private var webSocket: WebSocket? = null
    private val tag = "WebSocketSignaling"

    init {
        connect()
    }

    private fun connect() {
        try {
            val requestUrl = normalizeWebSocketUrl(serverUrl)
            val request = Request.Builder()
                .url(requestUrl)
                .build()
            logDebug("Connecting to $serverUrl")
            webSocket = client.newWebSocket(request, this)
        } catch (e: Exception) {
            logWarn("Failed to connect to $serverUrl: ${e.message}", e)
        }
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
            "type" to "signal",
            "to" to peerId,
            "toPublicKey" to peerId,
            "payload" to mapOf(
                "type" to type,
                "data" to data
            )
        )
        webSocket?.send(gson.toJson(bodyMap))
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        logDebug("Connected to $serverUrl")
        val register = mapOf(
            "type" to "register",
            "peerId" to myId,
            "publicKey" to myPublicKey
        )
        webSocket.send(gson.toJson(register))
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val msg = gson.fromJson(text, SignalingMessage::class.java)
        processMessage(msg)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(1000, null)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        logWarn("WebSocket failure for $serverUrl: ${t.message}", t)
        // Simple reconnect logic
        clientScope.launch {
            delay(5000)
            connect()
        }
    }

    private fun logDebug(message: String) {
        try {
            Log.d(tag, message)
        } catch (_: Throwable) {
            // Ignore logging failures in JVM unit tests.
        }
    }

    private fun logWarn(message: String, throwable: Throwable) {
        try {
            Log.w(tag, message, throwable)
        } catch (_: Throwable) {
            // Ignore logging failures in JVM unit tests.
        }
    }

    private fun normalizeWebSocketUrl(url: String): String {
        return when {
            url.startsWith("ws://") -> "http://" + url.removePrefix("ws://")
            url.startsWith("wss://") -> "https://" + url.removePrefix("wss://")
            else -> url
        }
    }

    private fun processMessage(msg: SignalingMessage) {
        if (msg.type != "signal" || msg.payload == null || msg.fromPublicKey.isNullOrBlank()) return
        when (msg.payload.type) {
            "OFFER" -> listener?.onOfferReceivedFromKey(
                msg.fromPublicKey,
                SessionDescription(SessionDescription.Type.OFFER, msg.payload.data)
            )
            "ANSWER" -> listener?.onAnswerReceivedFromKey(
                msg.fromPublicKey,
                SessionDescription(SessionDescription.Type.ANSWER, msg.payload.data)
            )
            "CANDIDATE" -> {
                val candidateMap = gson.fromJson(msg.payload.data, Map::class.java)
                val candidate = IceCandidate(
                    candidateMap["sdpMid"] as String,
                    (candidateMap["sdpMLineIndex"] as Double).toInt(),
                    candidateMap["sdp"] as String
                )
                listener?.onIceCandidateReceivedFromKey(msg.fromPublicKey, candidate)
            }
        }
    }

    override fun shutdown() {
        clientScope.cancel()
        webSocket?.close(1000, "Shutdown")
    }

    private data class SignalingMessage(
        val type: String,
        val from: String?,
        val fromPublicKey: String?,
        val payload: SignalingPayload?
    )

    private data class SignalingPayload(
        val type: String,
        val data: String
    )
}
