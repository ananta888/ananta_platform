package com.sovworks.eds.android.trust;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TrustedKey implements Serializable {
    public enum TrustStatus {
        TRUSTED, PENDING, DISTRUSTED
    }

    private String publicKey;
    private String fingerprint;
    private String name;
    private String peerId;
    private TrustStatus status;
    private int trustLevel; // 1-5 Sterne
    private List<String> reasons;
    private long addedTimestamp;
    private long lastSeenTimestamp;
    private long expiresTimestamp;
    private List<TrustRecommendation> recommendations;

    // Interaktionsstatistiken für automatisches Trust-Ranking
    private int successfulTransfers;
    private int failedTransfers;
    private long totalBytesTransferred;
    private long lastInteractionTimestamp;
    private double cumulativeLatencyMs;
    private int latencyMeasurements;

    public TrustedKey(String publicKey, String fingerprint, String name) {
        this.publicKey = publicKey;
        this.fingerprint = fingerprint;
        this.name = name;
        this.status = TrustStatus.PENDING;
        this.trustLevel = 0;
        this.reasons = new ArrayList<>();
        this.recommendations = new ArrayList<>();
        this.addedTimestamp = System.currentTimeMillis();
        this.lastSeenTimestamp = this.addedTimestamp;
    }

    public List<TrustRecommendation> getRecommendations() {
        if (recommendations == null) recommendations = new ArrayList<>();
        return recommendations;
    }

    public void addRecommendation(TrustRecommendation rec) {
        if (recommendations == null) recommendations = new ArrayList<>();
        for (int i = 0; i < recommendations.size(); i++) {
            if (recommendations.get(i).getRecommenderFingerprint().equals(rec.getRecommenderFingerprint())) {
                recommendations.set(i, rec);
                return;
            }
        }
        recommendations.add(rec);
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPeerId() {
        return peerId;
    }

    public void setPeerId(String peerId) {
        this.peerId = peerId;
    }

    public TrustStatus getStatus() {
        return status;
    }

    public void setStatus(TrustStatus status) {
        this.status = status;
    }

    public int getTrustLevel() {
        return trustLevel;
    }

    public void setTrustLevel(int trustLevel) {
        this.trustLevel = trustLevel;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void addReason(String reason) {
        if (!reasons.contains(reason)) {
            reasons.add(reason);
        }
    }

    public long getAddedTimestamp() {
        return addedTimestamp;
    }

    public long getLastSeenTimestamp() {
        return lastSeenTimestamp;
    }

    public void setLastSeenTimestamp(long lastSeenTimestamp) {
        this.lastSeenTimestamp = lastSeenTimestamp;
    }

    public long getExpiresTimestamp() {
        return expiresTimestamp;
    }

    public void setExpiresTimestamp(long expiresTimestamp) {
        this.expiresTimestamp = expiresTimestamp;
    }

    public int getSuccessfulTransfers() { return successfulTransfers; }
    public void incrementSuccessfulTransfers() { this.successfulTransfers++; this.lastInteractionTimestamp = System.currentTimeMillis(); }

    public int getFailedTransfers() { return failedTransfers; }
    public void incrementFailedTransfers() { this.failedTransfers++; this.lastInteractionTimestamp = System.currentTimeMillis(); }

    public long getTotalBytesTransferred() { return totalBytesTransferred; }
    public void addBytesTransferred(long bytes) { this.totalBytesTransferred += bytes; this.lastInteractionTimestamp = System.currentTimeMillis(); }

    public long getLastInteractionTimestamp() { return lastInteractionTimestamp; }

    public double getAverageLatencyMs() {
        return latencyMeasurements > 0 ? cumulativeLatencyMs / latencyMeasurements : 0;
    }
    public void addLatencyMeasurement(long latencyMs) {
        this.cumulativeLatencyMs += latencyMs;
        this.latencyMeasurements++;
        this.lastInteractionTimestamp = System.currentTimeMillis();
    }
}
