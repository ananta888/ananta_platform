package com.sovworks.eds.android.trust

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.sovworks.eds.android.identity.Identity
import com.sovworks.eds.android.identity.IdentityManager
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters

object TrustNetworkManager {
    private val gson = Gson()

    fun exportTrustNetwork(identity: Identity, keysToExport: List<TrustedKey>): TrustNetworkPackage? {
        val privateKey = IdentityManager.getDecryptedPrivateKey(identity) ?: return null
        val keysJson = gson.toJson(keysToExport)
        val signature = IdentityManager.sign(privateKey, keysJson.toByteArray())
        
        return TrustNetworkPackage(
            issuerPublicKeyBase64 = identity.publicKeyBase64,
            trustedKeys = keysToExport,
            signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP)
        )
    }

    fun verifyAndImportNetwork(context: Context, networkPackage: TrustNetworkPackage): Boolean {
        val trustStore = TrustStore.getInstance(context)
        
        // 1. Prüfen, ob wir dem Aussteller vertrauen
        if (!trustStore.isTrusted(networkPackage.issuerPublicKeyBase64)) {
            // Optional: Wenn wir den Aussteller noch nicht kennen, könnten wir ihn als PENDING hinzufügen
            // Aber für automatischen Import setzen wir Vertrauen voraus.
            return false
        }

        // 2. Signatur verifizieren
        val issuerPublicKey = Ed25519PublicKeyParameters(Base64.decode(networkPackage.issuerPublicKeyBase64, Base64.NO_WRAP), 0)
        val keysJson = gson.toJson(networkPackage.trustedKeys)
        val signature = Base64.decode(networkPackage.signatureBase64, Base64.NO_WRAP)
        
        if (IdentityManager.verify(issuerPublicKey, keysJson.toByteArray(), signature)) {
            // 3. Keys importieren
            for (key in networkPackage.trustedKeys) {
                // Wir übernehmen den Key, setzen aber ggf. den Status auf PENDING, 
                // da wir der Bewertung des anderen zwar "zuhören", aber sie nicht blind als TRUSTED übernehmen?
                // In diesem Fall: Wir fügen sie zum Archiv hinzu.
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
