package com.sovworks.eds.android.activities

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sovworks.eds.android.identity.IdentitySyncManager

class IdentitySyncActivity : ComponentActivity() {
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleScanResult(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val syncPackageJson = IdentitySyncManager.exportSyncPackage(this)
        val qrBitmap = syncPackageJson?.let { generateQrCode(it) }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        qrBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Sync QR Code",
                                modifier = Modifier.size(300.dp)
                            )
                        } ?: Text("Failed to generate sync package")

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = { startScanner() },
                            modifier = Modifier.fillMaxWidth(0.7f)
                        ) {
                            Text("Import Identity (Scan QR)")
                        }
                    }
                }
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

    private fun startScanner() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Scan identity sync QR code")
        options.setBeepEnabled(false)
        barcodeLauncher.launch(options)
    }

    private fun handleScanResult(contents: String) {
        val success = IdentitySyncManager.importSyncPackage(this, contents)
        if (success) {
            Toast.makeText(this, "Identity synced successfully!", Toast.LENGTH_LONG).show()
            finish()
        } else {
            Toast.makeText(this, "Failed to import identity. Invalid QR code?", Toast.LENGTH_LONG).show()
        }
    }
}
