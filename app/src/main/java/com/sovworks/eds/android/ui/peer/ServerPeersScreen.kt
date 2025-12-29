package com.sovworks.eds.android.ui.peer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sovworks.eds.android.network.SignalingServerPeersRepository
import com.sovworks.eds.android.trust.TrustStore

@Composable
fun ServerPeersScreen() {
    val context = LocalContext.current
    val peersByServer by SignalingServerPeersRepository.peers.collectAsState()
    val trustStore = remember { TrustStore.getInstance(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "Server Peers", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { SignalingServerPeersRepository.refresh(context) }) {
                Text("Refresh")
            }
        }

        if (peersByServer.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "No server peers loaded.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            peersByServer.forEach { (serverUrl, peers) ->
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = serverUrl, style = MaterialTheme.typography.titleSmall)
                }
                items(peers, key = { it.publicKey }) { peer ->
                    val trusted = trustStore.getKey(peer.publicKey)
                    val alias = trusted?.name?.takeIf { it.isNotBlank() }
                    val display = alias ?: peer.peerId ?: peer.publicKey.take(8)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(text = display, style = MaterialTheme.typography.titleMedium)
                            Text(text = "Peer ID: ${peer.peerId ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Public Key: ${peer.publicKey}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Visibility: ${peer.visibility}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
