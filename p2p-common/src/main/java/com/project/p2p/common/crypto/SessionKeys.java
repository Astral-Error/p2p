package com.project.p2p.common.crypto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages directional session keys, unique nonce generation, and replay defense
 * for an established secure peer-to-peer session.
 */
public final class SessionKeys {

    private final String sessionId;
    private final byte[] outboundKey;
    private final byte[] inboundKey;
    private final byte[] sessionSalt;
    private final AtomicLong outboundCounter = new AtomicLong(1);
    private final Set<Long> seenInboundSequences = ConcurrentHashMap.newKeySet();

    public SessionKeys(String sessionId, byte[] outboundKey, byte[] inboundKey, byte[] sessionSalt) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.outboundKey = Objects.requireNonNull(outboundKey, "outboundKey must not be null");
        this.inboundKey = Objects.requireNonNull(inboundKey, "inboundKey must not be null");
        this.sessionSalt = Objects.requireNonNull(sessionSalt, "sessionSalt must not be null");
    }

    /**
     * Derives directional session keys and session salt using HKDF-SHA256.
     *
     * @param sharedSecret 32-byte X25519 DH shared secret
     * @param transcriptHash contextual hash of the handshake transcript
     * @param isInitiator true if this peer initiated the handshake
     * @param sessionId unique session identifier
     */
    public static SessionKeys derive(byte[] sharedSecret, byte[] transcriptHash, boolean isInitiator, String sessionId) {
        // Derive 76 bytes total: 32 bytes KeyA, 32 bytes KeyB, 12 bytes Salt
        byte[] info = "P2P-SESSION-V1".getBytes(StandardCharsets.UTF_8);
        byte[] okm = Hkdf.deriveKey(transcriptHash, sharedSecret, info, 76);

        byte[] keyInitiatorToResponder = Arrays.copyOfRange(okm, 0, 32);
        byte[] keyResponderToInitiator = Arrays.copyOfRange(okm, 32, 64);
        byte[] salt = Arrays.copyOfRange(okm, 64, 76);

        byte[] outbound = isInitiator ? keyInitiatorToResponder : keyResponderToInitiator;
        byte[] inbound = isInitiator ? keyResponderToInitiator : keyInitiatorToResponder;

        return new SessionKeys(sessionId, outbound, inbound, salt);
    }

    /**
     * Generates a guaranteed-unique 12-byte (96-bit) nonce for outbound AES-GCM encryption.
     * Structure: [4 bytes from session salt][8 bytes monotonic sequence number].
     */
    public byte[] nextOutboundNonce() {
        long seq = outboundCounter.getAndIncrement();
        ByteBuffer buf = ByteBuffer.allocate(CryptoPrimitives.GCM_NONCE_LENGTH_BYTES);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put(sessionSalt, 0, 4);
        buf.putLong(seq);
        return buf.array();
    }

    /**
     * Validates incoming sequence number to prevent replay attacks.
     *
     * @param seq sequence number received in the encrypted envelope
     * @return true if valid and not seen before; false if replayed
     */
    public boolean checkAndRecordInboundSequence(long seq) {
        return seenInboundSequences.add(seq);
    }

    public String getSessionId() {
        return sessionId;
    }

    public byte[] getOutboundKey() {
        return Arrays.copyOf(outboundKey, outboundKey.length);
    }

    public byte[] getInboundKey() {
        return Arrays.copyOf(inboundKey, inboundKey.length);
    }
}
