package com.sovworks.eds.android.trust

import android.content.Context

object TrustRankingManager {

    fun recordSuccessfulTransfer(context: Context, peerId: String, bytes: Long) {
        val trustStore = TrustStore.getInstance(context)
        val key = trustStore.getKey(peerId) ?: return
        key.incrementSuccessfulTransfers()
        key.addBytesTransferred(bytes)
        trustStore.save()
    }

    fun recordFailedTransfer(context: Context, peerId: String) {
        val trustStore = TrustStore.getInstance(context)
        val key = trustStore.getKey(peerId) ?: return
        key.incrementFailedTransfers()
        trustStore.save()
    }

    fun recordLatency(context: Context, peerId: String, latencyMs: Long) {
        val trustStore = TrustStore.getInstance(context)
        val key = trustStore.getKey(peerId) ?: return
        key.addLatencyMeasurement(latencyMs)
        // Wir speichern regelmäßig, aber nicht bei jeder Messung
        if (System.currentTimeMillis() % 10 == 0L) { // Zufällig ca. alle 10 Messungen
             trustStore.save()
        }
    }

    /**
     * Berechnet einen Bonus/Malus-Score basierend auf Interaktionen und Empfehlungen.
     * Bereich: -5.0 bis +5.0
     */
    fun calculateInteractionScore(key: TrustedKey, includeRecommendations: Boolean = true): Double {
        var score = 0.0
        
        // 1. Gewichtete Interaktionen
        val transferWeight = 0.2
        val volumeWeight = 1.5 // pro GB
        val failureWeight = 1.0 // Malus
        val latencyThreshold = 300.0
        val latencyPenalty = 0.002 // pro ms über Schwelle
        
        score += key.successfulTransfers * transferWeight
        score += (key.totalBytesTransferred / (1024.0 * 1024.0 * 1024.0)) * volumeWeight
        score -= key.failedTransfers * failureWeight
        
        val avgLatency = key.averageLatencyMs
        if (avgLatency > latencyThreshold) {
            score -= (avgLatency - latencyThreshold) * latencyPenalty
        }

        // 2. Zeitliche Dämpfung (Time Decay)
        // Der Einfluss von Interaktionen nimmt über Zeit ab (Halbwertszeit ca. 30 Tage)
        if (key.lastInteractionTimestamp > 0) {
            val daysSinceLastInteraction = (System.currentTimeMillis() - key.lastInteractionTimestamp) / (1000.0 * 60 * 60 * 24)
            val decayFactor = Math.exp(-daysSinceLastInteraction / 30.0)
            score *= decayFactor
        }

        // 3. Empfehlungen einbeziehen
        if (includeRecommendations) {
            val recs = key.recommendations
            if (recs != null && recs.isNotEmpty()) {
                val avgRecTrust = recs.map { it.trustLevel }.average()
                // Empfehlungen geben bis zu 2.0 Punkte Bonus
                score += (avgRecTrust / 5.0) * 2.0
            }
        }
        
        return score.coerceIn(-5.0, 5.0)
    }

    fun updateTrustLevel(context: Context, peerId: String) {
        val trustStore = TrustStore.getInstance(context)
        val key = trustStore.getKey(peerId) ?: return
        
        val interactionScore = calculateInteractionScore(key)
        
        // Mapping von Score (-5 bis 5) auf Sterne (0 bis 5)
        // Ein Score von 0 (neutral) ergibt ca. 2 Sterne (Basis-Vertrauen für bekannte Peers)
        val calculatedStars = ((interactionScore + 5.0) / 2.0).toInt().coerceIn(0, 5)
        
        // Wir aktualisieren den Trust-Level nur, wenn er nicht manuell festgesetzt wurde?
        // Oder wir kombinieren ihn. Hier setzen wir ihn einfach mal.
        key.trustLevel = calculatedStars
        
        if (calculatedStars >= 4) {
            key.status = TrustedKey.TrustStatus.TRUSTED
        } else if (calculatedStars <= 1 && key.failedTransfers > 5) {
            key.status = TrustedKey.TrustStatus.DISTRUSTED
        }

        trustStore.save()
    }
}
