package com.sovworks.eds.android.ui.peer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sovworks.eds.android.trust.TrustedKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerManagementScreen(viewModel: PeerViewModel) {
    val peers by viewModel.peers.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Peer Management") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(peers) { peer ->
                PeerItem(
                    peer = peer,
                    onTrustLevelChange = { level -> viewModel.updateTrustLevel(peer.fingerprint, level) },
                    onDelete = { viewModel.removePeer(peer.fingerprint) }
                )
            }
        }
    }
}

@Composable
fun PeerItem(
    peer: TrustedKey,
    onTrustLevelChange: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = peer.name ?: "Unknown Peer", style = MaterialTheme.typography.titleMedium)
                    Text(text = peer.fingerprint.take(16) + "...", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Status: ${peer.status}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Trust: ", style = MaterialTheme.typography.bodyMedium)
                (1..5).forEach { level ->
                    IconButton(onClick = { onTrustLevelChange(level) }) {
                        Icon(
                            imageVector = if (level <= peer.trustLevel) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Star $level",
                            tint = if (level <= peer.trustLevel) Color(0xFFFFD700) else Color.Gray
                        )
                    }
                }
            }
        }
    }
}
