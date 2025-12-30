package com.sovworks.eds.android.network

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object SignalingIceServersFetcher {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun fetch(urls: List<String>): List<ConnectionMetadata.IceServerConfig>? {
        urls.forEach { url ->
            val httpUrl = toHttpUrl(url).trimEnd('/')
            val request = Request.Builder()
                .url("$httpUrl/ice-servers")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@forEach
                    val body = response.body?.string() ?: return@forEach
                    val payload = gson.fromJson(body, IceServersResponse::class.java)
                    val configs = payload?.iceServers.orEmpty()
                    if (configs.isNotEmpty()) {
                        return configs
                    }
                }
            } catch (_: Exception) {
                // Ignore and try next URL.
            }
        }
        return null
    }

    private fun toHttpUrl(url: String): String {
        return when {
            url.startsWith("ws://") -> "http://" + url.removePrefix("ws://")
            url.startsWith("wss://") -> "https://" + url.removePrefix("wss://")
            else -> url
        }
    }

    private data class IceServersResponse(
        val iceServers: List<ConnectionMetadata.IceServerConfig>?
    )
}
