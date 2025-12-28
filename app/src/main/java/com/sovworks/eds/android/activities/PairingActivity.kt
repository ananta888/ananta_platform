package com.sovworks.eds.android.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

import com.sovworks.eds.android.identity.IdentityManager
import com.sovworks.eds.android.network.PairingManager
import com.sovworks.eds.android.trust.TrustStore
import com.sovworks.eds.android.trust.TrustedKey

class PairingActivity : ComponentActivity() {
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show()
        } else {
            val metadata = PairingManager.parseQrCodeResult(result.contents)
            if (metadata != null) {
                // Trust-Handshake: Peer zum TrustStore hinzufügen
                val trustStore = TrustStore.getInstance(this)
                val trustedKey = TrustedKey(
                    metadata.publicKeyBase64,
                    metadata.publicKeyBase64, // Fingerprint ist PK
                    metadata.peerId
                )
                trustedKey.status = TrustedKey.TrustStatus.TRUSTED
                trustedKey.trustLevel = 5 // Höchstes Vertrauen durch physischen Kontakt
                trustedKey.addReason("Verified via QR Code Handshake")
                
                trustStore.addKey(trustedKey)
                
                Toast.makeText(this, "Peer trusted: ${metadata.peerId}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Invalid QR Code", Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Scan a pairing QR code")
        options.setCameraId(0)
        options.setBeepEnabled(false)
        options.setBarcodeImageEnabled(true)
        barcodeLauncher.launch(options)
    }
}
