package com.project.p2p.common.protocol;

import com.project.p2p.common.protocol.exceptions.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Stateful, streaming binary frame decoder.
 *
 * <p>Handles TCP stream fragmentation (partial headers, partial payloads) and
 * aggregation (multiple frames arriving in a single read). Enforces bounded memory
 * buffering and validates all protocol invariants.
 */
public class FrameDecoder {

    private final int maxPayloadSize;
    private final int maxBufferSize;
    private final ByteArrayOutputStream accumulator = new ByteArrayOutputStream();

    public FrameDecoder() {
        this(ProtocolConstants.DEFAULT_MAX_PAYLOAD_SIZE, ProtocolConstants.MAX_ACCUMULATOR_BUFFER_SIZE);
    }

    public FrameDecoder(int maxPayloadSize, int maxBufferSize) {
        if (maxPayloadSize <= 0) {
            throw new IllegalArgumentException("maxPayloadSize must be positive");
        }
        if (maxBufferSize < maxPayloadSize + ProtocolConstants.HEADER_SIZE) {
            throw new IllegalArgumentException("maxBufferSize must accommodate at least one max-sized frame");
        }
        this.maxPayloadSize = maxPayloadSize;
        this.maxBufferSize = maxBufferSize;
    }

    /**
     * Feeds incoming byte chunks into the decoder and extracts any completed frames.
     *
     * @param data chunk bytes
     * @param offset starting offset
     * @param length number of bytes
     * @return list of decoded ProtocolFrame instances (empty if more bytes needed)
     * @throws ProtocolException if stream violates framing, magic, version, CRC32, or size limits
     */
    public synchronized List<ProtocolFrame> feed(byte[] data, int offset, int length) throws ProtocolException {
        if (data == null || length <= 0) {
            return List.of();
        }

        if (accumulator.size() + length > maxBufferSize) {
            accumulator.reset();
            throw new OversizedPayloadException(accumulator.size() + length, maxBufferSize);
        }

        accumulator.write(data, offset, length);
        return decodeAccumulatedFrames();
    }

    public synchronized List<ProtocolFrame> feed(byte[] data) throws ProtocolException {
        if (data == null) {
            return List.of();
        }
        return feed(data, 0, data.length);
    }

    private List<ProtocolFrame> decodeAccumulatedFrames() throws ProtocolException {
        List<ProtocolFrame> frames = new ArrayList<>();
        byte[] currentBytes = accumulator.toByteArray();
        int offset = 0;
        int remaining = currentBytes.length;

        try {
            while (remaining >= ProtocolConstants.HEADER_SIZE) {
                ByteBuffer headerBuffer = ByteBuffer.wrap(currentBytes, offset, ProtocolConstants.HEADER_SIZE);
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

                // Verify Header CRC32 (computed over bytes 0..19 of the header)
                CRC32 crc = new CRC32();
                crc.update(currentBytes, offset, 20);
                long computedCrc = crc.getValue();
                long expectedCrc = Integer.toUnsignedLong(headerBuffer.getInt());

                if (computedCrc != expectedCrc) {
                    throw new CorruptFrameException(expectedCrc, computedCrc);
                }

                int totalFrameSize = ProtocolConstants.HEADER_SIZE + payloadLength;
                if (remaining < totalFrameSize) {
                    // Incomplete payload: wait for additional TCP chunks
                    break;
                }

                byte[] payload = new byte[payloadLength];
                if (payloadLength > 0) {
                    System.arraycopy(currentBytes, offset + ProtocolConstants.HEADER_SIZE, payload, 0, payloadLength);
                }

                ProtocolFrame frame = new ProtocolFrame(magic, version, messageType, flags, sequenceNumber, payload);
                frames.add(frame);

                offset += totalFrameSize;
                remaining -= totalFrameSize;
            }
        } finally {
            // Retain unconsumed fragmented bytes
            accumulator.reset();
            if (remaining > 0 && offset < currentBytes.length) {
                accumulator.write(currentBytes, offset, remaining);
            }
        }

        return frames;
    }

    public synchronized void reset() {
        accumulator.reset();
    }

    public synchronized int getBufferedBytesCount() {
        return accumulator.size();
    }

    public int getMaxPayloadSize() {
        return maxPayloadSize;
    }
}
