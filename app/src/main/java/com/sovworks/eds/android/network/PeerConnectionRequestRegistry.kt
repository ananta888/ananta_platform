package com.sovworks.eds.android.network

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PeerConnectionRequestRegistry {
    private val lock = Any()
    private val requests = ConcurrentHashMap<String, RequestState>()
    private val _state = MutableStateFlow<Map<String, RequestState>>(emptyMap())
    val state: StateFlow<Map<String, RequestState>> = _state.asStateFlow()

    data class RequestState(
        val outgoing: Boolean = false,
        val incoming: Boolean = false
    )

    fun markOutgoing(peerId: String) {
        update(peerId) { it.copy(outgoing = true) }
    }

    fun markIncoming(peerId: String) {
        update(peerId) { it.copy(incoming = true) }
    }

    fun clearOutgoing(peerId: String) {
        update(peerId) { it.copy(outgoing = false) }
    }

    fun clearIncoming(peerId: String) {
        update(peerId) { it.copy(incoming = false) }
    }

    fun clear(peerId: String) {
        synchronized(lock) {
            requests.remove(peerId)
            publish()
        }
    }

    fun get(peerId: String): RequestState? = requests[peerId]

    private fun update(peerId: String, block: (RequestState) -> RequestState) {
        synchronized(lock) {
            val existing = requests[peerId] ?: RequestState()
            val updated = block(existing)
            if (!updated.incoming && !updated.outgoing) {
                requests.remove(peerId)
            } else {
                requests[peerId] = updated
            }
            publish()
        }
    }

    private fun publish() {
        _state.value = requests.toSortedMap()
    }
}
