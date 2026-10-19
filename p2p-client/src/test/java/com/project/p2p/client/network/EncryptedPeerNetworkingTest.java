package com.project.p2p.client.network;

import com.project.p2p.client.crypto.ClientKeyManager;
import com.project.p2p.client.crypto.TrustStore;
import com.project.p2p.common.crypto.CryptoPrimitives;
import com.project.p2p.common.network.PeerClient;
import com.project.p2p.common.network.PeerConnection;
import com.project.p2p.common.network.PeerServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EncryptedPeerNetworkingTest {

    @TempDir
    Path tempDir;

    private ClientKeyManager aliceKeys;
    private ClientKeyManager bobKeys;
    private TrustStore aliceTrust;
    private TrustStore bobTrust;

    private PeerServer bobServer;
    private PeerConnection aliceConn;
    private PeerConnection bobConn;

    private EncryptedPeerSession aliceSession;
    private EncryptedPeerSession bobSession;

    @BeforeEach
    void setUp() throws Exception {
        aliceKeys = new ClientKeyManager(tempDir.resolve("alice_keys"));
        aliceKeys.initialize("alice", "pass-alice");

        bobKeys = new ClientKeyManager(tempDir.resolve("bob_keys"));
        bobKeys.initialize("bob", "pass-bob");

        aliceTrust = new TrustStore(tempDir.resolve("alice_trust.properties"));
        bobTrust = new TrustStore(tempDir.resolve("bob_trust.properties"));
    }

    @AfterEach
    void tearDown() {
        if (aliceSession != null) aliceSession.close();
        if (bobSession != null) bobSession.close();
        if (bobServer != null) bobServer.close();
    }

    @Test
    void testEncryptedBidirectionalMessagingAndAcks() throws Exception {
        CountDownLatch handshakeLatch = new CountDownLatch(2);
        CountDownLatch messageLatch = new CountDownLatch(1);
        CountDownLatch ackLatch = new CountDownLatch(1);

        AtomicReference<String> receivedText = new AtomicReference<>();
        AtomicReference<UUID> receivedMsgId = new AtomicReference<>();

        // Bob starts listening server
        bobServer = new PeerServer(0, (server, conn) -> {
            bobConn = conn;
            bobSession = new EncryptedPeerSession(
                    conn,
                    "bob",
                    bobKeys,
                    bobTrust,
                    false,
                    null,
                    new EncryptedPeerSession.SessionListener() {
                        @Override
                        public void onHandshakeCompleted(EncryptedPeerSession session, String peerUsername, String peerFingerprint) {
                            handshakeLatch.countDown();
                        }

                        @Override
                        public void onHandshakeFailed(EncryptedPeerSession session, String reason) {
                            fail("Bob handshake failed: " + reason);
                        }

                        @Override
                        public void onMessageReceived(EncryptedPeerSession session, UUID messageId, String text, long timestamp) {
                            receivedMsgId.set(messageId);
                            receivedText.set(text);
                            messageLatch.countDown();
                        }

                        @Override
                        public void onDeliveryAckReceived(EncryptedPeerSession session, UUID messageId) {}

                        @Override
                        public void onReadAckReceived(EncryptedPeerSession session, UUID messageId) {}

                        @Override
                        public void onDisconnected(EncryptedPeerSession session, Throwable cause) {}
                    }
            );

            try {
                bobSession.start();
            } catch (IOException e) {
                fail("Failed to start bob session: " + e.getMessage());
            }
        });
        bobServer.start();

        // Alice connects to Bob
        aliceConn = PeerClient.connect("127.0.0.1", bobServer.getPort(), new com.project.p2p.common.network.PeerConnectionListener() {
            @Override
            public void onFrameReceived(PeerConnection conn, com.project.p2p.common.protocol.ProtocolFrame frame) {}

            @Override
            public void onDisconnected(PeerConnection conn, Throwable cause) {}
        });
        aliceSession = new EncryptedPeerSession(
                aliceConn,
                "alice",
                aliceKeys,
                aliceTrust,
                true,
                "bob",
                new EncryptedPeerSession.SessionListener() {
                    @Override
                    public void onHandshakeCompleted(EncryptedPeerSession session, String peerUsername, String peerFingerprint) {
                        handshakeLatch.countDown();
                    }

                    @Override
                    public void onHandshakeFailed(EncryptedPeerSession session, String reason) {
                        fail("Alice handshake failed: " + reason);
                    }

                    @Override
                    public void onMessageReceived(EncryptedPeerSession session, UUID messageId, String text, long timestamp) {}

                    @Override
                    public void onDeliveryAckReceived(EncryptedPeerSession session, UUID messageId) {
                        ackLatch.countDown();
                    }

                    @Override
                    public void onReadAckReceived(EncryptedPeerSession session, UUID messageId) {}

                    @Override
                    public void onDisconnected(EncryptedPeerSession session, Throwable cause) {}
                }
        );
        aliceSession.start();

        // 1. Verify handshake completes successfully
        assertTrue(handshakeLatch.await(5, TimeUnit.SECONDS), "Handshake did not complete in time");
        assertTrue(aliceSession.isSecureEstablished());
        assertTrue(bobSession.isSecureEstablished());

        // 2. Alice sends an end-to-end encrypted message to Bob
        String secretMessage = "Hello Bob! This is an end-to-end encrypted message via AES-256-GCM.";
        UUID sentId = aliceSession.sendChatMessage(secretMessage).get(5, TimeUnit.SECONDS);

        // 3. Bob receives and decrypts message
        assertTrue(messageLatch.await(5, TimeUnit.SECONDS), "Bob did not receive message in time");
        assertEquals(secretMessage, receivedText.get());
        assertEquals(sentId, receivedMsgId.get());

        // 4. Bob automatically sent ACK_DELIVERED back to Alice
        assertTrue(ackLatch.await(5, TimeUnit.SECONDS), "Alice did not receive delivery ACK in time");
        assertEquals(1, aliceSession.getEncryptedFramesSent());
        assertEquals(1, bobSession.getEncryptedFramesReceived());
    }

    @Test
    void testRejectsUnencryptedMessageBeforeHandshake() throws Exception {
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0);
             java.net.Socket client = new java.net.Socket("127.0.0.1", ss.getLocalPort());
             java.net.Socket server = ss.accept()) {

            PeerConnection conn = new PeerConnection(client);
            EncryptedPeerSession session = new EncryptedPeerSession(
                    conn, "alice", aliceKeys, aliceTrust, true, "bob", null
            );

            assertThrows(Exception.class, () -> session.sendChatMessage("Premature chat").join());
        }
    }

    @Test
    void testSimultaneousOutboundConnectionArbitration() throws Exception {
        try (PeerNetworkService aliceService = new PeerNetworkService("alice", aliceKeys, aliceTrust, 0);
             PeerNetworkService bobService = new PeerNetworkService("bob", bobKeys, bobTrust, 0)) {

            aliceService.startServer();
            bobService.startServer();

            int alicePort = aliceService.getListeningPort();
            int bobPort = bobService.getListeningPort();

            CountDownLatch aliceRecvLatch = new CountDownLatch(1);
            CountDownLatch bobRecvLatch = new CountDownLatch(1);
            AtomicReference<String> aliceReceivedMsg = new AtomicReference<>();
            AtomicReference<String> bobReceivedMsg = new AtomicReference<>();

            aliceService.addEventListener(new PeerNetworkService.NetworkEventListener() {
                @Override
                public void onMessageReceived(EncryptedPeerSession session, UUID messageId, String text, long timestamp) {
                    aliceReceivedMsg.set(text);
                    aliceRecvLatch.countDown();
                }
            });

            bobService.addEventListener(new PeerNetworkService.NetworkEventListener() {
                @Override
                public void onMessageReceived(EncryptedPeerSession session, UUID messageId, String text, long timestamp) {
                    bobReceivedMsg.set(text);
                    bobRecvLatch.countDown();
                }
            });

            // Alice and Bob connect to each other simultaneously
            CompletableFuture<EncryptedPeerSession> aliceFuture = aliceService.connectToPeerAsync(
                    "bob", "127.0.0.1", bobPort, bobKeys.getIdentityKeyPair().getPublic(), null
            );
            CompletableFuture<EncryptedPeerSession> bobFuture = bobService.connectToPeerAsync(
                    "alice", "127.0.0.1", alicePort, aliceKeys.getIdentityKeyPair().getPublic(), null
            );

            CompletableFuture.allOf(aliceFuture, bobFuture).get(10, TimeUnit.SECONDS);

            EncryptedPeerSession aliceSession = aliceFuture.get();
            EncryptedPeerSession bobSession = bobFuture.get();

            assertNotNull(aliceSession, "Alice session must not be null");
            assertNotNull(bobSession, "Bob session must not be null");
            assertTrue(aliceSession.isSecureEstablished(), "Alice session must be established");
            assertTrue(bobSession.isSecureEstablished(), "Bob session must be established");

            // Bidirectional messaging
            aliceSession.sendChatMessage("Hello from Alice!").get(5, TimeUnit.SECONDS);
            assertTrue(bobRecvLatch.await(5, TimeUnit.SECONDS), "Bob did not receive Alice's message");
            assertEquals("Hello from Alice!", bobReceivedMsg.get());

            bobSession.sendChatMessage("Hello from Bob!").get(5, TimeUnit.SECONDS);
            assertTrue(aliceRecvLatch.await(5, TimeUnit.SECONDS), "Alice did not receive Bob's message");
            assertEquals("Hello from Bob!", aliceReceivedMsg.get());
        }
    }

    @Test
    void testInboundSessionReceivesMessagesAndAcksViaEventListener() throws Exception {
        try (PeerNetworkService bobService = new PeerNetworkService("bob", bobKeys, bobTrust, 0);
             PeerNetworkService aliceService = new PeerNetworkService("alice", aliceKeys, aliceTrust, 0)) {

            bobService.startServer();
            aliceService.startServer();

            CountDownLatch bobReceived = new CountDownLatch(1);
            CountDownLatch aliceDelivered = new CountDownLatch(1);
            AtomicReference<String> bobText = new AtomicReference<>();

            bobService.addEventListener(new PeerNetworkService.NetworkEventListener() {
                @Override
                public void onMessageReceived(EncryptedPeerSession session, UUID messageId, String text, long timestamp) {
                    bobText.set(text);
                    bobReceived.countDown();
                }
            });

            aliceService.addEventListener(new PeerNetworkService.NetworkEventListener() {
                @Override
                public void onDeliveryAckReceived(EncryptedPeerSession session, UUID messageId) {
                    aliceDelivered.countDown();
                }
            });

            EncryptedPeerSession aliceSession = aliceService.connectToPeerAsync(
                    "bob", "127.0.0.1", bobService.getListeningPort(), bobKeys.getIdentityKeyPair().getPublic(), null
            ).get(5, TimeUnit.SECONDS);

            assertTrue(aliceSession.isSecureEstablished());

            UUID sentId = aliceSession.sendChatMessage("Encrypted payload for inbound").get(5, TimeUnit.SECONDS);
            assertNotNull(sentId);

            assertTrue(bobReceived.await(5, TimeUnit.SECONDS), "Bob did not receive inbound message via NetworkEventListener");
            assertEquals("Encrypted payload for inbound", bobText.get());

            assertTrue(aliceDelivered.await(5, TimeUnit.SECONDS), "Alice did not receive delivery ACK via NetworkEventListener");
        }
    }

    @Test
    void testHandshakeFailureOnTamperedKey() throws Exception {
        bobServer = new PeerServer(0, (server, conn) -> {
            bobConn = conn;
            bobSession = new EncryptedPeerSession(
                    conn, "bob", bobKeys, bobTrust, false, null, null
            );
            try {
                bobSession.start();
            } catch (IOException ignored) {}
        });
        bobServer.start();

        // Alice connects to Bob but specifies an incorrect/tampered public key for Bob
        aliceConn = PeerClient.connect("127.0.0.1", bobServer.getPort(), new com.project.p2p.common.network.PeerConnectionListener() {
            @Override public void onFrameReceived(PeerConnection conn, com.project.p2p.common.protocol.ProtocolFrame frame) {}
            @Override public void onDisconnected(PeerConnection conn, Throwable cause) {}
        });
        aliceSession = new EncryptedPeerSession(
                aliceConn, "alice", aliceKeys, aliceTrust, true, "bob", null
        );

        // Generate a random key that does NOT match Bob's real key
        java.security.KeyPair fakeBobKey = CryptoPrimitives.generateEd25519KeyPair();
        aliceSession.verifyPeerIdentity("bob", fakeBobKey.getPublic());

        aliceSession.start();

        // Alice's handshake should fail because Bob's signature will not verify against the fake key
        assertThrows(Exception.class, () -> aliceSession.getHandshakeFuture().get(5, TimeUnit.SECONDS));
        assertEquals(SessionState.FAILED, aliceSession.getState());
    }

    @Test
    void testMixedCaseUsernameHandshakeAndMessaging() throws Exception {
        // Reproduces the exact case mismatch scenario: "Ajitesh" connecting to "Sarvansh"
        ClientKeyManager ajiteshKeys = new ClientKeyManager(tempDir.resolve("ajitesh_keys"));
        ajiteshKeys.initialize("Ajitesh", "pass-ajitesh");

        ClientKeyManager sarvanshKeys = new ClientKeyManager(tempDir.resolve("sarvansh_keys"));
        sarvanshKeys.initialize("Sarvansh", "pass-sarvansh");

        TrustStore ajiteshTrust = new TrustStore(tempDir.resolve("ajitesh_trust.properties"));
        TrustStore sarvanshTrust = new TrustStore(tempDir.resolve("sarvansh_trust.properties"));

        try (PeerNetworkService sarvanshService = new PeerNetworkService("Sarvansh", sarvanshKeys, sarvanshTrust, 0);
             PeerNetworkService ajiteshService = new PeerNetworkService("Ajitesh", ajiteshKeys, ajiteshTrust, 0)) {

            // Configure dynamic key resolvers
            sarvanshService.setPeerKeyResolver(user -> ajiteshKeys.getIdentityKeyPair().getPublic());
            ajiteshService.setPeerKeyResolver(user -> sarvanshKeys.getIdentityKeyPair().getPublic());

            sarvanshService.startServer();
            ajiteshService.startServer();

            CountDownLatch sarvanshRecvLatch = new CountDownLatch(1);
            AtomicReference<String> sarvanshRecvText = new AtomicReference<>();

            sarvanshService.addEventListener(new PeerNetworkService.NetworkEventListener() {
                @Override
                public void onMessageReceived(EncryptedPeerSession session, UUID messageId, String text, long timestamp) {
                    sarvanshRecvText.set(text);
                    sarvanshRecvLatch.countDown();
                }
            });

            // Ajitesh initiates connection to "sarvansh"
            EncryptedPeerSession ajiteshSession = ajiteshService.connectToPeerAsync(
                    "sarvansh", "127.0.0.1", sarvanshService.getListeningPort(), sarvanshKeys.getIdentityKeyPair().getPublic(), null
            ).get(5, TimeUnit.SECONDS);

            assertNotNull(ajiteshSession);
            assertTrue(ajiteshSession.isSecureEstablished(), "Session must be established despite mixed-case usernames");

            ajiteshSession.sendChatMessage("Hello Sarvansh from Ajitesh!").get(5, TimeUnit.SECONDS);

            assertTrue(sarvanshRecvLatch.await(5, TimeUnit.SECONDS), "Sarvansh must receive the message");
            assertEquals("Hello Sarvansh from Ajitesh!", sarvanshRecvText.get());
        }
    }

    @Test
    void testTofuKeyChangeRejectionViaNetworkService() throws Exception {
        // Pre-populate Alice's trust store with Bob's legitimate original fingerprint
        aliceTrust.trustFingerprint("bob", bobKeys.getIdentityFingerprint());

        // Now create a rogue Bob with a newly generated identity key
        ClientKeyManager rogueBobKeys = new ClientKeyManager(tempDir.resolve("rogue_bob_keys"));
        rogueBobKeys.initialize("bob", "pass-rogue");
        TrustStore rogueBobTrust = new TrustStore(tempDir.resolve("rogue_bob_trust.properties"));

        try (PeerNetworkService rogueBobService = new PeerNetworkService("bob", rogueBobKeys, rogueBobTrust, 0);
             PeerNetworkService aliceService = new PeerNetworkService("alice", aliceKeys, aliceTrust, 0)) {

            rogueBobService.startServer();
            aliceService.startServer();

            // Alice attempts to connect to Bob, but rogue Bob presents an altered key
            CompletableFuture<EncryptedPeerSession> future = aliceService.connectToPeerAsync(
                    "bob", "127.0.0.1", rogueBobService.getListeningPort(), rogueBobKeys.getIdentityKeyPair().getPublic(), null
            );

            // Must fail due to TOFU key mismatch
            ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertTrue(ex.getCause() instanceof SecurityException, "Failure cause must be a SecurityException");
            assertTrue(ex.getCause().getMessage().contains("TOFU"), "Message should mention TOFU violation");
        }
    }
}
