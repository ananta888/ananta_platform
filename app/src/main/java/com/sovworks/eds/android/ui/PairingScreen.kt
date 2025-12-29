package com.sovworks.eds.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.sovworks.eds.android.identity.IdentityManager
import com.sovworks.eds.android.network.PairingManager
import com.sovworks.eds.android.network.PublicPeersDirectory
import com.sovworks.eds.android.network.SignalingConnectionStatus
import com.sovworks.eds.android.network.SignalingStatusTracker
import com.sovworks.eds.android.network.WebRtcService
import com.sovworks.eds.android.trust.TrustStore
import com.sovworks.eds.android.trust.TrustedKey

@Composable
fun PairingScreen(onStartScanner: () -> Unit, onOpenIdentitySync: (() -> Unit)? = null) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val identity = remember { IdentityManager.loadIdentity(context) }
    val publicPeers by PublicPeersDirectory.publicPeers.collectAsState()
    val signalingStatuses by SignalingStatusTracker.statuses.collectAsState()
    val myMetadata = remember(identity) { identity?.let { PairingManager.createMyMetadata(it) } }
    val qrBitmap = remember(myMetadata) { myMetadata?.let { PairingManager.generateQrCode(it) } }
    val pairingCode = remember(identity) { identity?.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "My Pairing Code",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (pairingCode == null) {
            Text(
                text = "Create identity to generate a pairing code.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (onOpenIdentitySync != null) {
                TextButton(
                    onClick = onOpenIdentitySync,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text("Create or Sync Identity")
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = pairingCode,
                    style = MaterialTheme.typography.bodyMedium
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(pairingCode))
                        Toast.makeText(context, "Pairing code copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Pairing Code"
                    )
                }
            }
        }

        qrBitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "My QR Code",
                modifier = Modifier.size(250.dp)
            )
        } ?: Text("QR Code unavailable until an identity exists")

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This QR code shares your peer ID and public key for trust. It does not connect automatically.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onStartScanner) {
            Text("Scan Peer Code")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "WebSocket Status",
            style = MaterialTheme.typography.titleSmall
        )
        if (signalingStatuses.isEmpty()) {
            Text(
                text = "No signaling connection yet",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            signalingStatuses.forEach { (url, status) ->
                val statusLabel = when (status) {
                    SignalingConnectionStatus.CONNECTED -> "Connected"
                    SignalingConnectionStatus.CONNECTING -> "Connecting"
                    SignalingConnectionStatus.ERROR -> "Error"
                    SignalingConnectionStatus.DISCONNECTED -> "Disconnected"
                }
                Text(
                    text = "$url: $statusLabel",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Public Peers",
            style = MaterialTheme.typography.titleSmall
        )
        if (publicPeers.isEmpty()) {
            Text(
                text = "No public peers available",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            val trustStore = remember { TrustStore.getInstance(context) }
            publicPeers.forEach { peer ->
                val trusted = trustStore.getKey(peer.publicKey)
                val alias = trusted?.name?.takeIf { it.isNotBlank() }
                val displayName = alias ?: peer.peerId ?: peer.publicKey.take(8)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Key: ${peer.publicKey.take(16)}...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(onClick = {
                        if (trusted == null) {
                            val display = peer.peerId ?: "Public Peer"
                            val key = TrustedKey(peer.publicKey, peer.publicKey, display)
                            key.peerId = peer.peerId
                            trustStore.addKey(key)
                        }
                        WebRtcService.getPeerConnectionManager()
                            ?.initiateConnection(peer.publicKey)
                    }) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}
