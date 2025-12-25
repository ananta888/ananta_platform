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
    private TrustStatus status;
    private List<String> reasons;
    private long addedTimestamp;
    private long expiresTimestamp;

    public TrustedKey(String publicKey, String fingerprint) {
        this.publicKey = publicKey;
        this.fingerprint = fingerprint;
        this.status = TrustStatus.PENDING;
        this.reasons = new ArrayList<>();
        this.addedTimestamp = System.currentTimeMillis();
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public TrustStatus getStatus() {
        return status;
    }

    public void setStatus(TrustStatus status) {
        this.status = status;
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

    public long getExpiresTimestamp() {
        return expiresTimestamp;
    }

    public void setExpiresTimestamp(long expiresTimestamp) {
        this.expiresTimestamp = expiresTimestamp;
    }
}
