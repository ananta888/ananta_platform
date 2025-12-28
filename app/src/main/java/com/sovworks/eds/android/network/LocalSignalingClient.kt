package com.sovworks.eds.android.network

import android.content.Context
import android.os.Build
import com.google.gson.Gson
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class LocalSignalingClient(
    context: Context,
    private val myId: String
) : SignalingClient {
    private val gson = Gson()
    private val discoveryManager = DiscoveryManager(context)
    private val peerDirectory = ConcurrentHashMap<String, PeerEndpoint>()
    private val ioExecutor = Executors.newCachedThreadPool()
    private val server = LocalSignalingServer(ioExecutor, gson) { message ->
        if (message.to != myId) {
            return@LocalSignalingServer
        }
        when (message.type) {
            "OFFER" -> listener?.onOfferReceived(
                message.from,
                SessionDescription(SessionDescription.Type.OFFER, message.data)
            )
            "ANSWER" -> listener?.onAnswerReceived(
                message.from,
                SessionDescription(SessionDescription.Type.ANSWER, message.data)
            )
            "CANDIDATE" -> {
                val candidateMap = gson.fromJson(message.data, Map::class.java)
                val candidate = IceCandidate(
                    candidateMap["sdpMid"] as String,
                    (candidateMap["sdpMLineIndex"] as Double).toInt(),
                    candidateMap["sdp"] as String
                )
                listener?.onIceCandidateReceived(message.from, candidate)
            }
        }
    }
    private var listener: SignalingListener? = null

    init {
        server.start()
        discoveryManager.registerService(server.port, myId)
        discoveryManager.discoverPeers { serviceInfo ->
            val peerId = parsePeerId(serviceInfo.serviceName) ?: return@discoverPeers
            if (peerId == myId) {
                return@discoverPeers
            }
            val host = if (Build.VERSION.SDK_INT >= 34) {
                serviceInfo.hostAddresses.firstOrNull()?.hostAddress
            } else {
                @Suppress("DEPRECATION")
                serviceInfo.host?.hostAddress
            } ?: return@discoverPeers
            peerDirectory[peerId] = PeerEndpoint(host, serviceInfo.port)
            PeerConnectionRegistry.updatePeer(peerId, "$host:${serviceInfo.port}")
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

    override fun shutdown() {
        discoveryManager.stop()
        server.stop()
        ioExecutor.shutdownNow()
    }

    private fun sendPayload(peerId: String, type: String, data: String) {
        val endpoint = peerDirectory[peerId] ?: return
        ioExecutor.execute {
            val payload = SignalingPayload(
                from = myId,
                to = peerId,
                type = type,
                data = data
            )
            sendToEndpoint(endpoint, payload)
        }
    }

    private fun sendToEndpoint(endpoint: PeerEndpoint, payload: SignalingPayload) {
        try {
            Socket(endpoint.host, endpoint.port).use { socket ->
                BufferedWriter(OutputStreamWriter(socket.getOutputStream())).use { writer ->
                    writer.write(gson.toJson(payload))
                    writer.newLine()
                    writer.flush()
                }
            }
        } catch (e: Exception) {
            // Best-effort local signaling; failures can be retried by caller.
        }
    }

    private fun parsePeerId(serviceName: String): String? {
        val prefix = "Ananta-"
        return if (serviceName.startsWith(prefix)) {
            serviceName.removePrefix(prefix)
        } else {
            null
        }
    }

    private data class PeerEndpoint(val host: String, val port: Int)

    private data class SignalingPayload(
        val from: String,
        val to: String,
        val type: String,
        val data: String
    )

    private class LocalSignalingServer(
        private val ioExecutor: java.util.concurrent.ExecutorService,
        private val gson: Gson,
        private val onMessage: (SignalingPayload) -> Unit
    ) {
        private val serverSocket = ServerSocket(0)
        val port: Int = serverSocket.localPort
        @Volatile
        private var running = false

        fun start() {
            running = true
            ioExecutor.execute {
                while (running) {
                    try {
                        val socket = serverSocket.accept()
                        ioExecutor.execute { handleConnection(socket) }
                    } catch (e: Exception) {
                        if (running) {
                            continue
                        }
                    }
                }
            }
        }

        fun stop() {
            running = false
            try {
                serverSocket.close()
            } catch (e: Exception) {
                // Ignore close exceptions.
            }
        }

        private fun handleConnection(socket: Socket) {
            socket.use {
                BufferedReader(InputStreamReader(socket.getInputStream())).useLines { lines ->
                    lines.forEach { line ->
                        if (line.isBlank()) {
                            return@forEach
                        }
                        try {
                            val message = gson.fromJson(line, SignalingPayload::class.java)
                            onMessage(message)
                        } catch (e: Exception) {
                            // Ignore malformed payloads.
                        }
                    }
                }
            }
        }
    }
}
