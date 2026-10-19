package com.project.p2p.common.protocol.exceptions;

/**
 * Thrown when incoming stream bytes do not match the expected magic preamble (0x50325031 'P2P1').
 */
public class InvalidMagicException extends ProtocolException {

    private final int actualMagic;

    public InvalidMagicException(int actualMagic) {
        super(String.format("Invalid magic bytes: 0x%08X (expected 'P2P1' 0x50325031)", actualMagic));
        this.actualMagic = actualMagic;
    }

    public int getActualMagic() {
        return actualMagic;
    }
}
