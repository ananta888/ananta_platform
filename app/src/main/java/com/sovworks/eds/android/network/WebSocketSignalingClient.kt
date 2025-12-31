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
    private val visibility: String = "private",
    private val autoReconnect: Boolean = true,
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
            SignalingStatusTracker.update(serverUrl, SignalingConnectionStatus.CONNECTING)
            logDebug("Connecting to $serverUrl")
            webSocket = client.newWebSocket(request, this)
        } catch (e: Exception) {
            SignalingStatusTracker.update(serverUrl, SignalingConnectionStatus.ERROR)
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

    override fun sendConnectionRequest(peerId: String) {
        sendPayload(peerId, "CONNECT_REQUEST", "")
    }

    override fun sendConnectionAccept(peerId: String) {
        sendPayload(peerId, "CONNECT_ACCEPT", "")
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
        logDebug("sendPayload -> $peerId ($type)")
        webSocket?.send(gson.toJson(bodyMap))
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        SignalingStatusTracker.update(serverUrl, SignalingConnectionStatus.CONNECTED)
        logDebug("Connected to $serverUrl")
        val register = mapOf(
            "type" to "register",
            "peerId" to myId,
            "publicKey" to myPublicKey,
            "visibility" to visibility
        )
        webSocket.send(gson.toJson(register))
        requestPublicPeers()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val root = gson.fromJson(text, Map::class.java)
        val type = root["type"] as? String
        if (type == "ice_servers") {
            val configs = parseIceServers(root["iceServers"])
            if (configs.isNotEmpty()) {
                IceServersRegistry.updateFromConfigs(configs)
            }
            return
        }
        if (type == "relay") {
            val fromKey = root["fromPublicKey"] as? String ?: return
            val payload = root["payload"] as? String ?: return
            logDebug("Received relay payload from $fromKey (${payload.length} chars)")
            listener?.onRelayPayloadReceived(fromKey, payload)
            return
        }
        if (type == "public_peers") {
            val peers = (root["peers"] as? List<*>)?.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                val publicKey = map["publicKey"] as? String ?: return@mapNotNull null
                val peerId = map["peerId"] as? String
                PublicPeer(publicKey = publicKey, peerId = peerId)
            }.orEmpty()
            logDebug("Received public_peers: ${peers.size}")
            PublicPeersDirectory.update(peers)
            return
        }
        if (type == "public_keys") {
            val peers = (root["keys"] as? List<*>)?.mapNotNull { key ->
                val publicKey = key as? String ?: return@mapNotNull null
                PublicPeer(publicKey = publicKey, peerId = null)
            }.orEmpty()
            logDebug("Received public_keys: ${peers.size}")
            PublicPeersDirectory.update(peers)
            return
        }
        val msg = gson.fromJson(text, SignalingMessage::class.java)
        processMessage(msg)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        SignalingStatusTracker.update(serverUrl, SignalingConnectionStatus.DISCONNECTED)
        webSocket.close(1000, null)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        SignalingStatusTracker.update(serverUrl, SignalingConnectionStatus.ERROR)
        logWarn("WebSocket failure for $serverUrl: ${t.message}", t)
        if (autoReconnect) {
            clientScope.launch {
                delay(5000)
                connect()
            }
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
            "CONNECT_REQUEST" -> {
                listener?.onConnectionRequestReceived(msg.fromPublicKey)
            }
            "CONNECT_ACCEPT" -> {
                listener?.onConnectionAcceptReceived(msg.fromPublicKey)
            }
        }
    }

    override fun shutdown() {
        SignalingStatusTracker.update(serverUrl, SignalingConnectionStatus.DISCONNECTED)
        clientScope.cancel()
        webSocket?.close(1000, "Shutdown")
    }

    fun requestPublicPeers() {
        val bodyMap = mapOf("type" to "list_public")
        webSocket?.send(gson.toJson(bodyMap))
    }

    fun sendRelayPayload(peerIds: List<String>, payload: String) {
        if (peerIds.isEmpty()) return
        val bodyMap = mapOf(
            "type" to "relay",
            "toPeers" to peerIds,
            "payload" to payload
        )
        logDebug("sendRelayPayload -> ${peerIds.size} peers (${payload.length} chars)")
        webSocket?.send(gson.toJson(bodyMap))
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

    private fun parseIceServers(payload: Any?): List<ConnectionMetadata.IceServerConfig> {
        val entries = payload as? List<*> ?: return emptyList()
        return entries.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val urls = when (val raw = map["urls"]) {
                is String -> listOf(raw)
                is List<*> -> raw.mapNotNull { it as? String }
                else -> emptyList()
            }
            if (urls.isEmpty()) return@mapNotNull null
            val username = map["username"] as? String
            val credential = map["credential"] as? String
            ConnectionMetadata.IceServerConfig(
                urls = urls,
                username = username,
                credential = credential
            )
        }
    }
}
