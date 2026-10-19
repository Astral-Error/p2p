package com.project.p2p.common;

/**
 * Convenience legacy forwarding reference to {@link com.project.p2p.common.protocol.ProtocolConstants}.
 */
public final class ProtocolConstants {

    private ProtocolConstants() {}

    public static final int MAGIC_BYTES = com.project.p2p.common.protocol.ProtocolConstants.MAGIC_BYTES;
    public static final byte PROTOCOL_VERSION = com.project.p2p.common.protocol.ProtocolConstants.CURRENT_VERSION;
    public static final int MAX_FRAME_PAYLOAD_SIZE = com.project.p2p.common.protocol.ProtocolConstants.DEFAULT_MAX_PAYLOAD_SIZE;
    public static final int DEFAULT_PEER_PORT = com.project.p2p.common.protocol.ProtocolConstants.DEFAULT_PEER_PORT;
}
