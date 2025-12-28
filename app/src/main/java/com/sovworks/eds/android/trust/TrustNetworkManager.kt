package com.sovworks.eds.android.trust

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.sovworks.eds.android.identity.Identity
import com.sovworks.eds.android.identity.IdentityManager
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters

object TrustNetworkManager {
    private val gson = Gson()

    fun exportTrustNetwork(identity: Identity, keysToExport: List<TrustedKey>, rotations: List<KeyRotationCertificate> = emptyList()): TrustNetworkPackage? {
        val privateKey = IdentityManager.getDecryptedPrivateKey(identity) ?: return null
        val dataToSign = gson.toJson(mapOf("keys" to keysToExport, "rotations" to rotations))
        val signature = IdentityManager.sign(privateKey, dataToSign.toByteArray())
        
        return TrustNetworkPackage(
            issuerPublicKeyBase64 = identity.publicKeyBase64,
            trustedKeys = keysToExport,
            rotations = rotations,
            signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP)
        )
    }

    fun verifyRotationCertificate(certificate: KeyRotationCertificate): Boolean {
        return try {
            val oldPublicKey = Ed25519PublicKeyParameters(Base64.decode(certificate.oldPublicKeyBase64, Base64.NO_WRAP), 0)
            val newPublicKeyBytes = Base64.decode(certificate.newPublicKeyBase64, Base64.NO_WRAP)
            val signature = Base64.decode(certificate.signatureBase64, Base64.NO_WRAP)
            
            IdentityManager.verify(oldPublicKey, newPublicKeyBytes, signature)
        } catch (e: Exception) {
            false
        }
    }

    fun verifyAndImportNetwork(context: Context, networkPackage: TrustNetworkPackage): Boolean {
        val trustStore = TrustStore.getInstance(context)
        
        // 1. Prüfen, ob wir dem Aussteller vertrauen
        if (!trustStore.isTrusted(networkPackage.issuerPublicKeyBase64)) {
            return false
        }

        // 2. Signatur verifizieren
        val issuerPublicKey = Ed25519PublicKeyParameters(Base64.decode(networkPackage.issuerPublicKeyBase64, Base64.NO_WRAP), 0)
        val dataToVerify = gson.toJson(mapOf("keys" to networkPackage.trustedKeys, "rotations" to networkPackage.rotations))
        val signature = Base64.decode(networkPackage.signatureBase64, Base64.NO_WRAP)
        
        if (IdentityManager.verify(issuerPublicKey, dataToVerify.toByteArray(), signature)) {
            // 3. Rotationen anwenden
            for (rotation in networkPackage.rotations) {
                if (verifyRotationCertificate(rotation)) {
                    trustStore.updateKeyAfterRotation(rotation.oldPublicKeyBase64, rotation.newPublicKeyBase64)
                }
            }

            // 4. Keys importieren
            for (key in networkPackage.trustedKeys) {
                val existing = trustStore.getKey(key.getFingerprint())
                if (existing == null) {
                    trustStore.addKey(key)
                }
            }
            return true
        }
        
        return false
    }
}
