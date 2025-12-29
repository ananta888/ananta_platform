package com.sovworks.eds.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import com.sovworks.eds.android.network.PeerConnectionRegistry
import com.sovworks.eds.android.network.PeerDirectory
import com.sovworks.eds.android.network.SignalingConnectionStatus
import com.sovworks.eds.android.network.SignalingStatusTracker
import com.sovworks.eds.android.network.WebRtcService
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.UserSettingsCommon
import com.sovworks.eds.android.trust.TrustStore
import com.sovworks.eds.android.trust.TrustedKey
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose

@Composable
fun PairingScreen(onStartScanner: () -> Unit, onOpenIdentitySync: (() -> Unit)? = null) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val identity = remember { IdentityManager.loadIdentity(context) }
    val settings = remember { UserSettings.getSettings(context) }
    val peerDirectory by PeerDirectory.state.collectAsState()
    val signalingStatuses by SignalingStatusTracker.statuses.collectAsState()
    val peerRegistry by PeerConnectionRegistry.state.collectAsState()
    val myMetadata = remember(identity) { identity?.let { PairingManager.createMyMetadata(it) } }
    val qrBitmap = remember(myMetadata) { myMetadata?.let { PairingManager.generateQrCode(it) } }
    val pairingCode = remember(identity) { identity?.id }
    val autoConnectEnabled by remember {
        callbackFlow {
            val prefs = settings.sharedPreferences
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == UserSettingsCommon.SIGNALING_AUTO_CONNECT_PUBLIC) {
                    trySend(
                        prefs.getBoolean(UserSettingsCommon.SIGNALING_AUTO_CONNECT_PUBLIC, false)
                    )
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(
                prefs.getBoolean(UserSettingsCommon.SIGNALING_AUTO_CONNECT_PUBLIC, false)
            )
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }.collectAsState(initial = false)

    LaunchedEffect(peerDirectory, autoConnectEnabled) {
        if (!autoConnectEnabled) return@LaunchedEffect
        val myKey = identity?.publicKeyBase64
        peerDirectory.entries.filter { it.visibility == "public" }.forEach { peer ->
            if (peer.publicKey == myKey) return@forEach
            val existing = peerRegistry.firstOrNull { it.peerId == peer.publicKey }
            val status = existing?.status ?: ""
            val shouldConnect = status.isBlank() || status == "closed" || status == "disconnected" || status == "failed"
            if (shouldConnect) {
                val trustStore = TrustStore.getInstance(context)
                if (trustStore.getKey(peer.publicKey) == null) {
                    val display = peer.peerIdFromServer ?: "Public Peer"
                    val key = TrustedKey(peer.publicKey, peer.publicKey, display)
                    key.peerId = peer.peerIdFromServer
                    trustStore.addKey(key)
                }
                WebRtcService.getPeerConnectionManager()?.initiateConnection(peer.publicKey)
            }
        }
    }

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
        Text(
            text = if (autoConnectEnabled) "Auto-Connect: On" else "Auto-Connect: Off",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
        TextButton(onClick = {
            WebRtcService.requestPublicPeers()
            PeerDirectory.refreshServers(context)
        }) {
            Text("Refresh")
        }
        val myKey = identity?.publicKeyBase64
        val visiblePeers = peerDirectory.entries.filter { it.visibility == "public" && it.publicKey != myKey }
        if (visiblePeers.isEmpty()) {
            Text(
                text = "No public peers available",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            val trustStore = remember { TrustStore.getInstance(context) }
            visiblePeers.forEach { peer ->
                val trusted = trustStore.getKey(peer.publicKey)
                val alias = trusted?.name?.takeIf { it.isNotBlank() }
                val displayName = alias ?: peer.peerIdFromServer ?: peer.publicKey.take(8)
                val status = peerRegistry.firstOrNull { it.peerId == peer.publicKey }?.status ?: "offline"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Peer ID: ${peer.peerIdFromServer ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "Key: ${peer.publicKey.take(16)}...",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(text = "Status: $status", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = {
                        if (trusted == null) {
                            val display = peer.peerIdFromServer ?: "Public Peer"
                            val key = TrustedKey(peer.publicKey, peer.publicKey, display)
                            key.peerId = peer.peerIdFromServer
                            trustStore.addKey(key)
                        }
                        WebRtcService.getPeerConnectionManager()
                            ?.initiateConnection(peer.publicKey)
                    }, enabled = status != "connecting" && status != "connected") {
                        Text("Connect")
                    }
                }
            }
        }
    }
}
