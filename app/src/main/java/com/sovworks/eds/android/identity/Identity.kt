package com.sovworks.eds.android.identity

import android.util.Base64
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters

data class Identity(
    val id: String, // z.B. Anzeigename oder UUID
    val publicKeyBase64: String,
    private val privateKeyBase64: String? = null
) {
    fun getPublicKey(): Ed25519PublicKeyParameters {
        return Ed25519PublicKeyParameters(Base64.decode(publicKeyBase64, Base64.NO_WRAP), 0)
    }

    fun getPrivateKey(): Ed25519PrivateKeyParameters? {
        return privateKeyBase64?.let {
            Ed25519PrivateKeyParameters(Base64.decode(it, Base64.NO_WRAP), 0)
        }
    }

    fun hasPrivateKey(): Boolean = privateKeyBase64 != null

    fun getFingerprint(): String {
        // Der Public Key selbst dient als eindeutige Kennung
        return publicKeyBase64
    }
}
