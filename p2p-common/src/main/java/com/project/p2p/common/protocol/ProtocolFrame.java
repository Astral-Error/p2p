package com.project.p2p.common.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable in-memory representation of a framed application-layer message.
 */
public final class ProtocolFrame {

    private final int magic;
    private final byte version;
    private final MessageType messageType;
    private final short flags;
    private final long sequenceNumber;
    private final byte[] payload;

    public ProtocolFrame(int magic, byte version, MessageType messageType, short flags, long sequenceNumber, byte[] payload) {
        this.magic = magic;
        this.version = version;
        this.messageType = Objects.requireNonNull(messageType, "messageType must not be null");
        this.flags = flags;
        this.sequenceNumber = sequenceNumber;
        this.payload = (payload != null && payload.length > 0) ? Arrays.copyOf(payload, payload.length) : new byte[0];
    }

    public static ProtocolFrame of(MessageType messageType, long sequenceNumber, byte[] payload) {
        return new ProtocolFrame(
                ProtocolConstants.MAGIC_BYTES,
                ProtocolConstants.CURRENT_VERSION,
                messageType,
                (short) 0,
                sequenceNumber,
                payload
        );
    }

    public static ProtocolFrame of(MessageType messageType, long sequenceNumber, short flags, byte[] payload) {
        return new ProtocolFrame(
                ProtocolConstants.MAGIC_BYTES,
                ProtocolConstants.CURRENT_VERSION,
                messageType,
                flags,
                sequenceNumber,
                payload
        );
    }

    public static ProtocolFrame ping(long sequenceNumber) {
        return of(MessageType.PING, sequenceNumber, new byte[0]);
    }

    public static ProtocolFrame pong(long sequenceNumber, byte[] pingPayload) {
        return of(MessageType.PONG, sequenceNumber, pingPayload);
    }

    public static ProtocolFrame disconnect(long sequenceNumber, String reason) {
        byte[] payload = (reason != null) ? reason.getBytes(StandardCharsets.UTF_8) : new byte[0];
        return of(MessageType.DISCONNECT, sequenceNumber, payload);
    }

    public int getMagic() {
        return magic;
    }

    public byte getVersion() {
        return version;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public short getFlags() {
        return flags;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public int getPayloadLength() {
        return payload.length;
    }

    public byte[] getPayload() {
        return Arrays.copyOf(payload, payload.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProtocolFrame that = (ProtocolFrame) o;
        return magic == that.magic &&
                version == that.version &&
                flags == that.flags &&
                sequenceNumber == that.sequenceNumber &&
                messageType == that.messageType &&
                Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(magic, version, messageType, flags, sequenceNumber);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @Override
    public String toString() {
        return String.format("ProtocolFrame{type=%s, seq=%d, flags=%d, payloadLen=%d}",
                messageType, sequenceNumber, flags, payload.length);
    }
}
