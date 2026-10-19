package com.project.p2p.client.model;

public record AuthResult(String token, String tokenType, String username, Long userId, long expiresInMs) {}
