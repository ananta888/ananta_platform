package com.sovworks.eds.android.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

import com.sovworks.eds.android.identity.IdentityManager
import com.sovworks.eds.android.network.PairingManager
import com.sovworks.eds.android.trust.TrustStore
import com.sovworks.eds.android.trust.TrustedKey

class PairingActivity : ComponentActivity() {
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleScanResult(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val identity = IdentityManager.loadIdentity(this)
        val myMetadata = identity?.let { PairingManager.createMyMetadata(it) }
        val qrBitmap = myMetadata?.let { PairingManager.generateQrCode(it) }

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
                            text = "My Pairing Code",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        qrBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "My QR Code",
                                modifier = Modifier.size(250.dp)
                            )
                        } ?: Text("Failed to generate QR Code")

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(onClick = { startScanner() }) {
                            Text("Scan Peer Code")
                        }
                    }
                }
            }
        }
    }

    private fun startScanner() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Scan a pairing QR code")
        options.setBeepEnabled(false)
        barcodeLauncher.launch(options)
    }

    private fun handleScanResult(contents: String) {
        val metadata = PairingManager.parseQrCodeResult(contents)
        if (metadata != null) {
            val trustStore = TrustStore.getInstance(this)
            val trustedKey = TrustedKey(
                metadata.publicKeyBase64,
                metadata.publicKeyBase64,
                metadata.peerId
            )
            trustedKey.status = TrustedKey.TrustStatus.TRUSTED
            trustedKey.trustLevel = 5
            trustedKey.addReason("Verified via QR Code")
            trustStore.addKey(trustedKey)
            Toast.makeText(this, "Peer trusted: ${metadata.peerId}", Toast.LENGTH_LONG).show()
            finish()
        } else {
            Toast.makeText(this, "Invalid QR Code", Toast.LENGTH_LONG).show()
        }
    }
}
