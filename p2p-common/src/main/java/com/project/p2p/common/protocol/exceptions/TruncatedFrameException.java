package com.project.p2p.common.protocol.exceptions;

/**
 * Thrown when the stream ends unexpectedly before a complete frame header or payload can be read.
 */
public class TruncatedFrameException extends ProtocolException {

    private final int expectedBytes;
    private final int actualBytes;

    public TruncatedFrameException(int expectedBytes, int actualBytes) {
        super(String.format("Premature end of stream: expected %d bytes but only read %d", expectedBytes, actualBytes));
        this.expectedBytes = expectedBytes;
        this.actualBytes = actualBytes;
    }

    public int getExpectedBytes() {
        return expectedBytes;
    }

    public int getActualBytes() {
        return actualBytes;
    }
}
