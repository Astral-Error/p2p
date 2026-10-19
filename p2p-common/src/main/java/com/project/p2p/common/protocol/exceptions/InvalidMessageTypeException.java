package com.project.p2p.common.protocol.exceptions;

/**
 * Thrown when an unknown or undefined message type byte is received.
 */
public class InvalidMessageTypeException extends ProtocolException {

    private final byte rawType;

    public InvalidMessageTypeException(byte rawType) {
        super(String.format("Invalid message type code: 0x%02X", rawType));
        this.rawType = rawType;
    }

    public byte getRawType() {
        return rawType;
    }
}
