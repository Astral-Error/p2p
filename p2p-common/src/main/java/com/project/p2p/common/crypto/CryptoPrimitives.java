package com.project.p2p.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * Core cryptographic implementations using standard Java 21 security providers.
 * Covers Ed25519 signatures, X25519 Diffie-Hellman, AES-256-GCM, and fingerprinting.
 */
public final class CryptoPrimitives {

    public static final String ED25519_ALGORITHM = "Ed25519";
    public static final String X25519_ALGORITHM = "X25519";
    public static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";
    public static final int GCM_TAG_LENGTH_BITS = 128;
    public static final int GCM_NONCE_LENGTH_BYTES = 12;
    public static final int AES_KEY_LENGTH_BYTES = 32;

    private CryptoPrimitives() {}

    // --- Key Generation ---

    public static KeyPair generateEd25519KeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(ED25519_ALGORITHM);
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 not supported in this runtime", e);
        }
    }

    public static KeyPair generateX25519KeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(X25519_ALGORITHM);
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("X25519 not supported in this runtime", e);
        }
    }

    // --- Key Agreement ---

    public static byte[] computeSharedSecret(PrivateKey privateKey, PublicKey peerPublicKey) {
        try {
            KeyAgreement ka = KeyAgreement.getInstance(X25519_ALGORITHM);
            ka.init(privateKey);
            ka.doPhase(peerPublicKey, true);
            return ka.generateSecret();
        } catch (GeneralSecurityException e) {
            throw new SecurityException("Failed to compute X25519 shared secret", e);
        }
    }

    // --- Signatures ---

    public static byte[] signEd25519(PrivateKey privateKey, byte[] data) {
        try {
            Signature sig = Signature.getInstance(ED25519_ALGORITHM);
            sig.initSign(privateKey);
            sig.update(data);
            return sig.sign();
        } catch (GeneralSecurityException e) {
            throw new SecurityException("Failed to generate Ed25519 signature", e);
        }
    }

    public static boolean verifyEd25519(PublicKey publicKey, byte[] data, byte[] signature) {
        try {
            Signature sig = Signature.getInstance(ED25519_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    // --- Key Encoding & Decoding ---

    public static String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public static String encodePrivateKey(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    public static PublicKey decodeEd25519PublicKey(String base64) throws GeneralSecurityException {
        byte[] bytes = Base64.getDecoder().decode(base64.trim());
        KeyFactory kf = KeyFactory.getInstance(ED25519_ALGORITHM);
        return kf.generatePublic(new X509EncodedKeySpec(bytes));
    }

    public static PrivateKey decodeEd25519PrivateKey(String base64) throws GeneralSecurityException {
        byte[] bytes = Base64.getDecoder().decode(base64.trim());
        KeyFactory kf = KeyFactory.getInstance(ED25519_ALGORITHM);
        return kf.generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    public static PublicKey decodeX25519PublicKey(String base64) throws GeneralSecurityException {
        byte[] bytes = Base64.getDecoder().decode(base64.trim());
        KeyFactory kf = KeyFactory.getInstance(X25519_ALGORITHM);
        return kf.generatePublic(new X509EncodedKeySpec(bytes));
    }

    public static PrivateKey decodeX25519PrivateKey(String base64) throws GeneralSecurityException {
        byte[] bytes = Base64.getDecoder().decode(base64.trim());
        KeyFactory kf = KeyFactory.getInstance(X25519_ALGORITHM);
        return kf.generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    // --- Key Fingerprint ---

    /**
     * Computes a deterministic SHA-256 fingerprint from an encoded public key.
     * Formatted as: SHA256:xx:xx:... (colon-separated lowercase hex).
     */
    public static String computeFingerprint(byte[] publicKeyBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKeyBytes);
            StringBuilder sb = new StringBuilder("SHA256:");
            for (int i = 0; i < hash.length; i++) {
                sb.append(String.format("%02x", hash[i]));
                if (i < hash.length - 1) {
                    sb.append(":");
                }
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static byte[] sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String computeFingerprint(PublicKey key) {
        return computeFingerprint(key.getEncoded());
    }

    public static String computeFingerprintFromBase64(String base64Key) {
        byte[] bytes = Base64.getDecoder().decode(base64Key.trim());
        return computeFingerprint(bytes);
    }

    // --- Authenticated Encryption (AES-256-GCM) ---

    public static byte[] encryptAesGcm(byte[] key, byte[] nonce, byte[] plaintext, byte[] aad) throws GeneralSecurityException {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(nonce, "nonce must not be null");
        if (nonce.length != GCM_NONCE_LENGTH_BYTES) {
            throw new IllegalArgumentException("Nonce length must be exactly 12 bytes");
        }

        Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

        if (aad != null && aad.length > 0) {
            cipher.updateAAD(aad);
        }

        return cipher.doFinal(plaintext);
    }

    public static byte[] decryptAesGcm(byte[] key, byte[] nonce, byte[] ciphertext, byte[] aad) throws GeneralSecurityException {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(nonce, "nonce must not be null");
        if (nonce.length != GCM_NONCE_LENGTH_BYTES) {
            throw new IllegalArgumentException("Nonce length must be exactly 12 bytes");
        }

        Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

        if (aad != null && aad.length > 0) {
            cipher.updateAAD(aad);
        }

        return cipher.doFinal(ciphertext);
    }
}
