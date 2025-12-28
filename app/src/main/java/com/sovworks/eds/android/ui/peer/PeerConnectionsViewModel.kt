package com.sovworks.eds.android.ui.peer

import androidx.lifecycle.ViewModel
import com.sovworks.eds.android.network.PeerConnectionRegistry
import com.sovworks.eds.android.network.WebRtcService
import com.sovworks.eds.android.ui.messenger.ChatGroup
import com.sovworks.eds.android.ui.messenger.MessengerRepository
import kotlinx.coroutines.flow.StateFlow

class PeerConnectionsViewModel : ViewModel() {
    val peers: StateFlow<List<PeerConnectionRegistry.PeerInfo>> = PeerConnectionRegistry.state
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
