package com.project.p2p.client.crypto;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side Trust-On-First-Use (TOFU) identity fingerprint cache.
 * Detects unexpected peer public key changes to defend against Man-In-The-Middle attacks.
 */
public class TrustStore {

    private final Path storagePath;
    private final Map<String, String> trustedFingerprints = new ConcurrentHashMap<>();

    public TrustStore() {
        this(Paths.get(System.getProperty("user.home"), ".p2p_chat", "truststore.properties"));
    }

    public TrustStore(Path storagePath) {
        this.storagePath = storagePath;
        load();
    }

    private synchronized void load() {
        if (!Files.exists(storagePath)) {
            return;
        }
        try (InputStream in = Files.newInputStream(storagePath)) {
            Properties props = new Properties();
            props.load(in);
            for (String user : props.stringPropertyNames()) {
                trustedFingerprints.put(user.toLowerCase(), props.getProperty(user));
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to load trust store: " + e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            if (storagePath.getParent() != null) {
                Files.createDirectories(storagePath.getParent());
            }
            Properties props = new Properties();
            props.putAll(trustedFingerprints);
            try (OutputStream out = Files.newOutputStream(storagePath)) {
                props.store(out, "P2P Chat Verified Peer Fingerprints");
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to persist trust store: " + e.getMessage());
        }
    }

    public String getTrustedFingerprint(String username) {
        return trustedFingerprints.get(username.toLowerCase());
    }

    public void trustFingerprint(String username, String fingerprint) {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        trustedFingerprints.put(username.toLowerCase(), fingerprint);
        save();
    }

    public boolean isTrusted(String username, String fingerprint) {
        String existing = trustedFingerprints.get(username.toLowerCase());
        return existing != null && existing.equalsIgnoreCase(fingerprint);
    }

    public boolean hasKeyChanged(String username, String currentFingerprint) {
        String existing = trustedFingerprints.get(username.toLowerCase());
        return existing != null && !existing.equalsIgnoreCase(currentFingerprint);
    }

    public boolean hasRecordedKey(String username) {
        return trustedFingerprints.containsKey(username.toLowerCase());
    }

    public void clear() {
        trustedFingerprints.clear();
        save();
    }
}
