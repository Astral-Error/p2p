package com.project.p2p.dto;

public class AuthResponse {

    private String token;
    private String tokenType = "Bearer";
    private String username;
    private Long userId;
    private long expiresInMs;

    public AuthResponse() {}

    public AuthResponse(String token, String username, Long userId, long expiresInMs) {
        this.token = token;
        this.tokenType = "Bearer";
        this.username = username;
        this.userId = userId;
        this.expiresInMs = expiresInMs;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public long getExpiresInMs() {
        return expiresInMs;
    }

    public void setExpiresInMs(long expiresInMs) {
        this.expiresInMs = expiresInMs;
    }
}
