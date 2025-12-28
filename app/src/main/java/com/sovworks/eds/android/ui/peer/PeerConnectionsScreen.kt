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
import com.sovworks.eds.android.network.PeerConnectionRegistry

@Composable
fun PeerConnectionsScreen(
    viewModel: PeerConnectionsViewModel = viewModel()
) {
    val peers by viewModel.peers.collectAsState()

    if (peers.isEmpty()) {
        EmptyPeersState()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(peers, key = { it.peerId }) { peer ->
            PeerConnectionCard(
                peer = peer,
                onConnect = { viewModel.connect(peer.peerId) },
                onDisconnect = { viewModel.disconnect(peer.peerId) }
            )
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
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = peer.peerId, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Status: ${peer.status}", style = MaterialTheme.typography.bodySmall)
            peer.endpoint?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Endpoint: $it", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onConnect) {
                    Text("Connect")
                }
                TextButton(onClick = onDisconnect) {
                    Text("Disconnect")
                }
            }
        }
    }
}
