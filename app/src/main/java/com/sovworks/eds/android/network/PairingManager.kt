package com.sovworks.eds.android.network

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder

import com.sovworks.eds.android.identity.Identity
import com.sovworks.eds.android.identity.IdentityManager

object PairingManager {

    fun createMyMetadata(identity: Identity): ConnectionMetadata {
        return ConnectionMetadata(
            peerId = identity.id,
            publicKeyBase64 = identity.publicKeyBase64,
            iceServers = IceServersRegistry.getConfigs()
        )
    }

    fun generateQrCode(metadata: ConnectionMetadata, size: Int = 512): Bitmap? {
        val json = metadata.toJson()
        return try {
            val matrix = MultiFormatWriter().encode(json, BarcodeFormat.QR_CODE, size, size)
            BarcodeEncoder().createBitmap(matrix)
        } catch (e: Exception) {
            null
        }
    }

    fun parseQrCodeResult(result: String): ConnectionMetadata? {
        return try {
            ConnectionMetadata.fromJson(result)
        } catch (e: Exception) {
            null
        }
    }
}
