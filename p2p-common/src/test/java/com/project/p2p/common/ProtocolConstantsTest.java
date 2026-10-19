package com.project.p2p.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolConstantsTest {

    @Test
    void testProtocolConstantsDefined() {
        assertEquals(0x50325031, ProtocolConstants.MAGIC_BYTES);
        assertEquals((byte) 0x01, ProtocolConstants.PROTOCOL_VERSION);
        assertTrue(ProtocolConstants.MAX_FRAME_PAYLOAD_SIZE > 0);
    }
}
