package com.project.p2p.client.crypto;

import com.project.p2p.common.crypto.CryptoPrimitives;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Manages local cryptographic identity and ephemeral key agreement keypairs.
 * Protects private keys at rest using PBKDF2-HMAC-SHA256 and AES-256-GCM encryption.
 */
public class ClientKeyManager {

    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int SALT_BYTES = 16;

    private final Path keyStoreDir;
    private KeyPair identityKeyPair;
    private KeyPair keyAgreementKeyPair;

    public ClientKeyManager() {
        this(Paths.get(System.getProperty("user.home"), ".p2p_chat", "keys"));
    }

    public ClientKeyManager(Path keyStoreDir) {
        this.keyStoreDir = keyStoreDir;
    }

    /**
     * Loads existing encrypted keys from disk or generates fresh keypairs if first time.
     *
     * @param username user identity
     * @param passphrase passphrase used to encrypt private keys at rest
     */
    public synchronized void initialize(String username, String passphrase) throws GeneralSecurityException, IOException {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(passphrase, "passphrase must not be null");

        Path keyFile = keyStoreDir.resolve(username.toLowerCase() + ".keystore");

        if (Files.exists(keyFile)) {
            loadEncryptedKeys(keyFile, passphrase);
        } else {
            generateAndPersistKeys(keyFile, passphrase);
        }

        if (!selfTestSignature()) {
            throw new GeneralSecurityException("Ed25519 identity keypair self-signature verification failed");
        }
    }

    private void generateAndPersistKeys(Path keyFile, String passphrase) throws GeneralSecurityException, IOException {
        identityKeyPair = CryptoPrimitives.generateEd25519KeyPair();
        keyAgreementKeyPair = CryptoPrimitives.generateX25519KeyPair();

        // Serialize keys to plain string
        String payload = CryptoPrimitives.encodePrivateKey(identityKeyPair.getPrivate()) + "\n" +
                         CryptoPrimitives.encodePublicKey(identityKeyPair.getPublic()) + "\n" +
                         CryptoPrimitives.encodePrivateKey(keyAgreementKeyPair.getPrivate()) + "\n" +
                         CryptoPrimitives.encodePublicKey(keyAgreementKeyPair.getPublic());

        byte[] plainBytes = payload.getBytes(StandardCharsets.UTF_8);

        // Derive AES key from passphrase
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] aesKey = deriveAesKey(passphrase, salt);

        byte[] nonce = new byte[CryptoPrimitives.GCM_NONCE_LENGTH_BYTES];
        new SecureRandom().nextBytes(nonce);

        byte[] ciphertext = CryptoPrimitives.encryptAesGcm(aesKey, nonce, plainBytes, salt);

        // File format: [16-byte salt][12-byte nonce][ciphertext...]
        ByteBuffer buf = ByteBuffer.allocate(SALT_BYTES + CryptoPrimitives.GCM_NONCE_LENGTH_BYTES + ciphertext.length);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put(salt);
        buf.put(nonce);
        buf.put(ciphertext);

        if (keyFile.getParent() != null) {
            Files.createDirectories(keyFile.getParent());
        }
        Files.write(keyFile, buf.array());
    }

    private void loadEncryptedKeys(Path keyFile, String passphrase) throws GeneralSecurityException, IOException {
        byte[] fileBytes = Files.readAllBytes(keyFile);
        if (fileBytes.length < SALT_BYTES + CryptoPrimitives.GCM_NONCE_LENGTH_BYTES) {
            throw new IOException("Corrupted key file: header too short");
        }

        ByteBuffer buf = ByteBuffer.wrap(fileBytes);
        buf.order(ByteOrder.BIG_ENDIAN);

        byte[] salt = new byte[SALT_BYTES];
        buf.get(salt);
        byte[] nonce = new byte[CryptoPrimitives.GCM_NONCE_LENGTH_BYTES];
        buf.get(nonce);
        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);

        byte[] aesKey = deriveAesKey(passphrase, salt);
        byte[] plainBytes = CryptoPrimitives.decryptAesGcm(aesKey, nonce, ciphertext, salt);

        String[] lines = new String(plainBytes, StandardCharsets.UTF_8).split("\n");
        if (lines.length != 4) {
            throw new IOException("Invalid keystore internal structure");
        }

        this.identityKeyPair = new KeyPair(
                CryptoPrimitives.decodeEd25519PublicKey(lines[1]),
                CryptoPrimitives.decodeEd25519PrivateKey(lines[0])
        );
        this.keyAgreementKeyPair = new KeyPair(
                CryptoPrimitives.decodeX25519PublicKey(lines[3]),
                CryptoPrimitives.decodeX25519PrivateKey(lines[2])
        );
    }

    private byte[] deriveAesKey(String passphrase, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
    }

    public KeyPair getIdentityKeyPair() {
        return identityKeyPair;
    }

    public KeyPair getKeyAgreementKeyPair() {
        return keyAgreementKeyPair;
    }

    public String getIdentityPublicKeyBase64() {
        return CryptoPrimitives.encodePublicKey(identityKeyPair.getPublic());
    }

    public String getKeyAgreementPublicKeyBase64() {
        return CryptoPrimitives.encodePublicKey(keyAgreementKeyPair.getPublic());
    }

    public String getIdentityFingerprint() {
        return CryptoPrimitives.computeFingerprint(identityKeyPair.getPublic());
    }

    public boolean selfTestSignature() {
        if (identityKeyPair == null || identityKeyPair.getPrivate() == null || identityKeyPair.getPublic() == null) {
            return false;
        }
        try {
            byte[] testPayload = "SELF_TEST_SIGNATURE_PAYLOAD".getBytes(StandardCharsets.UTF_8);
            byte[] sig = CryptoPrimitives.signEd25519(identityKeyPair.getPrivate(), testPayload);
            return CryptoPrimitives.verifyEd25519(identityKeyPair.getPublic(), testPayload, sig);
        } catch (Exception e) {
            return false;
        }
    }
}
