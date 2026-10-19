package com.project.p2p.client.network;

import com.project.p2p.client.crypto.ClientKeyManager;
import com.project.p2p.client.crypto.TrustStore;
import com.project.p2p.common.crypto.*;
import com.project.p2p.common.network.MessageTracker;
import com.project.p2p.common.network.PeerConnection;
import com.project.p2p.common.network.PeerConnectionListener;
import com.project.p2p.common.protocol.AckPayload;
import com.project.p2p.common.protocol.MessageType;
import com.project.p2p.common.protocol.ProtocolFrame;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * High-level secure P2P session handling cryptographic handshake,
 * end-to-end encrypted messaging, TOFU peer verification, and delivery acknowledgements.
 * Enforces explicit lifecycle states: CONNECTING, HANDSHAKING, ESTABLISHED, FAILED, CLOSED.
 */
public class EncryptedPeerSession implements PeerConnectionListener {

    public interface SessionListener {
        default void onStateChanged(EncryptedPeerSession session, SessionState oldState, SessionState newState) {}
        default void onHandshakeCompleted(EncryptedPeerSession session, String peerUsername, String peerFingerprint) {}
        default void onHandshakeFailed(EncryptedPeerSession session, String reason) {}
        default void onMessageReceived(EncryptedPeerSession session, UUID messageId, String text, long timestamp) {}
        default void onDeliveryAckReceived(EncryptedPeerSession session, UUID messageId) {}
        default void onReadAckReceived(EncryptedPeerSession session, UUID messageId) {}
        default void onDisconnected(EncryptedPeerSession session, Throwable cause) {}
    }

    @FunctionalInterface
    public interface ConnectionArbitrator {
        boolean shouldAcceptInboundConnection(EncryptedPeerSession inboundSession, String remoteUsername);
    }

    private static final long HANDSHAKE_TIMEOUT_SECONDS = 10;

    private final PeerConnection connection;
    private final String localUsername;
    private final ClientKeyManager keyManager;
    private final TrustStore trustStore;
    private final boolean isInitiator;
    private final String expectedPeerUsername; // Non-null if initiator
    private final List<SessionListener> listeners = new CopyOnWriteArrayList<>();

