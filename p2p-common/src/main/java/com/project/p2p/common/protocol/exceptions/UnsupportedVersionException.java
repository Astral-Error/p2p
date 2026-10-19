package com.project.p2p.common.protocol.exceptions;

/**
 * Thrown when incoming frame version is not supported by this node.
 */
public class UnsupportedVersionException extends ProtocolException {

    private final byte actualVersion;

    public UnsupportedVersionException(byte actualVersion) {
        super(String.format("Unsupported protocol version: 0x%02X", actualVersion));
        this.actualVersion = actualVersion;
    }

    public byte getActualVersion() {
        return actualVersion;
    }
}
