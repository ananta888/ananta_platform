package com.sovworks.eds.android.ui.peer

import androidx.lifecycle.ViewModel
import com.sovworks.eds.android.network.PeerConnectionRegistry
import com.sovworks.eds.android.network.WebRtcService
import kotlinx.coroutines.flow.StateFlow

class PeerConnectionsViewModel : ViewModel() {
    val peers: StateFlow<List<PeerConnectionRegistry.PeerInfo>> = PeerConnectionRegistry.state

    fun connect(peerId: String) {
        WebRtcService.getPeerConnectionManager()?.initiateConnection(peerId)
    }

    fun disconnect(peerId: String) {
        WebRtcService.getPeerConnectionManager()?.closeConnection(peerId)
    }
}
