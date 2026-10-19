package com.project.p2p.common.protocol;

import com.project.p2p.common.protocol.exceptions.InvalidMessageTypeException;

/**
 * Enumeration of application-layer wire protocol message types.
 */
public enum MessageType {

    /** Initial Diffie-Hellman ephemeral key exchange proposal */
    HANDSHAKE_INIT((byte) 0x01),

    /** Diffie-Hellman ephemeral key exchange response and verification */
    HANDSHAKE_RESPONSE((byte) 0x02),

    /** Encrypted chat message envelope */
    CHAT_MESSAGE((byte) 0x03),

    /** Explicit acknowledgement that a message was received and validated by peer */
    ACK_DELIVERED((byte) 0x04),

    /** Explicit acknowledgement that the user opened/read the message */
    ACK_READ((byte) 0x05),

    /** Network latency keepalive probe */
    PING((byte) 0x06),

    /** Network latency keepalive response */
    PONG((byte) 0x07),

    /** Orderly peer session disconnection notification */
    DISCONNECT((byte) 0x08);

    private final byte code;

    MessageType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    /**
     * Resolves a byte code to its MessageType enum value.
     *
     * @param code raw byte code
     * @return matching MessageType
     * @throws InvalidMessageTypeException if code is not recognized
     */
    public static MessageType fromCode(byte code) throws InvalidMessageTypeException {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new InvalidMessageTypeException(code);
    }
}
