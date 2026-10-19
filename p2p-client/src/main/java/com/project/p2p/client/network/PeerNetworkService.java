package com.project.p2p.client.network;

import com.project.p2p.client.crypto.ClientKeyManager;
import com.project.p2p.client.crypto.TrustStore;
import com.project.p2p.common.network.PeerClient;
import com.project.p2p.common.network.PeerConnection;
import com.project.p2p.common.network.PeerServer;

import java.io.IOException;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * High-level peer networking service managing inbound and outbound encrypted sessions,
 * routing chat messages, arbitrating simultaneous connections, and coordinating peer identity.
 */
public class PeerNetworkService implements AutoCloseable {

    public interface NetworkEventListener {
        default void onSessionStateChanged(EncryptedPeerSession session, SessionState oldState, SessionState newState) {}
        default void onSessionEstablished(EncryptedPeerSession session) {}
        default void onMessageReceived(EncryptedPeerSession session, UUID messageId, String text, long timestamp) {}
        default void onDeliveryAckReceived(EncryptedPeerSession session, UUID messageId) {}
        default void onReadAckReceived(EncryptedPeerSession session, UUID messageId) {}
        default void onSessionClosed(EncryptedPeerSession session, Throwable cause) {}
    }

    private final String localUsername;
    private final ClientKeyManager keyManager;
    private final TrustStore trustStore;
    private final int listeningPort;

    private PeerServer peerServer;
    private final Map<String, EncryptedPeerSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<EncryptedPeerSession>> inFlightConnections = new ConcurrentHashMap<>();
    private final Map<String, PublicKey> peerPublicKeyCache = new ConcurrentHashMap<>();
    private final List<NetworkEventListener> eventListeners = new CopyOnWriteArrayList<>();

    private Consumer<EncryptedPeerSession> onInboundSessionConnected;

    public PeerNetworkService(String localUsername, ClientKeyManager keyManager, TrustStore trustStore, int listeningPort) {
        this.localUsername = Objects.requireNonNull(localUsername, "localUsername must not be null");
        this.keyManager = Objects.requireNonNull(keyManager, "keyManager must not be null");
        this.trustStore = Objects.requireNonNull(trustStore, "trustStore must not be null");
        this.listeningPort = listeningPort;
    }

    @FunctionalInterface
    public interface PeerKeyResolver {
        PublicKey resolvePeerPublicKey(String username) throws Exception;
    }

    private volatile PeerKeyResolver peerKeyResolver;

    public void setPeerKeyResolver(PeerKeyResolver resolver) {
        this.peerKeyResolver = resolver;
    }

    public void setOnInboundSessionConnected(Consumer<EncryptedPeerSession> callback) {
        this.onInboundSessionConnected = callback;
    }

