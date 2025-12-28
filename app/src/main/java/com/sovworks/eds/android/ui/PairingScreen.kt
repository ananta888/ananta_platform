package com.sovworks.eds.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

@Composable
fun PairingScreen(onStartScanner: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val identity = remember { IdentityManager.loadIdentity(context) }
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
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }

        run {
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
        } ?: Text("Failed to generate QR Code")

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onStartScanner) {
            Text("Scan Peer Code")
        }
    }
}
