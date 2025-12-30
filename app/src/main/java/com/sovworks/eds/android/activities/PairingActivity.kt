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
import com.sovworks.eds.android.network.WebRtcService
import com.sovworks.eds.android.trust.TrustStore
import com.sovworks.eds.android.trust.TrustedKey
import com.sovworks.eds.android.navigation.Screen
import com.sovworks.eds.android.ui.setSettingsContent
import android.app.AlertDialog

class PairingActivity : ComponentActivity() {
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleScanResult(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setSettingsContent(
            screen = Screen.Pairing,
            onStartPairingScanner = { startScanner() }
        )
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
            trustedKey.peerId = metadata.peerId
            trustedKey.status = TrustedKey.TrustStatus.TRUSTED
            trustedKey.trustLevel = 5
            trustedKey.addReason("Verified via QR Code")
            trustStore.addKey(trustedKey)
            Toast.makeText(this, "Peer trusted: ${metadata.peerId}", Toast.LENGTH_LONG).show()
            AlertDialog.Builder(this)
                .setTitle("Connect now?")
                .setMessage("Pairing saved. You can connect now or later.")
                .setPositiveButton("Connect") { _, _ ->
                    WebRtcService.getPeerConnectionManager()?.requestConnection(metadata.publicKeyBase64)
                    Toast.makeText(this, "Connecting to ${metadata.peerId}", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .setNegativeButton("Later") { _, _ ->
                    finish()
                }
                .setCancelable(true)
                .show()
        } else {
            Toast.makeText(this, "Invalid QR Code", Toast.LENGTH_LONG).show()
        }
    }
}
