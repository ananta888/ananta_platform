package com.sovworks.eds.android.network

import android.content.Context
import com.sovworks.eds.android.trust.TrustStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class PeerDirectoryEntry(
    val publicKey: String,
    val peerIdFromServer: String?,
    val alias: String?,
    val localPeerId: String?,
    val connectionStatus: String?,
    val visibility: String?,
    val servers: Set<String>,
    val sources: Set<String>,
    val lastSeenMillis: Long?
)

data class PeerDirectoryState(
    val entries: List<PeerDirectoryEntry>,
    val serverPeers: Map<String, List<ServerPeer>>
)

object PeerDirectory {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val refreshTrigger = MutableStateFlow(0)
    private val _state = MutableStateFlow(PeerDirectoryState(emptyList(), emptyMap()))
    val state: StateFlow<PeerDirectoryState> = _state.asStateFlow()
    private val lastSeen = mutableMapOf<String, Long>()

    private var trustStore: TrustStore? = null
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        trustStore = TrustStore.getInstance(context)
        initialized = true
        scope.launch {
            combine(
                PublicPeersDirectory.publicPeers,
                SignalingServerPeersRepository.peers,
                PeerConnectionRegistry.state,
                refreshTrigger
            ) { publicPeers, serverPeers, connections, _ ->
                val now = System.currentTimeMillis()
                val serverIndex = mutableMapOf<String, PeerDirectoryEntryBuilder>()
                val serverMap = serverPeers.mapValues { it.value.toList() }

                serverPeers.forEach { (serverUrl, peers) ->
                    peers.forEach { peer ->
                        lastSeen[peer.publicKey] = now
                        val builder = serverIndex.getOrPut(peer.publicKey) {
                            PeerDirectoryEntryBuilder(publicKey = peer.publicKey)
                        }
                        builder.servers.add(serverUrl)
                        builder.sources.add("server")
                        builder.peerIdFromServer = builder.peerIdFromServer ?: peer.peerId
                        builder.visibility = mergeVisibility(builder.visibility, peer.visibility)
                    }
                }

                publicPeers.forEach { peer ->
                    lastSeen[peer.publicKey] = now
                    val builder = serverIndex.getOrPut(peer.publicKey) {
                        PeerDirectoryEntryBuilder(publicKey = peer.publicKey)
                    }
                    builder.sources.add("public")
                    builder.peerIdFromServer = builder.peerIdFromServer ?: peer.peerId
                    builder.visibility = mergeVisibility(builder.visibility, "public")
                }

                connections.forEach { info ->
                    if (info.status == "connected") {
                        lastSeen[info.peerId] = now
                    }
                    val builder = serverIndex.getOrPut(info.peerId) {
                        PeerDirectoryEntryBuilder(publicKey = info.peerId)
                    }
                    builder.sources.add("connection")
                    builder.connectionStatus = info.status
                }

                val trust = trustStore
                val entries = serverIndex.values.map { builder ->
                    val trusted = trust?.getKey(builder.publicKey)
                    PeerDirectoryEntry(
                        publicKey = builder.publicKey,
                        peerIdFromServer = builder.peerIdFromServer,
                        alias = trusted?.name,
                        localPeerId = trusted?.peerId,
                        connectionStatus = builder.connectionStatus,
                        visibility = builder.visibility,
                        servers = builder.servers,
                        sources = builder.sources,
                        lastSeenMillis = lastSeen[builder.publicKey]
                    )
                }.sortedBy { it.peerIdFromServer ?: it.publicKey }

                PeerDirectoryState(entries = entries, serverPeers = serverMap)
            }.collect { _state.value = it }
        }
    }

    fun refreshLocal() {
        refreshTrigger.value = refreshTrigger.value + 1
    }

    fun refreshServers(context: Context) {
        SignalingServerPeersRepository.refresh(context)
    }

    private fun mergeVisibility(existing: String?, incoming: String?): String? {
        if (incoming == null) return existing
        if (incoming == "public") return "public"
        return existing ?: incoming
    }

    private data class PeerDirectoryEntryBuilder(
        val publicKey: String,
        var peerIdFromServer: String? = null,
        var connectionStatus: String? = null,
        var visibility: String? = null,
        val servers: MutableSet<String> = mutableSetOf(),
        val sources: MutableSet<String> = mutableSetOf()
    )
}
