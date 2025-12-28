package com.sovworks.eds.android.identity

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.sovworks.eds.android.trust.TrustStore
import com.sovworks.eds.android.trust.TrustedKey

data class SyncPackage(
    val identityName: String,
    val seedBase64: String,
    val trustedKeys: List<TrustedKey>
)

object IdentitySyncManager {
    private val gson = Gson()

    fun exportSyncPackage(context: Context): String? {
        val identity = IdentityManager.loadIdentity(context) ?: return null
        val privateKey = IdentityManager.getDecryptedPrivateKey(identity) ?: return null
        val trustStore = TrustStore.getInstance(context)
        
        val pkg = SyncPackage(
            identityName = identity.id,
            seedBase64 = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP),
            trustedKeys = trustStore.getAllKeys().values.toList()
        )
        
        return gson.toJson(pkg)
    }

    fun importSyncPackage(context: Context, json: String): Boolean {
        return try {
            val pkg = gson.fromJson(json, SyncPackage::class.java)
            val seed = Base64.decode(pkg.seedBase64, Base64.NO_WRAP)
            
            // 1. Identität wiederherstellen
            IdentityManager.recoverIdentity(context, seed, pkg.identityName)
            
            // 2. Trust-Network importieren
            val trustStore = TrustStore.getInstance(context)
            pkg.trustedKeys.forEach { key ->
                trustStore.addKey(key)
            }
            trustStore.save()
            
            true
        } catch (e: Exception) {
            false
        }
    }
}
