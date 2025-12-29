package com.sovworks.eds.android.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PublicPeer(
    val publicKey: String,
    val peerId: String?
)

object PublicPeersDirectory {
    private val _publicPeers = MutableStateFlow<List<PublicPeer>>(emptyList())
    val publicPeers: StateFlow<List<PublicPeer>> = _publicPeers.asStateFlow()

    fun update(peers: List<PublicPeer>) {
        try {
            android.util.Log.d("PublicPeersDirectory", "Public peers updated: ${peers.size}")
        } catch (_: Throwable) {
            // Ignore logging failures in JVM unit tests.
        }
        _publicPeers.value = peers
    }
}
