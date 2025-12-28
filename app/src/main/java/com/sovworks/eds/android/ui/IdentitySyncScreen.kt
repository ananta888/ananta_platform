package com.sovworks.eds.android.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.sovworks.eds.android.identity.IdentityManager
import com.sovworks.eds.android.identity.IdentitySyncManager

@Composable
fun IdentitySyncScreen(onStartScanner: () -> Unit) {
    val context = LocalContext.current
    var identity by remember { mutableStateOf(IdentityManager.loadIdentity(context)) }
    var identityName by remember { mutableStateOf("") }
    val syncPackageJson = remember(identity) {
        identity?.let { IdentitySyncManager.exportSyncPackage(context) }
    }
    val qrBitmap = remember(syncPackageJson) { syncPackageJson?.let { generateQrCode(it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Identity Sync",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Scan this code on another device to sync your identity and trust network.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp),
            textAlign = TextAlign.Center
        )
        
        if (identity == null) {
            Text(
                text = "No identity found. Create one to enable QR sync.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
                textAlign = TextAlign.Center
            )
            OutlinedTextField(
                value = identityName,
                onValueChange = { identityName = it },
                label = { Text("Identity name") },
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    identity = IdentityManager.createNewIdentity(context, identityName.trim())
                    identityName = ""
                },
                enabled = identityName.isNotBlank()
            ) {
                Text("Create Identity")
            }
        } else {
            qrBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Sync QR Code",
                    modifier = Modifier.size(300.dp)
                )
            } ?: Text("Failed to generate sync package")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStartScanner,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Import Identity (Scan QR)")
        }
    }
}

private fun generateQrCode(text: String, size: Int = 512): Bitmap? {
    return try {
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        BarcodeEncoder().createBitmap(matrix)
    } catch (e: Exception) {
        null
    }
}
