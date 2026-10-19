package com.project.p2p.common.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CryptoPrimitivesTest {

    @Test
    void testEd25519SignAndVerify() {
        KeyPair kp = CryptoPrimitives.generateEd25519KeyPair();
        byte[] data = "Authenticated Message Content".getBytes(StandardCharsets.UTF_8);

        byte[] sig = CryptoPrimitives.signEd25519(kp.getPrivate(), data);
        assertNotNull(sig);
        assertEquals(64, sig.length);

        assertTrue(CryptoPrimitives.verifyEd25519(kp.getPublic(), data, sig));

        // Tampered data must fail verification
        byte[] tampered = "Authenticated Message Content!".getBytes(StandardCharsets.UTF_8);
        assertFalse(CryptoPrimitives.verifyEd25519(kp.getPublic(), tampered, sig));

        // Tampered signature must fail verification
        byte[] badSig = Arrays.copyOf(sig, sig.length);
        badSig[0] ^= 0xFF;
        assertFalse(CryptoPrimitives.verifyEd25519(kp.getPublic(), data, badSig));
    }

    @Test
    void testX25519KeyAgreementMatchingSecret() {
        KeyPair aliceKp = CryptoPrimitives.generateX25519KeyPair();
        KeyPair bobKp = CryptoPrimitives.generateX25519KeyPair();

        byte[] aliceSecret = CryptoPrimitives.computeSharedSecret(aliceKp.getPrivate(), bobKp.getPublic());
        byte[] bobSecret = CryptoPrimitives.computeSharedSecret(bobKp.getPrivate(), aliceKp.getPublic());

        assertEquals(32, aliceSecret.length);
        assertArrayEquals(aliceSecret, bobSecret, "X25519 shared secrets must match exactly");
    }

    @Test
    void testHkdfKeyDerivationMatching() {
        byte[] ikm = "SharedDiffieHellmanSecret32BytesLong".getBytes(StandardCharsets.UTF_8);
        byte[] salt = "HandshakeTranscriptHash".getBytes(StandardCharsets.UTF_8);
        byte[] info = "P2P-Chat-v1".getBytes(StandardCharsets.UTF_8);

        byte[] key1 = Hkdf.deriveKey(salt, ikm, info, 64);
        byte[] key2 = Hkdf.deriveKey(salt, ikm, info, 64);

        assertEquals(64, key1.length);
        assertArrayEquals(key1, key2);

        // Different info produces completely distinct output
        byte[] key3 = Hkdf.deriveKey(salt, ikm, "Different-Info".getBytes(StandardCharsets.UTF_8), 64);
        assertFalse(Arrays.equals(key1, key3));
    }

    @Test
    void testAesGcmEncryptionDecryptionRoundTrip() throws Exception {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x2A);
        byte[] nonce = new byte[12];
        Arrays.fill(nonce, (byte) 0x07);

        byte[] plaintext = "Hello Secret P2P World!".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "Associated Data (Metadata)".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = CryptoPrimitives.encryptAesGcm(key, nonce, plaintext, aad);
        assertNotNull(ciphertext);
        // Ciphertext includes 16-byte authentication tag
        assertEquals(plaintext.length + 16, ciphertext.length);

        byte[] decrypted = CryptoPrimitives.decryptAesGcm(key, nonce, ciphertext, aad);
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void testAesGcmTamperedCiphertextRejected() {
        byte[] key = new byte[32];
        byte[] nonce = new byte[12];
        byte[] plaintext = "Secret Message".getBytes(StandardCharsets.UTF_8);

        assertThrows(AEADBadTagException.class, () -> {
            byte[] ciphertext = CryptoPrimitives.encryptAesGcm(key, nonce, plaintext, null);
            // Flip a bit in the ciphertext
            ciphertext[0] ^= 0x01;
            CryptoPrimitives.decryptAesGcm(key, nonce, ciphertext, null);
        });
    }

    @Test
    void testAesGcmMismatchedAadRejected() {
        byte[] key = new byte[32];
        byte[] nonce = new byte[12];
        byte[] plaintext = "Secret Message".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "Context-1".getBytes(StandardCharsets.UTF_8);

        assertThrows(AEADBadTagException.class, () -> {
            byte[] ciphertext = CryptoPrimitives.encryptAesGcm(key, nonce, plaintext, aad);
            byte[] wrongAad = "Context-2".getBytes(StandardCharsets.UTF_8);
            CryptoPrimitives.decryptAesGcm(key, nonce, ciphertext, wrongAad);
        });
    }

    @Test
    void testFingerprintDeterminism() {
        KeyPair kp = CryptoPrimitives.generateEd25519KeyPair();
        String fp1 = CryptoPrimitives.computeFingerprint(kp.getPublic());
        String fp2 = CryptoPrimitives.computeFingerprint(kp.getPublic());

        assertNotNull(fp1);
        assertTrue(fp1.startsWith("SHA256:"));
        assertEquals(fp1, fp2);

        // Distinct key has distinct fingerprint
        KeyPair kp2 = CryptoPrimitives.generateEd25519KeyPair();
        String fp3 = CryptoPrimitives.computeFingerprint(kp2.getPublic());
        assertNotEquals(fp1, fp3);
    }

    @Test
    void testSessionKeysDirectionalSymmetryAndUniqueNonces() {
        byte[] sharedSecret = new byte[32];
        Arrays.fill(sharedSecret, (byte) 0x55);
        byte[] transcript = "HandshakeTranscript123".getBytes(StandardCharsets.UTF_8);

        SessionKeys initiatorKeys = SessionKeys.derive(sharedSecret, transcript, true, "sess-1");
        SessionKeys responderKeys = SessionKeys.derive(sharedSecret, transcript, false, "sess-1");

        // Directional symmetry: Initiator's outbound key is Responder's inbound key
        assertArrayEquals(initiatorKeys.getOutboundKey(), responderKeys.getInboundKey());
        assertArrayEquals(initiatorKeys.getInboundKey(), responderKeys.getOutboundKey());

        // Outbound nonces must be strictly unique under the session
        Set<String> nonces = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            byte[] n = initiatorKeys.nextOutboundNonce();
            String b64 = java.util.Base64.getEncoder().encodeToString(n);
            assertTrue(nonces.add(b64), "Nonce must never be repeated!");
        }

        // Inbound sequence replay detection
        assertTrue(responderKeys.checkAndRecordInboundSequence(1L));
        assertTrue(responderKeys.checkAndRecordInboundSequence(2L));
        assertFalse(responderKeys.checkAndRecordInboundSequence(1L), "Replayed sequence 1 must be rejected");
    }

    @Test
    void testHandshakePayloadSerialization() throws Exception {
        byte[] sig = new byte[64];
        Arrays.fill(sig, (byte) 0x09);
        HandshakePayload original = new HandshakePayload("alice", "bob", "b64-ephemeral-key", 123456789L, sig);

        byte[] bytes = original.toBytes();
        HandshakePayload restored = HandshakePayload.fromBytes(bytes);

        assertEquals("alice", restored.getSenderUsername());
        assertEquals("bob", restored.getRecipientUsername());
        assertEquals("b64-ephemeral-key", restored.getEphemeralPublicKeyBase64());
        assertEquals(123456789L, restored.getTimestamp());
        assertArrayEquals(sig, restored.getSignature());
    }

    @Test
    void testEncryptedPayloadSerialization() throws Exception {
        byte[] nonce = new byte[12];
        Arrays.fill(nonce, (byte) 0x11);
        UUID msgId = UUID.randomUUID();
        byte[] ciphertext = "encrypted_ciphertext_and_tag".getBytes(StandardCharsets.UTF_8);

        EncryptedPayload original = new EncryptedPayload(nonce, 42L, msgId, ciphertext);
        byte[] bytes = original.toBytes();

        EncryptedPayload restored = EncryptedPayload.fromBytes(bytes);
        assertArrayEquals(nonce, restored.getNonce());
        assertEquals(42L, restored.getSequenceNumber());
        assertEquals(msgId, restored.getMessageId());
        assertArrayEquals(ciphertext, restored.getCiphertext());
    }
}
