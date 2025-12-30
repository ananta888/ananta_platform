package com.sovworks.eds.android.network

import android.content.Context
import com.google.gson.Gson
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.settings.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ServerPeer(
    val serverUrl: String,
    val publicKey: String,
    val peerId: String?,
    val visibility: String
)

object SignalingServerPeersRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private val client: OkHttpClient? by lazy { createClientOrNull() }

    private val _peers = MutableStateFlow<Map<String, List<ServerPeer>>>(emptyMap())
    val peers: StateFlow<Map<String, List<ServerPeer>>> = _peers.asStateFlow()

    fun refresh(context: Context) {
        val settings = UserSettings.getSettings(context)
        val urls = settings.getSignalingServerUrls()
        if (urls.isEmpty()) {
            _peers.value = emptyMap()
            return
        }
        scope.launch {
            val results = urls.associateWith { url ->
                fetchPeers(url)
            }
            _peers.value = results
        }
    }

    private fun fetchPeers(serverUrl: String): List<ServerPeer> {
        val httpClient = client ?: return emptyList()
        val httpUrl = toHttpUrl(serverUrl).trimEnd('/')
        val request = Request.Builder()
            .url("$httpUrl/peers")
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val payload = gson.fromJson(body, PeersResponse::class.java) ?: return emptyList()
                payload.peers.orEmpty().mapNotNull { peer ->
                    val publicKey = peer.publicKey ?: return@mapNotNull null
                    ServerPeer(
                        serverUrl = serverUrl,
                        publicKey = publicKey,
                        peerId = peer.peerId,
                        visibility = peer.visibility ?: "unknown"
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun createClientOrNull(): OkHttpClient? {
        return try {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            Logger.log("SignalingServerPeersRepository: OkHttp init failed: ${e.message}")
            null
        }
    }

    private fun toHttpUrl(url: String): String {
        return when {
            url.startsWith("ws://") -> "http://" + url.removePrefix("ws://")
            url.startsWith("wss://") -> "https://" + url.removePrefix("wss://")
            else -> url
        }
    }

    private data class PeersResponse(
        val peers: List<PeerEntry>? = null
    )

    private data class PeerEntry(
        val peerId: String? = null,
        val publicKey: String? = null,
        val visibility: String? = null
    )
}
