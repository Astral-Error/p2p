package com.project.p2p.client.service;

import com.project.p2p.client.model.AuthResult;
import com.project.p2p.client.model.KeyRecord;
import com.project.p2p.client.model.OnlinePeer;
import com.project.p2p.client.model.PresenceSession;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class CoordinationServiceClientTest {

    private HttpServer server;
    private CoordinationServiceClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.start();

        client = new CoordinationServiceClient("http://127.0.0.1:" + port);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testRegisterAndLoginAsync() throws Exception {
        server.createContext("/api/auth/register", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            String response = "{\"token\":\"jwt-test-token-123\",\"tokenType\":\"Bearer\",\"username\":\"alice\",\"userId\":1,\"expiresInMs\":3600000}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        AuthResult auth = client.registerAsync("alice", "password123").get();
        assertNotNull(auth);
        assertEquals("alice", auth.username());
        assertEquals("jwt-test-token-123", auth.token());
        assertEquals("jwt-test-token-123", client.getAccessToken());
    }

    @Test
    void testAuthenticationRequiredBeforeProtectedCalls() {
        client.clearSession();
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                client.registerPresenceAsync(9001).get());
        assertTrue(ex.getCause() instanceof IllegalStateException);
    }

    @Test
    void testPublishAndRetrieveKeysAsync() throws Exception {
        client.setAccessToken("valid-bearer-token");

        server.createContext("/api/keys", exchange -> {
            assertEquals("Bearer valid-bearer-token", exchange.getRequestHeaders().getFirst("Authorization"));
            String response = "{\"username\":\"alice\",\"identityPublicKey\":\"base64-ed25519\",\"keyAgreementPublicKey\":\"base64-x25519\",\"updatedAt\":\"2026-10-19T10:00:00Z\"}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        KeyRecord record = client.publishKeysAsync("base64-ed25519", "base64-x25519", false).get();
        assertEquals("alice", record.username());
        assertEquals("base64-ed25519", record.identityPublicKey());
        assertEquals("base64-x25519", record.keyAgreementPublicKey());
    }

    @Test
    void testPresenceAndOnlinePeersAsync() throws Exception {
        client.setAccessToken("valid-bearer-token");

        server.createContext("/api/peers/presence", exchange -> {
            String response = "{\"sessionId\":\"sess-uuid-1\",\"observedIp\":\"127.0.0.1\",\"listeningPort\":9001,\"status\":\"ONLINE\"}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.createContext("/api/peers/online", exchange -> {
            String response = "[{\"username\":\"bob\",\"sessionId\":\"sess-uuid-2\",\"observedIp\":\"192.168.1.5\",\"listeningPort\":9002,\"lastHeartbeat\":\"2026-10-19T10:00:00Z\",\"identityPublicKey\":\"keyA\",\"keyAgreementPublicKey\":\"keyB\"}]";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        PresenceSession session = client.registerPresenceAsync(9001).get();
        assertEquals("sess-uuid-1", session.sessionId());
        assertEquals(9001, session.listeningPort());

        List<OnlinePeer> onlinePeers = client.getOnlinePeersAsync().get();
        assertEquals(1, onlinePeers.size());
        assertEquals("bob", onlinePeers.get(0).username());
        assertEquals(9002, onlinePeers.get(0).listeningPort());
    }

    @Test
    void testServerErrorTranslation() {
        server.createContext("/api/auth/login", exchange -> {
            String response = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid username or password\"}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                client.loginAsync("alice", "badpass").get());

        assertTrue(ex.getCause() instanceof CoordinationClientException);
        CoordinationClientException clientEx = (CoordinationClientException) ex.getCause();
        assertEquals(401, clientEx.getStatusCode());
        assertTrue(clientEx.getMessage().contains("Invalid username or password"));
    }
}
