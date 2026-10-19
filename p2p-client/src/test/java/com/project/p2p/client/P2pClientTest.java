package com.project.p2p.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class P2pClientTest {

    @Test
    void testClientAppClassPresent() {
        assertNotNull(P2pClientApp.class);
        assertNotNull(P2pClientLauncher.class);
    }
}
