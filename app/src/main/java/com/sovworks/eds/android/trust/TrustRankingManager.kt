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
     * Berechnet einen Bonus/Malus-Score basierend auf Interaktionen.
     * Bereich: -5.0 bis +5.0 (theoretisch)
     */
    fun calculateInteractionScore(key: TrustedKey): Double {
        var score = 0.0
        
        // Erfolgreiche Transfers bringen Bonus
        score += key.successfulTransfers * 0.1
        
        // Volumen bringt Bonus (1 Punkt pro 1GB)
        score += (key.totalBytesTransferred / (1024.0 * 1024.0 * 1024.0)) * 1.0
        
        // Fehlgeschlagene Transfers bringen Malus
        score -= key.failedTransfers * 0.5
        
        // Latenz-Malus (wenn durchschnittliche Latenz > 500ms)
        val avgLatency = key.averageLatencyMs
        if (avgLatency > 500) {
            score -= (avgLatency - 500) / 500.0
        }
        
        // Cap den Score
        return score.coerceIn(-5.0, 5.0)
    }
}
