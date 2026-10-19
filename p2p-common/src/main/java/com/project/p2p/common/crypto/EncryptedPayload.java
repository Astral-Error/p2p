package com.project.p2p.common.crypto;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Encrypted envelope carried inside CHAT_MESSAGE frames.
 * Format: [12-byte Nonce][8-byte SequenceNumber][16-byte MessageId][Ciphertext + 16-byte GCM Tag]
 */
public final class EncryptedPayload {

    private final byte[] nonce;
    private final long sequenceNumber;
    private final UUID messageId;
    private final byte[] ciphertext;

    public EncryptedPayload(byte[] nonce, long sequenceNumber, UUID messageId, byte[] ciphertext) {
        this.nonce = Objects.requireNonNull(nonce, "nonce must not be null");
        if (nonce.length != CryptoPrimitives.GCM_NONCE_LENGTH_BYTES) {
            throw new IllegalArgumentException("Nonce must be 12 bytes");
        }
        this.sequenceNumber = sequenceNumber;
        this.messageId = Objects.requireNonNull(messageId, "messageId must not be null");
        this.ciphertext = (ciphertext != null) ? Arrays.copyOf(ciphertext, ciphertext.length) : new byte[0];
    }

    /**
     * Constructs Associated Authenticated Data (AAD) binding message context.
     */
    public static byte[] computeAad(UUID messageId, long sequenceNumber, String sender, String recipient) {
        byte[] senderBytes = sender.trim().toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] recipientBytes = recipient.trim().toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(16 + 8 + senderBytes.length + recipientBytes.length);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putLong(messageId.getMostSignificantBits());
        buf.putLong(messageId.getLeastSignificantBits());
        buf.putLong(sequenceNumber);
        buf.put(senderBytes);
        buf.put(recipientBytes);
        return buf.array();
    }

    public byte[] toBytes() {
        int totalLen = 12 + 8 + 16 + ciphertext.length;
        ByteBuffer buf = ByteBuffer.allocate(totalLen);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put(nonce);
        buf.putLong(sequenceNumber);
        buf.putLong(messageId.getMostSignificantBits());
        buf.putLong(messageId.getLeastSignificantBits());
        buf.put(ciphertext);
        return buf.array();
    }

    public static EncryptedPayload fromBytes(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 36) {
            throw new IOException("Payload too small for EncryptedPayload (min 36 bytes)");
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.order(ByteOrder.BIG_ENDIAN);

        byte[] nonce = new byte[12];
        buf.get(nonce);
        long sequenceNumber = buf.getLong();
        long mostSig = buf.getLong();
        long leastSig = buf.getLong();
        UUID messageId = new UUID(mostSig, leastSig);

        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);

        return new EncryptedPayload(nonce, sequenceNumber, messageId, ciphertext);
    }

    public byte[] getNonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public byte[] getCiphertext() {
        return Arrays.copyOf(ciphertext, ciphertext.length);
    }
}
