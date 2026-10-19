package com.project.p2p.common.crypto;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Binary payload exchanged during HANDSHAKE_INIT and HANDSHAKE_RESPONSE.
 * Enforces canonical username normalization (trimmed lowercase UTF-8)
 * to guarantee cryptographic transcript reproducibility across all platforms.
 */
public final class HandshakePayload {

    private final String senderUsername;
    private final String recipientUsername;
    private final String ephemeralPublicKeyBase64;
    private final long timestamp;
    private final byte[] signature;

    public HandshakePayload(String senderUsername, String recipientUsername, String ephemeralPublicKeyBase64, long timestamp, byte[] signature) {
        this.senderUsername = Objects.requireNonNull(senderUsername, "senderUsername must not be null").trim().toLowerCase();
        this.recipientUsername = Objects.requireNonNull(recipientUsername, "recipientUsername must not be null").trim().toLowerCase();
        this.ephemeralPublicKeyBase64 = Objects.requireNonNull(ephemeralPublicKeyBase64, "ephemeralPublicKeyBase64 must not be null").trim();
        this.timestamp = timestamp;
        this.signature = (signature != null) ? Arrays.copyOf(signature, signature.length) : new byte[0];
    }

    /**
     * Computes canonical transcript bytes for HANDSHAKE_INIT signature computation and verification.
     * Guaranteed identical across initiator and responder regardless of display-name casing.
     */
    public static byte[] computeInitTranscript(String sender, String recipient, String ephemeralKey, long timestamp) {
        String s = Objects.requireNonNull(sender, "sender must not be null").trim().toLowerCase();
        String r = Objects.requireNonNull(recipient, "recipient must not be null").trim().toLowerCase();
        String k = Objects.requireNonNull(ephemeralKey, "ephemeralKey must not be null").trim();
        String transcript = "HANDSHAKE_INIT:" + s + ":" + r + ":" + k + ":" + timestamp;
        return transcript.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Computes canonical transcript bytes for HANDSHAKE_RESPONSE signature computation and verification.
     * Guaranteed identical across responder and initiator regardless of display-name casing.
     */
    public static byte[] computeResponseTranscript(String sender, String recipient, String responderEphemeralKey, String peerEphemeralKey, long timestamp) {
        String s = Objects.requireNonNull(sender, "sender must not be null").trim().toLowerCase();
        String r = Objects.requireNonNull(recipient, "recipient must not be null").trim().toLowerCase();
        String rk = Objects.requireNonNull(responderEphemeralKey, "responderEphemeralKey must not be null").trim();
        String pk = Objects.requireNonNull(peerEphemeralKey, "peerEphemeralKey must not be null").trim();
        String transcript = "HANDSHAKE_RESP:" + s + ":" + r + ":" + rk + ":" + pk + ":" + timestamp;
        return transcript.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] toBytes() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeUTF(senderUsername);
            dos.writeUTF(recipientUsername);
            dos.writeUTF(ephemeralPublicKeyBase64);
            dos.writeLong(timestamp);
            dos.writeInt(signature.length);
            if (signature.length > 0) {
                dos.write(signature);
            }
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static HandshakePayload fromBytes(byte[] bytes) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
        String sender = dis.readUTF();
        String recipient = dis.readUTF();
        String ephemeralKey = dis.readUTF();
        long timestamp = dis.readLong();
        int sigLen = dis.readInt();
        if (sigLen < 0 || sigLen > 256) {
            throw new IOException("Invalid signature length in HandshakePayload: " + sigLen);
        }
        byte[] sig = new byte[sigLen];
        dis.readFully(sig);

        return new HandshakePayload(sender, recipient, ephemeralKey, timestamp, sig);
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public String getRecipientUsername() {
        return recipientUsername;
    }

    public String getEphemeralPublicKeyBase64() {
        return ephemeralPublicKeyBase64;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public byte[] getSignature() {
        return Arrays.copyOf(signature, signature.length);
    }
}
