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

    fun exportTrustSync(context: Context): String {
        val trustStore = TrustStore.getInstance(context)
        return gson.toJson(trustStore.getAllKeys().values.toList())
    }

    fun importTrustSync(context: Context, json: String) {
        try {
            val type = object : com.google.gson.reflect.TypeToken<List<TrustedKey>>() {}.type
            val keys: List<TrustedKey> = gson.fromJson(json, type)
            val trustStore = TrustStore.getInstance(context)
            keys.forEach { key ->
                val existing = trustStore.getKey(key.getFingerprint())
                if (existing == null || key.getTrustLevel() != existing.trustLevel || key.status != existing.status) {
                    trustStore.addKey(key)
                }
            }
        } catch (e: Exception) {
            // Log error
        }
    }
}
