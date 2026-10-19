package com.project.p2p.client.crypto;

import com.project.p2p.common.crypto.CryptoPrimitives;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.AEADBadTagException;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.*;

class ClientCryptoTest {

    @Test
    void testClientKeyManagerLifecycleAndAtRestEncryption(@TempDir Path tempDir) throws Exception {
        ClientKeyManager km1 = new ClientKeyManager(tempDir);
        km1.initialize("alice", "correct-passphrase-123");

        assertNotNull(km1.getIdentityKeyPair());
        assertNotNull(km1.getKeyAgreementKeyPair());
        assertNotNull(km1.getIdentityPublicKeyBase64());
        assertNotNull(km1.getKeyAgreementPublicKeyBase64());
        assertTrue(km1.getIdentityFingerprint().startsWith("SHA256:"));

        String originalFp = km1.getIdentityFingerprint();

        // Reload with correct passphrase
        ClientKeyManager km2 = new ClientKeyManager(tempDir);
        km2.initialize("alice", "correct-passphrase-123");
        assertEquals(originalFp, km2.getIdentityFingerprint(), "Reloaded key must have matching fingerprint");

        // Reload with wrong passphrase must fail with AEADBadTagException (tamper/auth fail)
        ClientKeyManager km3 = new ClientKeyManager(tempDir);
        assertThrows(GeneralSecurityException.class, () ->
                km3.initialize("alice", "wrong-passphrase-456"));
    }

    @Test
    void testTrustStoreTofuBehavior(@TempDir Path tempDir) {
        Path trustFile = tempDir.resolve("trust.properties");
        TrustStore store = new TrustStore(trustFile);

        String aliceFp = "SHA256:01:02:03:04:05";
        assertFalse(store.hasRecordedKey("alice"));

        // Trust on first use
        store.trustFingerprint("alice", aliceFp);
        assertTrue(store.hasRecordedKey("alice"));
        assertTrue(store.isTrusted("alice", aliceFp));
        assertFalse(store.hasKeyChanged("alice", aliceFp));

        // Key change detection (MITM alert)
        String rogueFp = "SHA256:aa:bb:cc:dd:ee";
        assertTrue(store.hasKeyChanged("alice", rogueFp));
        assertFalse(store.isTrusted("alice", rogueFp));

        // Reload from disk to verify persistence
        TrustStore reloadedStore = new TrustStore(trustFile);
        assertTrue(reloadedStore.isTrusted("alice", aliceFp));
        assertTrue(reloadedStore.hasKeyChanged("alice", rogueFp));
    }

    @Test
    void testClientKeyManagerSelfTest(@TempDir Path tempDir) throws Exception {
        ClientKeyManager km = new ClientKeyManager(tempDir);
        km.initialize("sarvansh", "my-strong-passphrase");
        assertTrue(km.selfTestSignature(), "Self-test signature must pass on initialized keys");
    }

    @Test
    void testTranscriptCanonicalNormalizationAndSignatureAgreement(@TempDir Path tempDir) throws Exception {
        ClientKeyManager senderKm = new ClientKeyManager(tempDir.resolve("sender"));
        senderKm.initialize("Ajitesh", "sender-pass-123");

        ClientKeyManager recipientKm = new ClientKeyManager(tempDir.resolve("recipient"));
        recipientKm.initialize("Sarvansh", "recipient-pass-123");

        String ephemeralKeyBase64 = "MCowBQYDK2VuAyEA8c3b...testEphemeralKey...";
        long timestamp = 1718000000000L;

        // Transcript computed with mixed-case strings
        byte[] mixedCaseTranscript = com.project.p2p.common.crypto.HandshakePayload.computeInitTranscript(
                "Ajitesh", "Sarvansh", ephemeralKeyBase64, timestamp
        );

        // Transcript computed with canonical lowercase strings
        byte[] lowerCaseTranscript = com.project.p2p.common.crypto.HandshakePayload.computeInitTranscript(
                "ajitesh", "sarvansh", ephemeralKeyBase64, timestamp
        );

        // Bit-for-bit identity check
        assertArrayEquals(lowerCaseTranscript, mixedCaseTranscript,
                "Transcript bytes must be bit-for-bit identical regardless of input casing");

        // Sender signs with mixed-case input
        byte[] signature = CryptoPrimitives.signEd25519(senderKm.getIdentityKeyPair().getPrivate(), mixedCaseTranscript);

        // Recipient verifies with lowercase input and sender's public key
        boolean verified = CryptoPrimitives.verifyEd25519(senderKm.getIdentityKeyPair().getPublic(), lowerCaseTranscript, signature);
        assertTrue(verified, "Signature must verify successfully when transcript is canonically normalized");

        // Tamper test: timestamp changed by 1 ms
        byte[] tamperedTranscript = com.project.p2p.common.crypto.HandshakePayload.computeInitTranscript(
                "ajitesh", "sarvansh", ephemeralKeyBase64, timestamp + 1
        );
        assertFalse(CryptoPrimitives.verifyEd25519(senderKm.getIdentityKeyPair().getPublic(), tamperedTranscript, signature),
                "Signature must be rejected if transcript timestamp is altered");

        // Tamper test: recipient swapped
        byte[] wrongRecipientTranscript = com.project.p2p.common.crypto.HandshakePayload.computeInitTranscript(
                "ajitesh", "eve", ephemeralKeyBase64, timestamp
        );
        assertFalse(CryptoPrimitives.verifyEd25519(senderKm.getIdentityKeyPair().getPublic(), wrongRecipientTranscript, signature),
                "Signature must be rejected if transcript recipient is altered");

        // Wrong public key test
        assertFalse(CryptoPrimitives.verifyEd25519(recipientKm.getIdentityKeyPair().getPublic(), lowerCaseTranscript, signature),
                "Signature must be rejected when verified with an incorrect public key");
    }
}
