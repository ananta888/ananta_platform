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

@Composable
fun ConnectedPeersScreen(
    viewModel: PeerConnectionsViewModel = viewModel(),
    navigationViewModel: NavigationViewModel = viewModel()
) {
    val peers by viewModel.peers.collectAsState()
    val connectedPeers = peers.filter { it.status == "connected" }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "Connected Devices", style = MaterialTheme.typography.titleLarge)
        }

        if (connectedPeers.isEmpty()) {
            item {
                Text(
                    text = "No connected devices.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(connectedPeers, key = { it.peerKey }) { peer ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(text = peer.alias ?: peer.peerId, style = MaterialTheme.typography.titleMedium)
                        Text(text = "Peer ID: ${peer.peerId}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "Public Key: ${peer.publicKey.take(16)}...",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    navigationViewModel.navigateTo(
                                        Screen.Messenger(peerId = peer.peerKey)
                                    )
                                }
                            ) {
                                Text("Chat")
                            }
                            TextButton(
                                onClick = {
                                    navigationViewModel.navigateTo(
                                        Screen.Messenger(peerId = peer.peerKey, relayOnly = true)
                                    )
                                }
                            ) {
                                Text("Relay")
                            }
                        }
                    }
                }
            }
        }
    }
}
