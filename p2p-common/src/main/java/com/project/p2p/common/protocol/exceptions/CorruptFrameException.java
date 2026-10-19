package com.project.p2p.common.protocol.exceptions;

/**
 * Thrown when header CRC32 check fails, indicating bit flips or data corruption in the stream.
 */
public class CorruptFrameException extends ProtocolException {

    private final long expectedCrc;
    private final long actualCrc;

    public CorruptFrameException(long expectedCrc, long actualCrc) {
        super(String.format("Frame header CRC32 mismatch: expected 0x%08X but computed 0x%08X", expectedCrc, actualCrc));
        this.expectedCrc = expectedCrc;
        this.actualCrc = actualCrc;
    }

    public long getExpectedCrc() {
        return expectedCrc;
    }

    public long getActualCrc() {
        return actualCrc;
    }
}