    public void addEventListener(NetworkEventListener listener) {
        if (listener != null && !eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

    public void removeEventListener(NetworkEventListener listener) {
        eventListeners.remove(listener);
    }

    public void registerPeerPublicKey(String username, PublicKey key) {
        if (username != null && key != null) {
            peerPublicKeyCache.put(username.toLowerCase(), key);
        }
    }

    public PublicKey getCachedPeerPublicKey(String username) {
        return (username != null) ? peerPublicKeyCache.get(username.toLowerCase()) : null;
    }

    /**
     * Deterministic arbitration rule:
     * When two peers simultaneously attempt outbound connections to each other,
     * the peer with lower lexicographical username is the designated INITIATOR.
     * The designated INITIATOR maintains its outbound connection and rejects incoming connections.
     * The designated RESPONDER yields/cancels its outbound connection and accepts the incoming connection.
     */
    public static boolean isDesignatedInitiator(String localUser, String peerUser) {
        int cmp = localUser.compareToIgnoreCase(peerUser);
        if (cmp != 0) {
            return cmp < 0;
        }
        return localUser.compareTo(peerUser) < 0;
    }

    /**
     * Arbitrates an incoming connection request from remoteUsername.
     *
     * @return true to accept the inbound connection; false to reject it
     */
    public synchronized boolean shouldAcceptInboundConnection(EncryptedPeerSession inboundSession, String remoteUsername) {
        String key = remoteUsername.toLowerCase();
        EncryptedPeerSession existing = activeSessions.get(key);

        // 1. If an established, active connection already exists, reject duplicate incoming connection
        if (existing != null && existing.isSecureEstablished() && existing.getConnection().isConnected()) {
            System.out.printf("[Arbitration] Rejecting redundant inbound connection from %s - existing session %s is already ESTABLISHED%n",
                    remoteUsername, existing.getConnection().getId());
            return false;
        }

        // 2. Collision detection: an outbound connection attempt to this peer is currently in progress
        if (existing != null && (existing.getState() == SessionState.CONNECTING || existing.getState() == SessionState.HANDSHAKING)) {
            boolean localIsInitiator = isDesignatedInitiator(localUsername, remoteUsername);
            if (localIsInitiator) {
                // Local node is designated initiator: keep outbound connection, reject incoming connection
                System.out.printf("[Arbitration] Collision with %s: local (%s) is designated INITIATOR. Rejecting inbound; keeping outbound.%n",
                        remoteUsername, localUsername);
                return false;
            } else {
                // Local node is designated responder: yield outbound connection, accept incoming connection
                System.out.printf("[Arbitration] Collision with %s: local (%s) is designated RESPONDER. Yielding outbound; accepting inbound.%n",
                        remoteUsername, localUsername);
                // Cancel existing outbound attempt and mark as yielded so inFlight future awaits incoming session
                existing.markYieldedByArbitration();
                existing.close();
                activeSessions.remove(key, existing);
                return true;
            }
        }

        // 3. Associate cached or dynamically resolved public key
        PublicKey cachedKey = peerPublicKeyCache.get(key);
        if (cachedKey == null && peerKeyResolver != null) {
            try {
                cachedKey = peerKeyResolver.resolvePeerPublicKey(key);
                if (cachedKey != null) {
                    peerPublicKeyCache.put(key, cachedKey);
                }
            } catch (Exception e) {
                System.err.println("[PeerNetworkService] Error resolving public key for " + key + ": " + e.getMessage());
            }
        }
        if (cachedKey != null) {
            boolean trusted = inboundSession.verifyPeerIdentity(remoteUsername, cachedKey);
            if (!trusted) {
                System.out.printf("[Security] Inbound connection rejected: identity key for %s changed (TOFU violation)%n", remoteUsername);
                return false;
            }
        }

        return true;
    }

    /**
     * Starts listening for inbound peer connections on the configured port.
     */
    public synchronized void startServer() throws IOException {
        if (peerServer != null) {
            return;
        }

        peerServer = new PeerServer("0.0.0.0", listeningPort, (server, conn) -> {
            EncryptedPeerSession session = new EncryptedPeerSession(
                    conn,
                    localUsername,
                    keyManager,
                    trustStore,
                    false, // isInitiator = false
                    null,
                    null
            );

            session.setArbitrator(this::shouldAcceptInboundConnection);
            attachSessionEventForwarder(session);

            try {
                session.start();
            } catch (IOException e) {
                System.err.println("Failed to start inbound session: " + e.getMessage());
            }
        });

        peerServer.start();
    }

    /**
     * Connects to a remote peer and performs mutual handshake.
     * Deduplicates in-flight connection requests.
     */
    public synchronized CompletableFuture<EncryptedPeerSession> connectToPeerAsync(
            String peerUsername,
            String host,
            int port,
            PublicKey peerIdentityPublicKey,
            EncryptedPeerSession.SessionListener specificListener) {

        String key = peerUsername.toLowerCase();
        if (peerIdentityPublicKey != null) {
            peerPublicKeyCache.put(key, peerIdentityPublicKey);
        }

        // Return existing active session if already connected and established
        EncryptedPeerSession existing = activeSessions.get(key);
        if (existing != null && existing.isSecureEstablished() && existing.getConnection().isConnected()) {
            if (specificListener != null) {
                existing.addListener(specificListener);
            }
            return CompletableFuture.completedFuture(existing);
        }

        // Return existing in-flight future if connection is already being negotiated
        CompletableFuture<EncryptedPeerSession> inFlight = inFlightConnections.get(key);
        if (inFlight != null && !inFlight.isDone()) {
            if (specificListener != null && existing != null) {
                existing.addListener(specificListener);
            }
            return inFlight;
        }

        CompletableFuture<EncryptedPeerSession> future = new CompletableFuture<>();
        inFlightConnections.put(key, future);

        new Thread(() -> {
            try {
                PeerConnection rawConn = PeerClient.connect(host, port, new com.project.p2p.common.network.PeerConnectionListener() {
                    @Override
                    public void onFrameReceived(PeerConnection conn, com.project.p2p.common.protocol.ProtocolFrame frame) {}

                    @Override
                    public void onDisconnected(PeerConnection conn, Throwable cause) {}
                });

                EncryptedPeerSession session = new EncryptedPeerSession(
                        rawConn,
                        localUsername,
                        keyManager,
                        trustStore,
                        true, // isInitiator = true
                        peerUsername,
                        specificListener
                );

                if (peerIdentityPublicKey != null) {
                    boolean trusted = session.verifyPeerIdentity(peerUsername, peerIdentityPublicKey);
                    if (!trusted) {
                        session.close();
                        future.completeExceptionally(new SecurityException("Peer " + peerUsername + " identity key has changed (TOFU violation)"));
                        return;
                    }
                }

                activeSessions.put(key, session);
                attachSessionEventForwarder(session);

                session.start();

                session.getHandshakeFuture().whenComplete((res, ex) -> {
                    if (ex != null) {
                        if (!session.isYieldedByArbitration() && !future.isDone()) {
                            activeSessions.remove(key, session);
                            inFlightConnections.remove(key, future);
                            future.completeExceptionally(ex);
                        }
                    } else {
                        activeSessions.put(key, session);
                        inFlightConnections.remove(key, future);
                        future.complete(session);
                    }
                });

            } catch (Exception e) {
                if (!future.isDone()) {
                    inFlightConnections.remove(key, future);
                    future.completeExceptionally(e);
                }
            }
        }, "Peer-Connector-" + peerUsername).start();

        return future;
    }

    private void attachSessionEventForwarder(EncryptedPeerSession session) {
        session.addListener(new EncryptedPeerSession.SessionListener() {
            @Override
            public void onStateChanged(EncryptedPeerSession sess, SessionState oldState, SessionState newState) {
                for (NetworkEventListener listener : eventListeners) {
                    try {
                        listener.onSessionStateChanged(sess, oldState, newState);
                    } catch (Exception e) {
                        System.err.println("Error in onSessionStateChanged listener: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onHandshakeCompleted(EncryptedPeerSession sess, String peerUser, String fingerprint) {
                String key = peerUser.toLowerCase();
                activeSessions.put(key, sess);

                // Complete any pending in-flight connection future for this peer
                CompletableFuture<EncryptedPeerSession> inFlight = inFlightConnections.remove(key);
                if (inFlight != null && !inFlight.isDone()) {
                    inFlight.complete(sess);
                }

                for (NetworkEventListener listener : eventListeners) {
                    try {
                        listener.onSessionEstablished(sess);
                    } catch (Exception e) {
                        System.err.println("Error in onSessionEstablished listener: " + e.getMessage());
                    }
                }

                if (onInboundSessionConnected != null && !sess.isInitiator()) {
                    try {
                        onInboundSessionConnected.accept(sess);
                    } catch (Exception e) {
                        System.err.println("Error in onInboundSessionConnected: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onHandshakeFailed(EncryptedPeerSession sess, String reason) {
                if (sess.isYieldedByArbitration()) {
                    return;
                }
                String peer = sess.getPeerUsername();
                if (peer != null) {
                    String key = peer.toLowerCase();
                    activeSessions.remove(key, sess);
                    if (sess.isInitiator()) {
                        CompletableFuture<EncryptedPeerSession> inFlight = inFlightConnections.remove(key);
                        if (inFlight != null && !inFlight.isDone()) {
                            inFlight.completeExceptionally(new IOException("Handshake failed: " + reason));
                        }
                    }
                }
            }

            @Override
            public void onMessageReceived(EncryptedPeerSession sess, UUID messageId, String text, long timestamp) {
                for (NetworkEventListener listener : eventListeners) {
                    try {
                        listener.onMessageReceived(sess, messageId, text, timestamp);
                    } catch (Exception e) {
                        System.err.println("Error in onMessageReceived listener: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onDeliveryAckReceived(EncryptedPeerSession sess, UUID messageId) {
                for (NetworkEventListener listener : eventListeners) {
                    try {
                        listener.onDeliveryAckReceived(sess, messageId);
                    } catch (Exception e) {
                        System.err.println("Error in onDeliveryAckReceived listener: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onReadAckReceived(EncryptedPeerSession sess, UUID messageId) {
                for (NetworkEventListener listener : eventListeners) {
                    try {
                        listener.onReadAckReceived(sess, messageId);
                    } catch (Exception e) {
                        System.err.println("Error in onReadAckReceived listener: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onDisconnected(EncryptedPeerSession sess, Throwable cause) {
                if (sess.isYieldedByArbitration()) {
                    return;
                }
                String peer = sess.getPeerUsername();
                if (peer != null) {
                    String key = peer.toLowerCase();
                    activeSessions.remove(key, sess);
                    if (sess.isInitiator()) {
                        CompletableFuture<EncryptedPeerSession> inFlight = inFlightConnections.remove(key);
                        if (inFlight != null && !inFlight.isDone()) {
                            inFlight.completeExceptionally(cause != null ? cause : new IOException("Disconnected"));
                        }
                    }
                }

                for (NetworkEventListener listener : eventListeners) {
                    try {
                        listener.onSessionClosed(sess, cause);
                    } catch (Exception e) {
                        System.err.println("Error in onSessionClosed listener: " + e.getMessage());
                    }
                }
            }
        });
    }

    public EncryptedPeerSession getSession(String username) {
        return (username != null) ? activeSessions.get(username.toLowerCase()) : null;
    }

    public int getListeningPort() {
        return (peerServer != null) ? peerServer.getPort() : listeningPort;
    }

    @Override
    public synchronized void close() {
        for (EncryptedPeerSession session : activeSessions.values()) {
            session.close();
        }
        activeSessions.clear();
        inFlightConnections.clear();
        eventListeners.clear();

        if (peerServer != null) {
            peerServer.close();
            peerServer = null;
        }
    }
}
