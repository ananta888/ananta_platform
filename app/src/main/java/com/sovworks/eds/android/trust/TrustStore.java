package com.sovworks.eds.android.trust;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sovworks.eds.android.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class TrustStore {
    private static final String STORE_FILE_NAME = "trust_store.json";
    private static TrustStore instance;
    private final Context context;
    private Map<String, TrustedKey> keys;
    private final Gson gson;

    private TrustStore(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        this.keys = new HashMap<>();
        load();
    }

    public static synchronized TrustStore getInstance(Context context) {
        if (instance == null) {
            instance = new TrustStore(context);
        }
        return instance;
    }

    private void load() {
        File file = new File(context.getFilesDir(), STORE_FILE_NAME);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Type type = new TypeToken<HashMap<String, TrustedKey>>() {}.getType();
                Map<String, TrustedKey> loadedKeys = gson.fromJson(reader, type);
                if (loadedKeys != null) {
                    keys = loadedKeys;
                }
            } catch (IOException e) {
                Logger.log("Failed to load trust store: " + e.getMessage());
            }
        }
    }

    public synchronized void save() {
        File file = new File(context.getFilesDir(), STORE_FILE_NAME);
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(keys, writer);
        } catch (IOException e) {
            Logger.log("Failed to save trust store: " + e.getMessage());
        }
    }

    public synchronized void addKey(TrustedKey key) {
        keys.put(key.getFingerprint(), key);
        save();
    }

    public synchronized void updateKeyAfterRotation(String oldFingerprint, String newPublicKey) {
        TrustedKey key = keys.remove(oldFingerprint);
        if (key != null) {
            // In dieser Implementierung ist der Fingerprint der Public Key
            TrustedKey newKey = new TrustedKey(newPublicKey, newPublicKey, key.getName());
            newKey.setStatus(key.getStatus());
            newKey.setTrustLevel(key.getTrustLevel());
            // Kopiere weitere Attribute falls nötig
            keys.put(newKey.getFingerprint(), newKey);
            save();
        }
    }

    public synchronized TrustedKey getKey(String fingerprint) {
        return keys.get(fingerprint);
    }

    public synchronized Map<String, TrustedKey> getAllKeys() {
        return new HashMap<>(keys);
    }

    public synchronized void removeKey(String fingerprint) {
        keys.remove(fingerprint);
        save();
    }

    public synchronized boolean isTrusted(String fingerprint) {
        TrustedKey key = keys.get(fingerprint);
        return key != null && key.getStatus() == TrustedKey.TrustStatus.TRUSTED;
    }
}
