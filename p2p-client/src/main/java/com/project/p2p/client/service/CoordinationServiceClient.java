package com.project.p2p.client.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.p2p.client.model.AuthResult;
import com.project.p2p.client.model.KeyRecord;
import com.project.p2p.client.model.OnlinePeer;
import com.project.p2p.client.model.PresenceSession;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Asynchronous HTTP client communicating with the P2P Coordination Server.
 * All remote calls are executed off the UI thread via CompletableFuture.
 */
public class CoordinationServiceClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicReference<String> accessToken = new AtomicReference<>(null);

    public CoordinationServiceClient() {
        this("http://localhost:8080");
    }

    public CoordinationServiceClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public CoordinationServiceClient(String baseUrl, HttpClient httpClient) {
        this.baseUrl = (baseUrl.endsWith("/")) ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void setAccessToken(String token) {
        this.accessToken.set(token);
    }

    public String getAccessToken() {
        return this.accessToken.get();
    }

    public void clearSession() {
        this.accessToken.set(null);
    }

    // --- Authentication ---

    public CompletableFuture<AuthResult> registerAsync(String username, String password) {
        Map<String, String> body = Map.of("username", username, "password", password);
        return sendRequestAsync("POST", "/api/auth/register", body, false, AuthResult.class)
                .thenApply(auth -> {
                    setAccessToken(auth.token());
                    return auth;
                });
    }

    public CompletableFuture<AuthResult> loginAsync(String username, String password) {
        Map<String, String> body = Map.of("username", username, "password", password);
        return sendRequestAsync("POST", "/api/auth/login", body, false, AuthResult.class)
                .thenApply(auth -> {
                    setAccessToken(auth.token());
                    return auth;
                });
    }

    // --- Public Key Directory ---

    public CompletableFuture<KeyRecord> publishKeysAsync(String identityPublicKey, String keyAgreementPublicKey, boolean replaceConfirmed) {
        Map<String, Object> body = Map.of(
                "identityPublicKey", identityPublicKey,
                "keyAgreementPublicKey", keyAgreementPublicKey,
                "replaceConfirmed", replaceConfirmed
        );
        return sendRequestAsync("POST", "/api/keys", body, true, KeyRecord.class);
    }

    public CompletableFuture<KeyRecord> getPeerKeysAsync(String username) {
        return sendRequestAsync("GET", "/api/keys/" + username, null, true, KeyRecord.class);
    }

    public CompletableFuture<KeyRecord> getMyKeysAsync() {
        return sendRequestAsync("GET", "/api/keys/me", null, true, KeyRecord.class);
    }

    // --- Presence and Peer Discovery ---

    public CompletableFuture<PresenceSession> registerPresenceAsync(int listeningPort) {
        Map<String, Object> body = Map.of("listeningPort", listeningPort);
        return sendRequestAsync("POST", "/api/peers/presence", body, true, PresenceSession.class);
    }

    public CompletableFuture<Void> sendHeartbeatAsync(String sessionId) {
        Map<String, String> body = Map.of("sessionId", sessionId);
        return sendRequestAsync("POST", "/api/peers/heartbeat", body, true, JsonNode.class)
                .thenAccept(ignored -> {});
    }

    public CompletableFuture<Void> disconnectPresenceAsync(String sessionId) {
        Map<String, String> body = Map.of("sessionId", sessionId);
        return sendRequestAsync("POST", "/api/peers/disconnect", body, true, JsonNode.class)
                .thenAccept(ignored -> {});
    }

    public CompletableFuture<List<OnlinePeer>> getOnlinePeersAsync() {
        return sendRequestAsyncList("/api/peers/online", true, new TypeReference<List<OnlinePeer>>() {});
    }

    // --- Internal HTTP helpers ---

    private <T> CompletableFuture<T> sendRequestAsync(String method, String path, Object requestBody, boolean requireAuth, Class<T> responseType) {
        try {
            HttpRequest request = buildRequest(method, path, requestBody, requireAuth);
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> handleResponse(response, responseType));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private <T> CompletableFuture<T> sendRequestAsyncList(String path, boolean requireAuth, TypeReference<T> typeRef) {
        try {
            HttpRequest request = buildRequest("GET", path, null, requireAuth);
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        int code = response.statusCode();
                        if (code >= 200 && code < 300) {
                            try {
                                return objectMapper.readValue(response.body(), typeRef);
                            } catch (Exception e) {
                                throw new CoordinationClientException("Failed to parse response body", e);
                            }
                        }
                        throw createException(code, response.body());
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private HttpRequest buildRequest(String method, String path, Object requestBody, boolean requireAuth) throws IOException {
        URI uri = URI.create(baseUrl + path);
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri).timeout(Duration.ofSeconds(10));

        builder.header("Accept", "application/json");

        if (requireAuth) {
            String token = accessToken.get();
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("Authentication required: no access token set");
            }
            builder.header("Authorization", "Bearer " + token);
        }

        if (requestBody != null) {
            builder.header("Content-Type", "application/json");
            byte[] jsonBytes = objectMapper.writeValueAsBytes(requestBody);
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(jsonBytes));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        return builder.build();
    }

    private <T> T handleResponse(HttpResponse<String> response, Class<T> responseType) {
        int code = response.statusCode();
        String body = response.body();

        if (code >= 200 && code < 300) {
            if (responseType == Void.class) {
                return null;
            }
            try {
                return objectMapper.readValue(body, responseType);
            } catch (Exception e) {
                throw new CoordinationClientException("Failed to parse response: " + e.getMessage(), e);
            }
        }

        throw createException(code, body);
    }

    private CoordinationClientException createException(int code, String body) {
        String message = "Server responded with status " + code;
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("message") && !node.get("message").asText().isBlank()) {
                message = node.get("message").asText();
            } else if (node.has("error") && !node.get("error").asText().isBlank()) {
                message = node.get("error").asText();
            }
        } catch (Exception ignored) {}
        return new CoordinationClientException(code, message);
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
