package com.project.p2p.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private final String secret = "a-very-long-and-secure-test-secret-key-that-exceeds-256-bits-for-testing";
    private final JwtTokenProvider provider = new JwtTokenProvider(secret, 3600000); // 1 hour

    @Test
    void testGenerateAndValidateToken() {
        String token = provider.generateToken("alice", 1L);
        assertNotNull(token);
        assertTrue(provider.validateToken(token));
        assertEquals("alice", provider.getUsernameFromToken(token));
        assertEquals(1L, provider.getUserIdFromToken(token));
    }

    @Test
    void testInvalidTokenRejected() {
        assertFalse(provider.validateToken("not-a-valid-token"));
        assertFalse(provider.validateToken(""));
        assertFalse(provider.validateToken(null));
    }

    @Test
    void testTamperedTokenRejected() {
        String token = provider.generateToken("alice", 1L);
        // Tamper with the token payload
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        String tampered = parts[0] + "." + parts[1] + "tampered." + parts[2];
        assertFalse(provider.validateToken(tampered));
    }

    @Test
    void testExpiredTokenRejected() {
        // Provider with negative expiration (already expired)
        JwtTokenProvider expiredProvider = new JwtTokenProvider(secret, -1000);
        String token = expiredProvider.generateToken("bob", 2L);
        assertFalse(expiredProvider.validateToken(token));
    }
}
