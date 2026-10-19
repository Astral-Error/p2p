package com.project.p2p.common.protocol.exceptions;

/**
 * Base exception for application-layer wire protocol errors.
 */
public class ProtocolException extends Exception {

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
