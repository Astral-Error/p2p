package com.project.p2p.common.protocol;

import java.io.*;
import java.util.Objects;
import java.util.UUID;

/**
 * Structured payload for {@link MessageType#ACK_DELIVERED} and {@link MessageType#ACK_READ} frames.
 */
public final class AckPayload {

    private final UUID messageId;
    private final long ackSequenceNumber;
    private final long timestamp;

    public AckPayload(UUID messageId, long ackSequenceNumber, long timestamp) {
        this.messageId = Objects.requireNonNull(messageId, "messageId must not be null");
        this.ackSequenceNumber = ackSequenceNumber;
        this.timestamp = timestamp;
    }

    public static AckPayload of(UUID messageId, long ackSequenceNumber) {
        return new AckPayload(messageId, ackSequenceNumber, System.currentTimeMillis());
    }

    public byte[] toBytes() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeLong(messageId.getMostSignificantBits());
            dos.writeLong(messageId.getLeastSignificantBits());
            dos.writeLong(ackSequenceNumber);
            dos.writeLong(timestamp);
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize AckPayload", e);
        }
    }

    public static AckPayload fromBytes(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes must not be null");
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));

        long mostSig = dis.readLong();
        long leastSig = dis.readLong();
        UUID messageId = new UUID(mostSig, leastSig);

        long ackSequence = dis.readLong();
        long timestamp = dis.readLong();

        return new AckPayload(messageId, ackSequence, timestamp);
    }

    public UUID getMessageId() {
        return messageId;
    }

    public long getAckSequenceNumber() {
        return ackSequenceNumber;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AckPayload that = (AckPayload) o;
        return ackSequenceNumber == that.ackSequenceNumber &&
                timestamp == that.timestamp &&
                Objects.equals(messageId, that.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, ackSequenceNumber, timestamp);
    }
}
