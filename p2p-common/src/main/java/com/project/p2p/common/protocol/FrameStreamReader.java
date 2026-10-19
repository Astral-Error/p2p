package com.project.p2p.common.protocol;

import com.project.p2p.common.protocol.exceptions.*;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

/**
 * Blocking stream reader that parses discrete {@link ProtocolFrame} instances directly
 * from an {@link InputStream}.
 */
public class FrameStreamReader {

    private final InputStream in;
    private final int maxPayloadSize;

    public FrameStreamReader(InputStream in) {
        this(in, ProtocolConstants.DEFAULT_MAX_PAYLOAD_SIZE);
    }

    public FrameStreamReader(InputStream in, int maxPayloadSize) {
        if (in == null) {
            throw new IllegalArgumentException("InputStream must not be null");
        }
        if (maxPayloadSize <= 0) {
            throw new IllegalArgumentException("maxPayloadSize must be positive");
        }
        this.in = in;
        this.maxPayloadSize = maxPayloadSize;
    }

    /**
     * Reads the next complete frame from the stream.
     *
     * @return next ProtocolFrame, or null if the stream cleanly reached EOF before any header bytes
     * @throws IOException on I/O error
     * @throws ProtocolException on protocol violation, corruption, or truncation
     */
    public ProtocolFrame readFrame() throws IOException, ProtocolException {
        byte[] headerBytes = new byte[ProtocolConstants.HEADER_SIZE];
        int firstByte = in.read();
        if (firstByte == -1) {
            // Clean EOF at frame boundary
            return null;
        }
        headerBytes[0] = (byte) firstByte;

        // Read remaining 23 header bytes
        readFully(in, headerBytes, 1, ProtocolConstants.HEADER_SIZE - 1);

        ByteBuffer headerBuffer = ByteBuffer.wrap(headerBytes);
        headerBuffer.order(ByteOrder.BIG_ENDIAN);

        int magic = headerBuffer.getInt();
        if (magic != ProtocolConstants.MAGIC_BYTES) {
            throw new InvalidMagicException(magic);
        }

        byte version = headerBuffer.get();
        if (version != ProtocolConstants.CURRENT_VERSION) {
            throw new UnsupportedVersionException(version);
        }

        byte typeCode = headerBuffer.get();
        MessageType messageType = MessageType.fromCode(typeCode);

        short flags = headerBuffer.getShort();
        long sequenceNumber = headerBuffer.getLong();
        int payloadLength = headerBuffer.getInt();

        if (payloadLength < 0 || payloadLength > maxPayloadSize) {
            throw new OversizedPayloadException(payloadLength, maxPayloadSize);
        }

        // Verify CRC32
        CRC32 crc = new CRC32();
        crc.update(headerBytes, 0, 20);
        long computedCrc = crc.getValue();
        long expectedCrc = Integer.toUnsignedLong(headerBuffer.getInt());

        if (computedCrc != expectedCrc) {
            throw new CorruptFrameException(expectedCrc, computedCrc);
        }

        byte[] payload = new byte[payloadLength];
        if (payloadLength > 0) {
            readFully(in, payload, 0, payloadLength);
        }

        return new ProtocolFrame(magic, version, messageType, flags, sequenceNumber, payload);
    }

    private static void readFully(InputStream in, byte[] buffer, int offset, int length) throws IOException, TruncatedFrameException {
        int readTotal = 0;
        while (readTotal < length) {
            int count = in.read(buffer, offset + readTotal, length - readTotal);
            if (count < 0) {
                throw new TruncatedFrameException(length, readTotal);
            }
            readTotal += count;
        }
    }
}
