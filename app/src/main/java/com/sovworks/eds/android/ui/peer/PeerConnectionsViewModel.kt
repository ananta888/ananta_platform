package com.sovworks.eds.android.ui.peer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sovworks.eds.android.network.PeerConnectionRegistry
import com.sovworks.eds.android.network.PeerConnectionRequestRegistry
import com.sovworks.eds.android.network.PeerDirectory
import com.sovworks.eds.android.network.WebRtcService
import com.sovworks.eds.android.identity.IdentityManager
import com.sovworks.eds.android.trust.TrustStore
import com.sovworks.eds.android.trust.TrustedKey
import com.sovworks.eds.android.ui.messenger.ChatGroup
import com.sovworks.eds.android.ui.messenger.MessengerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PeerConnectionsViewModel(application: Application) : AndroidViewModel(application) {
    private val trustStore = TrustStore.getInstance(application)

    val peers: StateFlow<List<PeerConnectionDisplay>> = combine(
        PeerConnectionRegistry.state,
        PeerDirectory.state,
        PeerConnectionRequestRegistry.state
    ) { list, directory, requests ->
        val myKey = IdentityManager.loadIdentity(getApplication())?.publicKeyBase64
        val peerIdMap = directory.entries.associateBy({ it.publicKey }, { it.peerIdFromServer })
        list.filter { info -> info.peerId != myKey }.map { info ->
            val trust = trustStore.getKey(info.peerId)
            val publicPeerId = peerIdMap[info.peerId]
            val requestState = requests[info.peerId]
            PeerConnectionDisplay(
                peerKey = info.peerId,
                peerId = trust?.peerId ?: trust?.name ?: publicPeerId ?: info.peerId,
                alias = trust?.name,
                publicKey = trust?.publicKey ?: info.peerId,
                endpoint = info.endpoint,
                status = info.status,
                stats = info.stats,
                trustLevel = trust?.trustLevel ?: 0,
                requestIncoming = requestState?.incoming == true,
                requestOutgoing = requestState?.outgoing == true
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<Map<String, ChatGroup>> = MessengerRepository.groups

    fun requestConnection(peer: PeerConnectionDisplay) {
        val existing = trustStore.getKey(peer.peerKey)
        if (existing == null) {
            val fallbackName = peer.alias ?: peer.peerId
            val key = TrustedKey(peer.peerKey, peer.peerKey, fallbackName)
            key.peerId = peer.peerId
            trustStore.addKey(key)
        }
        WebRtcService.getPeerConnectionManager()?.requestConnection(peer.peerKey)
    }

    fun confirmConnection(peerKey: String) {
        WebRtcService.getPeerConnectionManager()?.acceptConnection(peerKey)
    }

    fun disconnect(peerKey: String) {
        WebRtcService.getPeerConnectionManager()?.closeConnection(peerKey)
    }

    fun createGroup(name: String, memberIds: Set<String>) {
        MessengerRepository.createGroup(name, memberIds)
    }

    fun deleteGroup(groupId: String) {
        MessengerRepository.deleteGroup(groupId)
    }

    fun sendMessageToPeers(peerIds: Set<String>, text: String) {
        MessengerRepository.sendMessageToPeers(peerIds, text)
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
    val trustLevel: Int,
    val requestIncoming: Boolean,
    val requestOutgoing: Boolean
)
