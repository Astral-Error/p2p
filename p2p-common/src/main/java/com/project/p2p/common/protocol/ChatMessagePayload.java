package com.project.p2p.common.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Structured payload for {@link MessageType#CHAT_MESSAGE} frames.
 */
public final class ChatMessagePayload {

    private final UUID messageId;
    private final String senderId;
    private final String recipientId;
    private final long timestamp;
    private final byte[] content;

    public ChatMessagePayload(UUID messageId, String senderId, String recipientId, long timestamp, byte[] content) {
        this.messageId = Objects.requireNonNull(messageId, "messageId must not be null");
        this.senderId = Objects.requireNonNull(senderId, "senderId must not be null");
        this.recipientId = Objects.requireNonNull(recipientId, "recipientId must not be null");
        this.timestamp = timestamp;
        this.content = (content != null) ? Arrays.copyOf(content, content.length) : new byte[0];
    }

    public static ChatMessagePayload ofText(UUID messageId, String senderId, String recipientId, String text) {
        byte[] bytes = (text != null) ? text.getBytes(StandardCharsets.UTF_8) : new byte[0];
        return new ChatMessagePayload(messageId, senderId, recipientId, System.currentTimeMillis(), bytes);
    }

    public byte[] toBytes() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeLong(messageId.getMostSignificantBits());
            dos.writeLong(messageId.getLeastSignificantBits());
            dos.writeUTF(senderId);
            dos.writeUTF(recipientId);
            dos.writeLong(timestamp);
            dos.writeInt(content.length);
            if (content.length > 0) {
                dos.write(content);
            }
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize ChatMessagePayload", e);
        }
    }

    public static ChatMessagePayload fromBytes(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes must not be null");
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));

        long mostSig = dis.readLong();
        long leastSig = dis.readLong();
        UUID messageId = new UUID(mostSig, leastSig);

        String sender = dis.readUTF();
        String recipient = dis.readUTF();
        long timestamp = dis.readLong();

        int contentLen = dis.readInt();
        if (contentLen < 0 || contentLen > bytes.length) {
            throw new IOException("Invalid content length in ChatMessagePayload: " + contentLen);
        }
        byte[] content = new byte[contentLen];
        dis.readFully(content);

        return new ChatMessagePayload(messageId, sender, recipient, timestamp, content);
    }

    public UUID getMessageId() {
        return messageId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public byte[] getContent() {
        return Arrays.copyOf(content, content.length);
    }

    public String getContentAsString() {
        return new String(content, StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatMessagePayload that = (ChatMessagePayload) o;
        return timestamp == that.timestamp &&
                Objects.equals(messageId, that.messageId) &&
                Objects.equals(senderId, that.senderId) &&
                Objects.equals(recipientId, that.recipientId) &&
                Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(messageId, senderId, recipientId, timestamp);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }
}
