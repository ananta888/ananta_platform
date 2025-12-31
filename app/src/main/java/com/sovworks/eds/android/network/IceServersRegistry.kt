package com.sovworks.eds.android.network

import org.webrtc.PeerConnection

object IceServersRegistry {
    private val defaultConfigs = listOf(
        ConnectionMetadata.IceServerConfig(listOf("stun:stun.l.google.com:19302"))
    )

    @Volatile
    private var iceServers: List<PeerConnection.IceServer> = buildIceServers(defaultConfigs)
    @Volatile
    private var iceServerConfigs: List<ConnectionMetadata.IceServerConfig> = defaultConfigs

    fun getIceServers(): List<PeerConnection.IceServer> = iceServers
    fun getConfigs(): List<ConnectionMetadata.IceServerConfig> = iceServerConfigs

    fun updateFromConfigs(configs: List<ConnectionMetadata.IceServerConfig>?) {
        if (configs.isNullOrEmpty()) return
        val mapped = buildIceServers(configs)
        if (mapped.isNotEmpty()) {
            iceServers = mapped
            iceServerConfigs = configs
        }
    }

    private fun buildIceServers(
        configs: List<ConnectionMetadata.IceServerConfig>
    ): List<PeerConnection.IceServer> {
        return configs.mapNotNull { config ->
            val urls = config.urls.filter { it.isNotBlank() }
            if (urls.isEmpty()) return@mapNotNull null
            val builder = PeerConnection.IceServer.builder(urls)
            if (!config.username.isNullOrBlank() && !config.credential.isNullOrBlank()) {
                builder.setUsername(config.username).setPassword(config.credential)
            }
            builder.createIceServer()
        }
    }
}
