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
    
    val peers: StateFlow<List<PeerConnectionRegistry.PeerInfo>> = PeerConnectionRegistry.state
        .map { list ->
            list.map { info ->
                val trust = trustStore.getKey(info.peerId)
                info.copy(trustLevel = trust?.trustLevel ?: 0)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<Map<String, ChatGroup>> = MessengerRepository.groups

    fun connect(peerId: String) {
        WebRtcService.getPeerConnectionManager()?.initiateConnection(peerId)
    }

    fun disconnect(peerId: String) {
        WebRtcService.getPeerConnectionManager()?.closeConnection(peerId)
    }

    fun createGroup(name: String, memberIds: Set<String>) {
        MessengerRepository.createGroup(name, memberIds)
    }
}
