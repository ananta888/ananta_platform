package com.sovworks.eds.android.ui.peer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sovworks.eds.android.navigation.NavigationViewModel
import com.sovworks.eds.android.navigation.Screen
import com.sovworks.eds.android.network.PeerConnectionRegistry

@Composable
fun PeerConnectionsScreen(
    viewModel: PeerConnectionsViewModel = viewModel(),
    navigationViewModel: NavigationViewModel = viewModel()
) {
    val peers by viewModel.peers.collectAsState()
    val groups by viewModel.groups.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (peers.isNotEmpty()) {
            item {
                Text(text = "Peers", style = MaterialTheme.typography.titleLarge)
            }
            items(peers, key = { it.peerId }) { peer ->
                PeerConnectionCard(
                    peer = peer,
                    onConnect = { viewModel.connect(peer.peerId) },
                    onDisconnect = { viewModel.disconnect(peer.peerId) },
                    onChat = { navigationViewModel.navigateTo(Screen.Messenger(peerId = peer.peerId)) }
                )
            }
        }

        if (groups.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Groups", style = MaterialTheme.typography.titleLarge)
            }
            items(groups.values.toList(), key = { it.id }) { group ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = group.name, style = MaterialTheme.typography.titleMedium)
                            Text(text = "${group.memberIds.size} members", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { 
                            navigationViewModel.navigateTo(Screen.Messenger(groupId = group.id))
                        }) {
                            Text("Chat")
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = {
                    val connectedPeerIds = peers.filter { it.status == "connected" }.map { it.peerId }.toSet()
                    if (connectedPeerIds.isNotEmpty()) {
                        viewModel.createGroup("New Group", connectedPeerIds)
                    }
                },
                enabled = peers.any { it.status == "connected" },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Group with Connected Peers")
            }
        }

        if (peers.isEmpty() && groups.isEmpty()) {
            item {
                EmptyPeersState()
            }
        }
    }
}

@Composable
private fun EmptyPeersState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "No peers discovered yet.", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "Make sure devices are on the same network.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PeerConnectionCard(
    peer: PeerConnectionRegistry.PeerInfo,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onChat: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = peer.peerId, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Status: ${peer.status}", style = MaterialTheme.typography.bodySmall)
            peer.stats?.let { stats ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Latency: ${stats.latencyMs}ms • Loss: ${stats.packetLoss.toInt()} • Bitrate: ${stats.bitrateKbps.toInt()}kbps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            peer.endpoint?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Endpoint: $it", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    TextButton(onClick = onConnect) {
                        Text("Connect")
                    }
                    TextButton(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                }
                TextButton(
                    onClick = onChat,
                    enabled = peer.status == "connected"
                ) {
                    Text("Chat")
                }
            }
        }
    }
}
