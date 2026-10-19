package com.project.p2p.common.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * Encodes {@link ProtocolFrame} instances into framed binary wire representation.
 *
 * <p>Header layout (24 bytes, Big-Endian):
 * <ul>
 *   <li>[0..3] Magic bytes (0x50325031)</li>
 *   <li>[4] Protocol version</li>
 *   <li>[5] Message type code</li>
 *   <li>[6..7] Flags (short)</li>
 *   <li>[8..15] Sequence number (long)</li>
 *   <li>[16..19] Payload length (int)</li>
 *   <li>[20..23] Header CRC32 (int, covering bytes 0..19)</li>
 * </ul>
 * Followed immediately by payload bytes.
 */
public final class FrameEncoder {

    private FrameEncoder() {}

    /**
     * Encodes a frame into a new byte array.
     *
     * @param frame frame to encode
     * @return serialized binary frame
     */
    public static byte[] encode(ProtocolFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");

        byte[] payload = frame.getPayload();
        int totalLength = ProtocolConstants.HEADER_SIZE + payload.length;

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.BIG_ENDIAN);

        // Header fields (bytes 0..19)
        buffer.putInt(frame.getMagic());
        buffer.put(frame.getVersion());
        buffer.put(frame.getMessageType().getCode());
        buffer.putShort(frame.getFlags());
        buffer.putLong(frame.getSequenceNumber());
        buffer.putInt(payload.length);

        // Compute CRC32 over bytes 0..19
        CRC32 crc = new CRC32();
        crc.update(buffer.array(), 0, 20);
        long crcValue = crc.getValue();

        // Put CRC32 into bytes 20..23
        buffer.putInt((int) (crcValue & 0xFFFFFFFFL));

        // Put payload
        if (payload.length > 0) {
            buffer.put(payload);
        }

        return buffer.array();
    }

    /**
     * Encodes a frame directly to an {@link OutputStream}.
     *
     * @param frame frame to encode
     * @param out target stream
     * @throws IOException if writing fails
     */
    public static void encodeToStream(ProtocolFrame frame, OutputStream out) throws IOException {
        Objects.requireNonNull(out, "out must not be null");
        byte[] bytes = encode(frame);
        out.write(bytes);
        out.flush();
    }
}
