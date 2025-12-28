package com.sovworks.eds.android.network

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PeerConnectionRegistry {
    private val lock = Any()
    private val peers = ConcurrentHashMap<String, PeerInfo>()
    private val _state = MutableStateFlow<List<PeerInfo>>(emptyList())
    val state: StateFlow<List<PeerInfo>> = _state.asStateFlow()

    fun updatePeer(peerId: String, endpoint: String?, status: String? = null) {
        synchronized(lock) {
            val existing = peers[peerId]
            val updated = PeerInfo(
                peerId = peerId,
                endpoint = endpoint ?: existing?.endpoint,
                status = status ?: existing?.status ?: "discovered"
            )
            peers[peerId] = updated
            publish()
        }
    }

    fun updateStatus(peerId: String, status: String) {
        synchronized(lock) {
            val existing = peers[peerId]
            val updated = PeerInfo(
                peerId = peerId,
                endpoint = existing?.endpoint,
                status = status
            )
            peers[peerId] = updated
            publish()
        }
    }

    fun removePeer(peerId: String) {
        synchronized(lock) {
            peers.remove(peerId)
            publish()
        }
    }

    private fun publish() {
        _state.value = peers.values.sortedBy { it.peerId }
    }

    data class PeerInfo(
        val peerId: String,
        val endpoint: String?,
        val status: String
    )
}
