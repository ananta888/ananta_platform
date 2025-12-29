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
import com.sovworks.eds.android.network.PeerDirectory
import com.sovworks.eds.android.trust.TrustStore

@Composable
fun ServerPeersScreen() {
    val context = LocalContext.current
    val directory by PeerDirectory.state.collectAsState()
    val trustStore = remember { TrustStore.getInstance(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "Server Peers", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { PeerDirectory.refreshServers(context) }) {
                Text("Refresh")
            }
        }

        if (directory.serverPeers.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "No server peers loaded.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            val entryMap = directory.entries.associateBy { it.publicKey }
            directory.serverPeers.forEach { (serverUrl, peers) ->
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = serverUrl, style = MaterialTheme.typography.titleSmall)
                }
                items(peers, key = { it.publicKey }) { peer ->
                    val trusted = trustStore.getKey(peer.publicKey)
                    val alias = trusted?.name?.takeIf { it.isNotBlank() }
                    val display = alias ?: peer.peerId ?: peer.publicKey.take(8)
                    val entry = entryMap[peer.publicKey]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(text = display, style = MaterialTheme.typography.titleMedium)
                            Text(text = "Peer ID: ${peer.peerId ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Public Key: ${peer.publicKey}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Visibility: ${peer.visibility}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "Sources: ${entry?.sources?.joinToString(", ") ?: "-"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Servers: ${entry?.servers?.joinToString(", ") ?: serverUrl}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Last seen: ${formatLastSeen(entry?.lastSeenMillis)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatLastSeen(lastSeenMillis: Long?): String {
    if (lastSeenMillis == null) return "unknown"
    val diffMs = System.currentTimeMillis() - lastSeenMillis
    if (diffMs < 0) return "now"
    val seconds = diffMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 30 -> "just now"
        minutes < 1 -> "${seconds}s ago"
        hours < 1 -> "${minutes}m ago"
        days < 1 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}
