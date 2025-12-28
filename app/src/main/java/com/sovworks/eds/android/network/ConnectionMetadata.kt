package com.sovworks.eds.android.network

import com.google.gson.Gson
import org.webrtc.PeerConnection

data class ConnectionMetadata(
    val peerId: String,
    val publicKeyBase64: String,
    val iceServers: List<IceServerConfig>
) {
    data class IceServerConfig(
        val urls: List<String>,
        val username: String? = null,
        val credential: String? = null
    )

    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): ConnectionMetadata = Gson().fromJson(json, ConnectionMetadata::class.java)
    }
}
