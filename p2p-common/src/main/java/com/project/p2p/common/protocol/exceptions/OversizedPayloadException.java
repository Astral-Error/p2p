package com.project.p2p.common.protocol.exceptions;

/**
 * Thrown when an advertised payload length exceeds the maximum permitted frame size or is negative.
 */
public class OversizedPayloadException extends ProtocolException {

    private final int requestedLength;
    private final int maxAllowedLength;

    public OversizedPayloadException(int requestedLength, int maxAllowedLength) {
        super(String.format("Payload size %d bytes exceeds maximum permitted size of %d bytes", requestedLength, maxAllowedLength));
        this.requestedLength = requestedLength;
        this.maxAllowedLength = maxAllowedLength;
    }

    public int getRequestedLength() {
        return requestedLength;
    }

    public int getMaxAllowedLength() {
        return maxAllowedLength;
    }
}
