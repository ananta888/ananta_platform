package com.sovworks.eds.android.trust

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.sovworks.eds.android.identity.Identity
import com.sovworks.eds.android.identity.IdentityManager
import com.sovworks.eds.android.ui.messenger.MessengerRepository
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
        
        // 1. Prüfen, ob wir dem Aussteller vertrauen oder ob er in einer unserer Gruppen ist
        val isDirectlyTrusted = trustStore.isTrusted(networkPackage.issuerPublicKeyBase64)
        val isGroupMember = isPeerInAnyGroup(networkPackage.issuerPublicKeyBase64)
        
        if (!isDirectlyTrusted && !isGroupMember) {
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
                var existing = trustStore.getKey(key.getFingerprint())
                if (existing == null) {
                    existing = key
                    trustStore.addKey(existing)
                }
                // Empfehlung hinzufügen
                existing.addRecommendation(TrustRecommendation(
                    recommenderFingerprint = networkPackage.issuerPublicKeyBase64,
                    trustLevel = key.trustLevel
                ))
            }
            trustStore.save()
            return true
        }
        
        return false
    }

    /**
     * Berechnet den Trust-Rank eines Peers basierend auf direktem Vertrauen und Empfehlungen im Netzwerk.
     * Algorithmus: Score = DirectTrust + Sum(RecommenderScore * RecommendationLevel / 5.0)
     */
    fun calculateTrustRank(context: Context, fingerprint: String, maxDepth: Int = 2): Double {
        val trustStore = TrustStore.getInstance(context)
        val cache = mutableMapOf<String, Double>()
        return calculateTrustRankRecursive(trustStore, fingerprint, maxDepth, cache, mutableSetOf())
    }

    fun isPeerInAnyGroup(peerId: String): Boolean {
        return MessengerRepository.groups.value.values.any { group ->
            group.memberIds.contains(peerId)
        }
    }

    private fun calculateTrustRankRecursive(
        trustStore: TrustStore,
        fingerprint: String,
        depth: Int,
        cache: MutableMap<String, Double>,
        visited: MutableSet<String>
    ): Double {
        if (depth < 0 || fingerprint in visited) return 0.0
        if (cache.containsKey(fingerprint)) return cache[fingerprint]!!

        val key = trustStore.getKey(fingerprint) ?: return 0.0
        visited.add(fingerprint)
        
        // Basis: Unser direktes Vertrauen (0-5)
        var score = key.trustLevel.toDouble()

        // Indirektes Vertrauen f\u00fcr Gruppenmitglieder
        if (score == 0.0 && isPeerInAnyGroup(fingerprint)) {
            score = 1.0 // Minimales Basis-Vertrauen f\u00fcr Gruppenmitglieder
        }

        // Automatisches Trust-Ranking basierend auf Interaktionen
        score += TrustRankingManager.calculateInteractionScore(key)
        
        // Empfehlungen von anderen einbeziehen
        for (rec in key.getRecommendations()) {
            // Nur Recommender einbeziehen, denen wir selbst einen gewissen Trust-Score zuschreiben
            val recommenderScore = calculateTrustRankRecursive(trustStore, rec.recommenderFingerprint, depth - 1, cache, visited.toMutableSet())
            if (recommenderScore > 0) {
                // Gewichtete Empfehlung
                score += (recommenderScore * (rec.trustLevel.toDouble() / 5.0))
            }
        }

        cache[fingerprint] = score
        return score
    }
}
