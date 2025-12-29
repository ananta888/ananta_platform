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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sovworks.eds.android.navigation.NavigationViewModel
import com.sovworks.eds.android.navigation.Screen
import com.sovworks.eds.android.ui.theme.TrustStars

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
            items(peers, key = { it.peerKey }) { peer ->
                PeerConnectionCard(
                    peer = peer,
                    onConnect = { viewModel.connect(peer.peerKey) },
                    onDisconnect = { viewModel.disconnect(peer.peerKey) },
                    onChat = { navigationViewModel.navigateTo(Screen.Messenger(peerId = peer.peerKey)) }
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
                    val connectedPeerIds = peers.filter { it.status == "connected" }.map { it.peerKey }.toSet()
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
    peer: PeerConnectionDisplay,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onChat: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val showKeyDialog = remember { mutableStateOf(false) }
    val alias = peer.alias ?: "Unknown Alias"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = alias, style = MaterialTheme.typography.titleMedium)
                    Text(text = "Peer ID: ${peer.peerId}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Public Key: ${peer.publicKey.take(16)}...", style = MaterialTheme.typography.bodySmall)
                }
                TrustStars(level = peer.trustLevel)
            }
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
                    androidx.compose.material3.IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(peer.publicKey))
                    }) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Public Key"
                        )
                    }
                    TextButton(onClick = { showKeyDialog.value = true }) {
                        Text("Public Key")
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

    if (showKeyDialog.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showKeyDialog.value = false },
            title = { Text("Public Key") },
            text = { Text(peer.publicKey) },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(peer.publicKey))
                        showKeyDialog.value = false
                    }
                ) { Text("Copy") }
            },
            dismissButton = {
                TextButton(onClick = { showKeyDialog.value = false }) {
                    Text("Close")
                }
            }
        )
    }
}
