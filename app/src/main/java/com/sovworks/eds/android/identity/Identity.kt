package com.sovworks.eds.android.identity

import android.util.Base64
import com.sovworks.eds.android.trust.KeyRotationCertificate
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters

data class Identity(
    val id: String, // z.B. Anzeigename oder UUID
    val publicKeyBase64: String,
    private val privateKeyBase64: String? = null,
    private val ivBase64: String? = null,
    val previousPublicKeyBase64: String? = null,
    val rotationSignatureBase64: String? = null,
    val lastRotationTimestamp: Long = System.currentTimeMillis(),
    val rotationIntervalDays: Int = 30
) {
    fun getPublicKey(): Ed25519PublicKeyParameters {
        return Ed25519PublicKeyParameters(Base64.decode(publicKeyBase64, Base64.NO_WRAP), 0)
    }

    fun getPrivateKey(decryptedValue: String? = null): Ed25519PrivateKeyParameters? {
        val keyToDecode = decryptedValue ?: privateKeyBase64
        return keyToDecode?.let {
            Ed25519PrivateKeyParameters(Base64.decode(it, Base64.NO_WRAP), 0)
        }
    }

    fun getEncryptedPrivateKey(): String? = privateKeyBase64
    fun getIv(): String? = ivBase64

    fun hasPrivateKey(): Boolean = privateKeyBase64 != null

    fun getFingerprint(): String {
        // Der Public Key selbst dient als eindeutige Kennung
        return publicKeyBase64
    }

    fun getRotationCertificate(): KeyRotationCertificate? {
        if (previousPublicKeyBase64 != null && rotationSignatureBase64 != null) {
            return KeyRotationCertificate(
                oldPublicKeyBase64 = previousPublicKeyBase64,
                newPublicKeyBase64 = publicKeyBase64,
                signatureBase64 = rotationSignatureBase64
            )
        }
        return null
    }
}