    private final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.CONNECTING);
    private final AtomicReference<SessionKeys> sessionKeysRef = new AtomicReference<>(null);
    private final AtomicReference<String> peerUsernameRef = new AtomicReference<>(null);
    private final AtomicReference<String> peerFingerprintRef = new AtomicReference<>(null);
    private final AtomicReference<PublicKey> peerIdentityKeyRef = new AtomicReference<>(null);

    // Ephemeral key agreement pair for this session
    private final KeyPair ephemeralKeyPair;
    private final MessageTracker messageTracker = new MessageTracker();

    // Diagnostics counters
    private final AtomicLong encryptedFramesSent = new AtomicLong(0);
    private final AtomicLong encryptedFramesReceived = new AtomicLong(0);
    private final CompletableFuture<Void> handshakeFuture = new CompletableFuture<>();
    private final java.util.concurrent.atomic.AtomicBoolean yieldedByArbitration = new java.util.concurrent.atomic.AtomicBoolean(false);
    private volatile long sentInitTimestamp;
    private volatile ConnectionArbitrator arbitrator;

    public EncryptedPeerSession(
            PeerConnection connection,
            String localUsername,
            ClientKeyManager keyManager,
            TrustStore trustStore,
            boolean isInitiator,
            String expectedPeerUsername,
            SessionListener listener) {

        this.connection = Objects.requireNonNull(connection, "connection must not be null");
        this.localUsername = Objects.requireNonNull(localUsername, "localUsername must not be null");
        this.keyManager = Objects.requireNonNull(keyManager, "keyManager must not be null");
        this.trustStore = Objects.requireNonNull(trustStore, "trustStore must not be null");
        this.isInitiator = isInitiator;
        this.expectedPeerUsername = expectedPeerUsername;

        if (listener != null) {
            this.listeners.add(listener);
        }

        // Fresh ephemeral X25519 keypair for this specific TCP session
        this.ephemeralKeyPair = CryptoPrimitives.generateX25519KeyPair();
        this.connection.addListener(this);

        // Schedule handshake timeout
        this.handshakeFuture.orTimeout(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((res, ex) -> {
                    if (ex instanceof java.util.concurrent.TimeoutException) {
                        logDiagnostic("HANDSHAKE_TIMEOUT", "Handshake timed out after " + HANDSHAKE_TIMEOUT_SECONDS + "s");
                        failHandshake("Handshake timed out after " + HANDSHAKE_TIMEOUT_SECONDS + " seconds");
                    }
                });
    }

    public void addListener(SessionListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(SessionListener listener) {
        listeners.remove(listener);
    }

    public void setArbitrator(ConnectionArbitrator arbitrator) {
        this.arbitrator = arbitrator;
    }

    /**
     * Starts the session. If this peer is initiator, sends HANDSHAKE_INIT.
     */
    public void start() throws IOException {
        connection.start();
        if (isInitiator) {
            transitionTo(SessionState.HANDSHAKING, "Initiator sending HANDSHAKE_INIT");
            sendHandshakeInit();
        } else {
            transitionTo(SessionState.HANDSHAKING, "Responder awaiting HANDSHAKE_INIT");
        }
    }

    public CompletableFuture<Void> getHandshakeFuture() {
        return handshakeFuture;
    }

    public SessionState getState() {
        return state.get();
    }

    public boolean isSecureEstablished() {
        return state.get() == SessionState.ESTABLISHED;
    }

    public boolean isInitiator() {
        return isInitiator;
    }

    public String getLocalUsername() {
        return localUsername;
    }

    public String getExpectedPeerUsername() {
        return expectedPeerUsername;
    }

    public void markYieldedByArbitration() {
        yieldedByArbitration.set(true);
    }

    public boolean isYieldedByArbitration() {
        return yieldedByArbitration.get();
    }

    private synchronized void transitionTo(SessionState newState, String reason) {
        SessionState oldState = state.get();
        if (oldState == newState) {
            return;
        }
        // Don't transition out of CLOSED or FAILED
        if (oldState == SessionState.CLOSED || oldState == SessionState.FAILED) {
            return;
        }

        state.set(newState);
        logDiagnostic("STATE_CHANGE", oldState + " -> " + newState + (reason != null ? " (" + reason + ")" : ""));
        for (SessionListener l : listeners) {
            try {
                l.onStateChanged(this, oldState, newState);
            } catch (Exception e) {
                System.err.println("Error notifying onStateChanged: " + e.getMessage());
            }
        }
    }

    /**
     * Emits structured diagnostic log line:
     * [PeerSession-{id}] local={ip:port} remote={ip:port} role={INITIATOR|RESPONDER} state={STATE} event={EVENT} [details=...]
     * Never logs secret keys or chat message plaintext.
     */
    public void logDiagnostic(String event, String details) {
        String localAddr = (connection.getLocalAddress() != null) ? connection.getLocalAddress().toString() : "unbound";
        String remoteAddr = (connection.getRemoteAddress() != null) ? connection.getRemoteAddress().toString() : "unbound";
        String role = isInitiator ? "INITIATOR" : "RESPONDER";
        String sid = (connection.getId().length() > 8) ? connection.getId().substring(0, 8) : connection.getId();
        String detailStr = (details != null && !details.isEmpty()) ? " details=" + details : "";
        System.out.printf("[PeerSession-%s] local=%s remote=%s role=%s state=%s event=%s%s%n",
                sid, localAddr, remoteAddr, role, state.get(), event, detailStr);
    }

    private void sendHandshakeInit() throws IOException {
        String ephemeralPubBase64 = CryptoPrimitives.encodePublicKey(ephemeralKeyPair.getPublic());
        long now = System.currentTimeMillis();
        this.sentInitTimestamp = now;

        byte[] transcript = HandshakePayload.computeInitTranscript(
                localUsername,
                expectedPeerUsername,
                ephemeralPubBase64,
                now
        );

        byte[] signature = CryptoPrimitives.signEd25519(keyManager.getIdentityKeyPair().getPrivate(), transcript);

        HandshakePayload initPayload = new HandshakePayload(
                localUsername,
                expectedPeerUsername,
                ephemeralPubBase64,
                now,
                signature
        );

        String myKeyFp = CryptoPrimitives.computeFingerprint(keyManager.getIdentityKeyPair().getPublic());
        String transcriptHashHex = bytesToHex(CryptoPrimitives.sha256(transcript)).substring(0, 16);
        logDiagnostic("SEND_HANDSHAKE_INIT", "Target=" + expectedPeerUsername +
                " myKeyFp=" + myKeyFp + " transcriptHash=" + transcriptHashHex);
        connection.sendFrame(MessageType.HANDSHAKE_INIT, initPayload.toBytes());
    }

    @Override
    public void onFrameReceived(PeerConnection conn, ProtocolFrame frame) {
        try {
            switch (frame.getMessageType()) {
                case HANDSHAKE_INIT -> handleHandshakeInit(frame);
                case HANDSHAKE_RESPONSE -> handleHandshakeResponse(frame);
                case CHAT_MESSAGE -> handleChatMessage(frame);
                case ACK_DELIVERED -> handleAckDelivered(frame);
                case ACK_READ -> handleAckRead(frame);
                case PING -> conn.sendFrame(MessageType.PONG, new byte[0]);
                case PONG -> {} // Keepalive response handled
                case DISCONNECT -> {
                    logDiagnostic("DISCONNECT_FRAME", "Remote peer sent DISCONNECT");
                    close();
                }
                default -> logDiagnostic("UNEXPECTED_FRAME", "MessageType=" + frame.getMessageType());
            }
        } catch (Exception e) {
            logDiagnostic("FRAME_ERROR", "MessageType=" + frame.getMessageType() + " err=" + e.getMessage());
            if (!isSecureEstablished() && !handshakeFuture.isDone()) {
                failHandshake(e.getMessage());
            }
        }
    }

    private void failHandshake(String reason) {
        transitionTo(SessionState.FAILED, reason);
        handshakeFuture.completeExceptionally(new IOException("Handshake failed: " + reason));
        for (SessionListener l : listeners) {
            try {
                l.onHandshakeFailed(this, reason);
            } catch (Exception ignored) {}
        }
        close();
    }

    private void handleHandshakeInit(ProtocolFrame frame) throws Exception {
        if (isInitiator) {
            throw new IllegalStateException("Initiator received unexpected HANDSHAKE_INIT on outbound connection");
        }

        HandshakePayload payload = HandshakePayload.fromBytes(frame.getPayload());
        if (!payload.getRecipientUsername().equalsIgnoreCase(localUsername)) {
            throw new SecurityException("Handshake recipient mismatch: " + payload.getRecipientUsername() + " != " + localUsername);
        }

        String remoteUser = payload.getSenderUsername();
        peerUsernameRef.set(remoteUser);
        logDiagnostic("RECV_HANDSHAKE_INIT", "From=" + remoteUser);

        // Check connection arbitration before proceeding
        if (arbitrator != null && !arbitrator.shouldAcceptInboundConnection(this, remoteUser)) {
            logDiagnostic("ARBITRATION_REJECT", "Inbound connection from " + remoteUser + " rejected by arbitration");
            close();
            return;
        }

        byte[] transcript = HandshakePayload.computeInitTranscript(
                payload.getSenderUsername(),
                payload.getRecipientUsername(),
                payload.getEphemeralPublicKeyBase64(),
                payload.getTimestamp()
        );

        if (peerIdentityKeyRef.get() != null) {
            String verifyFp = CryptoPrimitives.computeFingerprint(peerIdentityKeyRef.get());
            String transcriptHashHex = bytesToHex(CryptoPrimitives.sha256(transcript)).substring(0, 16);
            logDiagnostic("VERIFY_HANDSHAKE_INIT", "remoteUser=" + remoteUser + " keyFp=" + verifyFp + " transcriptHash=" + transcriptHashHex);

            if (!CryptoPrimitives.verifyEd25519(peerIdentityKeyRef.get(), transcript, payload.getSignature())) {
                throw new SecurityException("Invalid Ed25519 handshake signature from " + remoteUser +
                        " (Key FP: " + verifyFp + ", TranscriptHash: " + transcriptHashHex + ")");
            }
        }

        // Derive shared secret from peer ephemeral key
        PublicKey peerEphemeralKey = CryptoPrimitives.decodeX25519PublicKey(payload.getEphemeralPublicKeyBase64());
        byte[] sharedSecret = CryptoPrimitives.computeSharedSecret(ephemeralKeyPair.getPrivate(), peerEphemeralKey);

        // Compute session ID and transcript hash
        byte[] transcriptHash = CryptoPrimitives.sha256(transcript);
        String sessionId = UUID.randomUUID().toString();

        SessionKeys keys = SessionKeys.derive(sharedSecret, transcriptHash, false, sessionId);
        sessionKeysRef.set(keys);

        // Send HANDSHAKE_RESPONSE
        String myEphemeralPubBase64 = CryptoPrimitives.encodePublicKey(ephemeralKeyPair.getPublic());
        long now = System.currentTimeMillis();

        byte[] respTranscript = HandshakePayload.computeResponseTranscript(
                localUsername,
                remoteUser,
                myEphemeralPubBase64,
                payload.getEphemeralPublicKeyBase64(),
                now
        );

        byte[] respSig = CryptoPrimitives.signEd25519(keyManager.getIdentityKeyPair().getPrivate(), respTranscript);

        HandshakePayload respPayload = new HandshakePayload(
                localUsername,
                remoteUser,
                myEphemeralPubBase64,
                now,
                respSig
        );

        logDiagnostic("SEND_HANDSHAKE_RESP", "To=" + remoteUser);
        connection.sendFrame(MessageType.HANDSHAKE_RESPONSE, respPayload.toBytes());

        transitionTo(SessionState.ESTABLISHED, "Handshake completed with " + remoteUser);
        handshakeFuture.complete(null);

        for (SessionListener l : listeners) {
            try {
                l.onHandshakeCompleted(this, remoteUser, peerFingerprintRef.get());
            } catch (Exception e) {
                System.err.println("Error in onHandshakeCompleted listener: " + e.getMessage());
            }
        }
    }

    private void handleHandshakeResponse(ProtocolFrame frame) throws Exception {
        if (!isInitiator) {
            throw new IllegalStateException("Responder received unexpected HANDSHAKE_RESPONSE");
        }

        HandshakePayload payload = HandshakePayload.fromBytes(frame.getPayload());
        String remoteUser = payload.getSenderUsername();
        if (!remoteUser.equalsIgnoreCase(expectedPeerUsername)) {
            throw new SecurityException("Handshake responder mismatch: expected " + expectedPeerUsername + ", got " + remoteUser);
        }
        peerUsernameRef.set(remoteUser);
        logDiagnostic("RECV_HANDSHAKE_RESP", "From=" + remoteUser);

        String myEphemeralPubBase64 = CryptoPrimitives.encodePublicKey(ephemeralKeyPair.getPublic());
        byte[] respTranscript = HandshakePayload.computeResponseTranscript(
                remoteUser,
                localUsername,
                payload.getEphemeralPublicKeyBase64(),
                myEphemeralPubBase64,
                payload.getTimestamp()
        );

        if (peerIdentityKeyRef.get() != null) {
            String verifyFp = CryptoPrimitives.computeFingerprint(peerIdentityKeyRef.get());
            String respTranscriptHashHex = bytesToHex(CryptoPrimitives.sha256(respTranscript)).substring(0, 16);
            logDiagnostic("VERIFY_HANDSHAKE_RESP", "remoteUser=" + remoteUser + " keyFp=" + verifyFp + " transcriptHash=" + respTranscriptHashHex);

            if (!CryptoPrimitives.verifyEd25519(peerIdentityKeyRef.get(), respTranscript, payload.getSignature())) {
                throw new SecurityException("Invalid Ed25519 handshake signature from " + remoteUser +
                        " (Key FP: " + verifyFp + ", TranscriptHash: " + respTranscriptHashHex + ")");
            }
        }

        PublicKey peerEphemeralKey = CryptoPrimitives.decodeX25519PublicKey(payload.getEphemeralPublicKeyBase64());
        byte[] sharedSecret = CryptoPrimitives.computeSharedSecret(ephemeralKeyPair.getPrivate(), peerEphemeralKey);

        byte[] initTranscript = HandshakePayload.computeInitTranscript(
                localUsername,
                expectedPeerUsername,
                myEphemeralPubBase64,
                sentInitTimestamp
        );
        byte[] transcriptHash = CryptoPrimitives.sha256(initTranscript);
        String sessionId = UUID.randomUUID().toString();

        SessionKeys keys = SessionKeys.derive(sharedSecret, transcriptHash, true, sessionId);
        sessionKeysRef.set(keys);

        transitionTo(SessionState.ESTABLISHED, "Handshake completed with " + remoteUser);
        handshakeFuture.complete(null);

        for (SessionListener l : listeners) {
            try {
                l.onHandshakeCompleted(this, remoteUser, peerFingerprintRef.get());
            } catch (Exception e) {
                System.err.println("Error in onHandshakeCompleted listener: " + e.getMessage());
            }
        }
    }

    /**
     * Associates peer's verified identity public key and checks TOFU trust store.
     */
    public boolean verifyPeerIdentity(String peerUsername, PublicKey peerIdentityKey) {
        Objects.requireNonNull(peerUsername, "peerUsername must not be null");
        Objects.requireNonNull(peerIdentityKey, "peerIdentityKey must not be null");

        String fingerprint = CryptoPrimitives.computeFingerprint(peerIdentityKey);
        peerIdentityKeyRef.set(peerIdentityKey);
        peerFingerprintRef.set(fingerprint);

        if (trustStore.hasKeyChanged(peerUsername, fingerprint)) {
            logDiagnostic("SECURITY_ALERT", "Peer " + peerUsername + " identity key has CHANGED! Possible MITM!");
            return false;
        }

        if (!trustStore.hasRecordedKey(peerUsername)) {
            trustStore.trustFingerprint(peerUsername, fingerprint);
        }
        return true;
    }

    /**
     * Sends an end-to-end encrypted chat message to the peer.
     * Enforces that plaintext chat messages can NEVER be transmitted.
     */
    public CompletableFuture<UUID> sendChatMessage(String text) {
        if (!isSecureEstablished() || sessionKeysRef.get() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Cannot send message: secure session not established (state=" + state.get() + ")"));
        }

        UUID messageId = UUID.randomUUID();
        long seq = connection.getNextSequenceNumber();

        try {
            SessionKeys keys = sessionKeysRef.get();
            byte[] nonce = keys.nextOutboundNonce();
            byte[] plainBytes = text.getBytes(StandardCharsets.UTF_8);

            String recipient = (peerUsernameRef.get() != null) ? peerUsernameRef.get() : expectedPeerUsername;
            byte[] aad = EncryptedPayload.computeAad(messageId, seq, localUsername, recipient);

            byte[] ciphertext = CryptoPrimitives.encryptAesGcm(keys.getOutboundKey(), nonce, plainBytes, aad);

            EncryptedPayload envelope = new EncryptedPayload(nonce, seq, messageId, ciphertext);
            messageTracker.trackOutgoing(messageId, seq);

            connection.sendFrame(MessageType.CHAT_MESSAGE, envelope.toBytes());
            messageTracker.markSent(messageId);
            encryptedFramesSent.incrementAndGet();

            return CompletableFuture.completedFuture(messageId);
        } catch (Exception e) {
            messageTracker.markFailed(messageId);
            return CompletableFuture.failedFuture(e);
        }
    }

    private void handleChatMessage(ProtocolFrame frame) throws Exception {
        if (!isSecureEstablished() || sessionKeysRef.get() == null) {
            throw new SecurityException("Rejected unencrypted or premature CHAT_MESSAGE frame: secure channel not established");
        }

        EncryptedPayload envelope = EncryptedPayload.fromBytes(frame.getPayload());
        SessionKeys keys = sessionKeysRef.get();

        // Enforce replay check
        if (!keys.checkAndRecordInboundSequence(envelope.getSequenceNumber())) {
            logDiagnostic("REPLAY_REJECTED", "Sequence=" + envelope.getSequenceNumber());
            return;
        }

        String sender = (peerUsernameRef.get() != null) ? peerUsernameRef.get() : expectedPeerUsername;
        byte[] aad = EncryptedPayload.computeAad(envelope.getMessageId(), envelope.getSequenceNumber(), sender, localUsername);

        byte[] plainBytes = CryptoPrimitives.decryptAesGcm(keys.getInboundKey(), envelope.getNonce(), envelope.getCiphertext(), aad);
        String text = new String(plainBytes, StandardCharsets.UTF_8);
        encryptedFramesReceived.incrementAndGet();

        // Send ACK_DELIVERED back to peer
        AckPayload ack = AckPayload.of(envelope.getMessageId(), envelope.getSequenceNumber());
        connection.sendFrame(MessageType.ACK_DELIVERED, ack.toBytes());

        for (SessionListener l : listeners) {
            try {
                l.onMessageReceived(this, envelope.getMessageId(), text, System.currentTimeMillis());
            } catch (Exception e) {
                System.err.println("Error in onMessageReceived listener: " + e.getMessage());
            }
        }
    }

    private void handleAckDelivered(ProtocolFrame frame) throws Exception {
        AckPayload ack = AckPayload.fromBytes(frame.getPayload());
        messageTracker.markDelivered(ack.getMessageId());
        for (SessionListener l : listeners) {
            try {
                l.onDeliveryAckReceived(this, ack.getMessageId());
            } catch (Exception e) {
                System.err.println("Error in onDeliveryAckReceived listener: " + e.getMessage());
            }
        }
    }

    private void handleAckRead(ProtocolFrame frame) throws Exception {
        AckPayload ack = AckPayload.fromBytes(frame.getPayload());
        messageTracker.markRead(ack.getMessageId());
        for (SessionListener l : listeners) {
            try {
                l.onReadAckReceived(this, ack.getMessageId());
            } catch (Exception e) {
                System.err.println("Error in onReadAckReceived listener: " + e.getMessage());
            }
        }
    }

    public void sendReadAck(UUID messageId) {
        try {
            AckPayload ack = AckPayload.of(messageId, 0);
            connection.sendFrame(MessageType.ACK_READ, ack.toBytes());
        } catch (IOException e) {
            logDiagnostic("SEND_READ_ACK_ERROR", e.getMessage());
        }
    }

    @Override
    public void onDisconnected(PeerConnection conn, Throwable cause) {
        transitionTo(SessionState.CLOSED, cause != null ? cause.getMessage() : "Peer disconnected");
        for (SessionListener l : listeners) {
            try {
                l.onDisconnected(this, cause);
            } catch (Exception ignored) {}
        }
    }

    public void close() {
        transitionTo(SessionState.CLOSED, "Explicit session close");
        try {
            if (connection.isConnected()) {
                connection.sendFrame(MessageType.DISCONNECT, new byte[0]);
            }
        } catch (Exception ignored) {}
        connection.close();
    }

    public String getPeerUsername() {
        return peerUsernameRef.get();
    }

    public String getPeerFingerprint() {
        return peerFingerprintRef.get();
    }

    public MessageTracker getMessageTracker() {
        return messageTracker;
    }

    public long getEncryptedFramesSent() {
        return encryptedFramesSent.get();
    }

    public long getEncryptedFramesReceived() {
        return encryptedFramesReceived.get();
    }

    public PeerConnection getConnection() {
        return connection;
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
