package com.project.p2p.common.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Standard RFC 5869 HMAC-SHA256 Extract-and-Expand Key Derivation Function (HKDF).
 */
public final class Hkdf {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HASH_LEN = 32;

    private Hkdf() {}

    /**
     * Extracts a pseudorandom key (PRK) from Input Keying Material (IKM) and optional salt.
     *
     * @param salt optional salt (if null or empty, 32 zero bytes are used per RFC 5869)
     * @param ikm  input keying material (e.g. Diffie-Hellman shared secret)
     * @return 32-byte PRK
     */
    public static byte[] extract(byte[] salt, byte[] ikm) {
        if (salt == null || salt.length == 0) {
            salt = new byte[HASH_LEN];
        }
        return hmac(salt, ikm);
    }

    /**
     * Expands a PRK with application-specific context info into output keying material (OKM).
     *
     * @param prk    pseudorandom key of at least HASH_LEN bytes
     * @param info   optional context and application info
     * @param length length of output keying material in bytes (max 255 * HASH_LEN)
     * @return derived output bytes of requested length
     */
    public static byte[] expand(byte[] prk, byte[] info, int length) {
        if (length <= 0 || length > 255 * HASH_LEN) {
            throw new IllegalArgumentException("Invalid output length for HKDF-Expand: " + length);
        }
        if (info == null) {
            info = new byte[0];
        }

        int n = (int) Math.ceil((double) length / HASH_LEN);
        byte[] okm = new byte[length];
        byte[] t = new byte[0];
        int bytesFilled = 0;

        for (int i = 1; i <= n; i++) {
            byte[] input = new byte[t.length + info.length + 1];
            System.arraycopy(t, 0, input, 0, t.length);
            System.arraycopy(info, 0, input, t.length, info.length);
            input[input.length - 1] = (byte) i;

            t = hmac(prk, input);

            int copyLen = Math.min(HASH_LEN, length - bytesFilled);
            System.arraycopy(t, 0, okm, bytesFilled, copyLen);
            bytesFilled += copyLen;
        }

        return okm;
    }

    /**
     * Convenience one-step HKDF (Extract and Expand).
     */
    public static byte[] deriveKey(byte[] salt, byte[] ikm, byte[] info, int length) {
        byte[] prk = extract(salt, ikm);
        return expand(prk, info, length);
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }
}
