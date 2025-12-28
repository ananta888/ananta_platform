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
import com.sovworks.eds.android.navigation.Screen
import com.sovworks.eds.android.ui.setSettingsContent

class IdentitySyncActivity : ComponentActivity() {
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleScanResult(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setSettingsContent(
            screen = Screen.IdentitySync,
            onStartIdentityScanner = { startScanner() }
        )
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
