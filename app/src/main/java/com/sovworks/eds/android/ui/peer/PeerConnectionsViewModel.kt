package com.sovworks.eds.android.ui.peer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sovworks.eds.android.network.PeerConnectionRegistry
import com.sovworks.eds.android.network.WebRtcService
import com.sovworks.eds.android.trust.TrustStore
import com.sovworks.eds.android.ui.messenger.ChatGroup
import com.sovworks.eds.android.ui.messenger.MessengerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PeerConnectionsViewModel(application: Application) : AndroidViewModel(application) {
    private val trustStore = TrustStore.getInstance(application)

    val peers: StateFlow<List<PeerConnectionDisplay>> = PeerConnectionRegistry.state
        .map { list ->
            list.map { info ->
                val trust = trustStore.getKey(info.peerId)
                PeerConnectionDisplay(
                    peerKey = info.peerId,
                    peerId = trust?.peerId ?: trust?.name ?: info.peerId,
                    alias = trust?.name,
                    publicKey = trust?.publicKey ?: info.peerId,
                    endpoint = info.endpoint,
                    status = info.status,
                    stats = info.stats,
                    trustLevel = trust?.trustLevel ?: 0
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<Map<String, ChatGroup>> = MessengerRepository.groups

    fun connect(peerKey: String) {
        WebRtcService.getPeerConnectionManager()?.initiateConnection(peerKey)
    }

    fun disconnect(peerKey: String) {
        WebRtcService.getPeerConnectionManager()?.closeConnection(peerKey)
    }

    fun createGroup(name: String, memberIds: Set<String>) {
        MessengerRepository.createGroup(name, memberIds)
    }
}

data class PeerConnectionDisplay(
    val peerKey: String,
    val peerId: String,
    val alias: String?,
    val publicKey: String,
    val endpoint: String?,
    val status: String,
    val stats: PeerConnectionRegistry.PeerStats?,
    val trustLevel: Int
)
