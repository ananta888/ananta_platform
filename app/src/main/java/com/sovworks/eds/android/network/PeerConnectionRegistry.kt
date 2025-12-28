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
            val updated = existing?.copy(status = status) ?: PeerInfo(peerId, null, status)
            peers[peerId] = updated
            publish()
        }
    }

    fun updateStats(peerId: String, stats: PeerStats) {
        synchronized(lock) {
            val existing = peers[peerId] ?: return
            peers[peerId] = existing.copy(stats = stats)
            publish()
        }
    }

    fun updateTrustLevel(peerId: String, level: Int) {
        synchronized(lock) {
            val existing = peers[peerId] ?: return
            peers[peerId] = existing.copy(trustLevel = level)
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

    data class PeerStats(
        val latencyMs: Long,
        val packetLoss: Double,
        val bitrateKbps: Double
    )

    data class PeerInfo(
        val peerId: String,
        val endpoint: String?,
        val status: String,
        val stats: PeerStats? = null,
        val trustLevel: Int = 0
    )
}
