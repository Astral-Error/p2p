package com.project.p2p.common.protocol;

import com.project.p2p.common.protocol.exceptions.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class FrameCodecTest {

    @Test
    void testValidFrameEncodeDecode() throws Exception {
        byte[] payload = "Hello Secure Peer!".getBytes(StandardCharsets.UTF_8);
        ProtocolFrame original = ProtocolFrame.of(MessageType.CHAT_MESSAGE, 42L, (short) 1, payload);

        byte[] encoded = FrameEncoder.encode(original);
        assertEquals(ProtocolConstants.HEADER_SIZE + payload.length, encoded.length);

        FrameDecoder decoder = new FrameDecoder();
        List<ProtocolFrame> decodedList = decoder.feed(encoded);

        assertEquals(1, decodedList.size());
        ProtocolFrame decoded = decodedList.get(0);

        assertEquals(ProtocolConstants.MAGIC_BYTES, decoded.getMagic());
        assertEquals(ProtocolConstants.CURRENT_VERSION, decoded.getVersion());
        assertEquals(MessageType.CHAT_MESSAGE, decoded.getMessageType());
        assertEquals((short) 1, decoded.getFlags());
        assertEquals(42L, decoded.getSequenceNumber());
        assertArrayEquals(payload, decoded.getPayload());
    }

    @Test
    void testEmptyPayloadFrame() throws Exception {
        ProtocolFrame ping = ProtocolFrame.ping(100L);
        byte[] encoded = FrameEncoder.encode(ping);

        assertEquals(ProtocolConstants.HEADER_SIZE, encoded.length);

        FrameDecoder decoder = new FrameDecoder();
        List<ProtocolFrame> decodedList = decoder.feed(encoded);

        assertEquals(1, decodedList.size());
        ProtocolFrame decoded = decodedList.get(0);
        assertEquals(MessageType.PING, decoded.getMessageType());
        assertEquals(0, decoded.getPayloadLength());
        assertEquals(100L, decoded.getSequenceNumber());
    }

    @Test
    void testMultipleFramesInSingleRead() throws Exception {
        ProtocolFrame frame1 = ProtocolFrame.of(MessageType.PING, 1L, new byte[0]);
        ProtocolFrame frame2 = ProtocolFrame.of(MessageType.PONG, 2L, "pong-data".getBytes(StandardCharsets.UTF_8));
        ProtocolFrame frame3 = ProtocolFrame.of(MessageType.DISCONNECT, 3L, "bye".getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(FrameEncoder.encode(frame1));
        baos.write(FrameEncoder.encode(frame2));
        baos.write(FrameEncoder.encode(frame3));

        FrameDecoder decoder = new FrameDecoder();
        List<ProtocolFrame> decodedList = decoder.feed(baos.toByteArray());

        assertEquals(3, decodedList.size());
        assertEquals(MessageType.PING, decodedList.get(0).getMessageType());
        assertEquals(MessageType.PONG, decodedList.get(1).getMessageType());
        assertEquals(MessageType.DISCONNECT, decodedList.get(2).getMessageType());
        assertEquals(0, decoder.getBufferedBytesCount());
    }

    @Test
    void testFragmentedHeaderAcrossReads() throws Exception {
        ProtocolFrame frame = ProtocolFrame.of(MessageType.CHAT_MESSAGE, 5L, "Fragmented Header Test".getBytes(StandardCharsets.UTF_8));
        byte[] fullBytes = FrameEncoder.encode(frame);

        FrameDecoder decoder = new FrameDecoder();

        // Feed first 10 bytes of header (incomplete)
        List<ProtocolFrame> result1 = decoder.feed(fullBytes, 0, 10);
        assertTrue(result1.isEmpty(), "Should not produce frame from partial header");
        assertEquals(10, decoder.getBufferedBytesCount());

        // Feed remaining bytes
        List<ProtocolFrame> result2 = decoder.feed(fullBytes, 10, fullBytes.length - 10);
        assertEquals(1, result2.size());
        assertEquals(MessageType.CHAT_MESSAGE, result2.get(0).getMessageType());
        assertEquals(0, decoder.getBufferedBytesCount());
    }

    @Test
    void testFragmentedPayloadAcrossReads() throws Exception {
        byte[] payload = "Large chunk of payload that arrives over several TCP packets".getBytes(StandardCharsets.UTF_8);
        ProtocolFrame frame = ProtocolFrame.of(MessageType.CHAT_MESSAGE, 99L, payload);
        byte[] fullBytes = FrameEncoder.encode(frame);

        FrameDecoder decoder = new FrameDecoder();

        // Split into chunks of 7 bytes
        int chunkSize = 7;
        int offset = 0;
        List<ProtocolFrame> result = List.of();

        while (offset < fullBytes.length) {
            int len = Math.min(chunkSize, fullBytes.length - offset);
            result = decoder.feed(fullBytes, offset, len);
            offset += len;
            if (offset < fullBytes.length) {
                assertTrue(result.isEmpty(), "Intermediate fragments must not decode prematurely");
            }
        }

        assertEquals(1, result.size());
        assertEquals(frame, result.get(0));
        assertEquals(0, decoder.getBufferedBytesCount());
    }

    @Test
    void testByteByByteStreaming() throws Exception {
        byte[] payload = "Byte by byte stream accumulation".getBytes(StandardCharsets.UTF_8);
        ProtocolFrame frame = ProtocolFrame.of(MessageType.ACK_DELIVERED, 777L, payload);
        byte[] fullBytes = FrameEncoder.encode(frame);

        FrameDecoder decoder = new FrameDecoder();
        ProtocolFrame decoded = null;

        for (int i = 0; i < fullBytes.length; i++) {
            List<ProtocolFrame> frames = decoder.feed(new byte[]{fullBytes[i]});
            if (i < fullBytes.length - 1) {
                assertTrue(frames.isEmpty());
            } else {
                assertEquals(1, frames.size());
                decoded = frames.get(0);
            }
        }

        assertNotNull(decoded);
        assertEquals(frame, decoded);
    }

    @Test
    void testInvalidMagicBytesRejected() {
        byte[] raw = new byte[ProtocolConstants.HEADER_SIZE];
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(0xDEADBEEF); // Bad magic
        buf.put((byte) 1);      // version
        buf.put((byte) 1);      // type

        FrameDecoder decoder = new FrameDecoder();
        assertThrows(InvalidMagicException.class, () -> decoder.feed(raw));
    }

    @Test
    void testUnsupportedVersionRejected() {
        byte[] raw = new byte[ProtocolConstants.HEADER_SIZE];
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(ProtocolConstants.MAGIC_BYTES);
        buf.put((byte) 0x99); // Unsupported version 99
        buf.put((byte) 1);

        FrameDecoder decoder = new FrameDecoder();
        assertThrows(UnsupportedVersionException.class, () -> decoder.feed(raw));
    }

    @Test
    void testInvalidMessageTypeRejected() {
        byte[] raw = new byte[ProtocolConstants.HEADER_SIZE];
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(ProtocolConstants.MAGIC_BYTES);
        buf.put(ProtocolConstants.CURRENT_VERSION);
        buf.put((byte) 0x7F); // Invalid message type

        FrameDecoder decoder = new FrameDecoder();
        assertThrows(InvalidMessageTypeException.class, () -> decoder.feed(raw));
    }

    @Test
    void testCorruptHeaderCrc32Rejected() {
        ProtocolFrame frame = ProtocolFrame.ping(1L);
        byte[] encoded = FrameEncoder.encode(frame);

        // Corrupt sequence number byte in header
        encoded[10] ^= (byte) 0xFF;

        FrameDecoder decoder = new FrameDecoder();
        assertThrows(CorruptFrameException.class, () -> decoder.feed(encoded));
    }

    @Test
    void testOversizedPayloadRejected() {
        byte[] header = new byte[ProtocolConstants.HEADER_SIZE];
        ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(ProtocolConstants.MAGIC_BYTES);
        buf.put(ProtocolConstants.CURRENT_VERSION);
        buf.put(MessageType.CHAT_MESSAGE.getCode());
        buf.putShort((short) 0);
        buf.putLong(1L);
        buf.putInt(1000); // 1000 bytes payload

        // Valid CRC32
        CRC32 crc = new CRC32();
        crc.update(header, 0, 20);
        buf.putInt((int) crc.getValue());

        // Decoder configured with max 500 bytes payload
        FrameDecoder decoder = new FrameDecoder(500, 2048);
        assertThrows(OversizedPayloadException.class, () -> decoder.feed(header));
    }

    @Test
    void testNegativePayloadLengthRejected() {
        byte[] header = new byte[ProtocolConstants.HEADER_SIZE];
        ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(ProtocolConstants.MAGIC_BYTES);
        buf.put(ProtocolConstants.CURRENT_VERSION);
        buf.put(MessageType.CHAT_MESSAGE.getCode());
        buf.putShort((short) 0);
        buf.putLong(1L);
        buf.putInt(-5); // Negative payload length

        CRC32 crc = new CRC32();
        crc.update(header, 0, 20);
        buf.putInt((int) crc.getValue());

        FrameDecoder decoder = new FrameDecoder();
        assertThrows(OversizedPayloadException.class, () -> decoder.feed(header));
    }

    @Test
    void testFrameStreamReaderTruncatedStreamThrows() {
        ProtocolFrame frame = ProtocolFrame.of(MessageType.CHAT_MESSAGE, 1L, "Some text".getBytes(StandardCharsets.UTF_8));
        byte[] encoded = FrameEncoder.encode(frame);

        // Truncate the stream halfway through the payload
        byte[] truncated = new byte[ProtocolConstants.HEADER_SIZE + 3];
        System.arraycopy(encoded, 0, truncated, 0, truncated.length);

        FrameStreamReader reader = new FrameStreamReader(new ByteArrayInputStream(truncated));
        assertThrows(TruncatedFrameException.class, reader::readFrame);
    }

    @Test
    void testFrameStreamReaderCleanEof() throws Exception {
        byte[] empty = new byte[0];
        FrameStreamReader reader = new FrameStreamReader(new ByteArrayInputStream(empty));
        assertNull(reader.readFrame(), "Empty stream at frame boundary must return null");
    }

    @Test
    void testChatMessagePayloadSerialization() throws Exception {
        UUID id = UUID.randomUUID();
        ChatMessagePayload original = ChatMessagePayload.ofText(id, "alice", "bob", "Hello Bob!");

        byte[] serialized = original.toBytes();
        ChatMessagePayload restored = ChatMessagePayload.fromBytes(serialized);

        assertEquals(original.getMessageId(), restored.getMessageId());
        assertEquals(original.getSenderId(), restored.getSenderId());
        assertEquals(original.getRecipientId(), restored.getRecipientId());
        assertEquals(original.getContentAsString(), restored.getContentAsString());
    }

    @Test
    void testAckPayloadSerialization() throws Exception {
        UUID id = UUID.randomUUID();
        AckPayload original = AckPayload.of(id, 456L);

        byte[] serialized = original.toBytes();
        AckPayload restored = AckPayload.fromBytes(serialized);

        assertEquals(original.getMessageId(), restored.getMessageId());
        assertEquals(original.getAckSequenceNumber(), restored.getAckSequenceNumber());
        assertEquals(original.getTimestamp(), restored.getTimestamp());
    }
}
