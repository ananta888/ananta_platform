package com.sovworks.eds.android.ui.transfers

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sovworks.eds.android.transfer.FileTransferEntry
import com.sovworks.eds.android.transfer.FileTransferStatus
import com.sovworks.eds.android.transfer.TransferDirection

@Composable
fun FileTransferDashboardScreen(
    viewModel: FileTransferDashboardViewModel = viewModel()
) {
    val transfers by viewModel.transfers.collectAsState()

    if (transfers.isEmpty()) {
        EmptyFileTransferState()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(transfers, key = { it.id }) { transfer ->
            FileTransferCard(
                transfer = transfer,
                onPause = { viewModel.pauseTransfer(transfer.id) },
                onResume = { viewModel.resumeTransfer(transfer.id) }
            )
        }
    }
}

@Composable
private fun EmptyFileTransferState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No active file transfers",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Incoming and outgoing transfers will appear here.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun FileTransferCard(
    transfer: FileTransferEntry,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = transfer.fileName, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            val directionLabel = if (transfer.direction == TransferDirection.INCOMING) {
                "Incoming"
            } else {
                "Outgoing"
            }
            Text(
                text = "$directionLabel • ${transfer.peerId}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))

            val progress = transfer.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${(progress * 100).toInt()}% of ${transfer.totalBytes ?: 0} bytes",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${transfer.bytesTransferred} bytes transferred",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = transfer.status.name.lowercase().replace('_', ' '), style = MaterialTheme.typography.bodySmall)
                val canPause = transfer.status == FileTransferStatus.IN_PROGRESS
                val canResume = transfer.status == FileTransferStatus.PAUSED
                if (canPause) {
                    TextButton(onClick = onPause) {
                        Text("Pause")
                    }
                } else if (canResume) {
                    TextButton(onClick = onResume) {
                        Text("Resume")
                    }
                }
            }
        }
    }
}
