package com.project.p2p.client.network;

/**
 * Explicit lifecycle states of an encrypted peer-to-peer session.
 */
public enum SessionState {
    /**
     * Underlying TCP socket is opening or initial connection is in progress.
     */
    CONNECTING,

    /**
     * TCP connection is open and cryptographic handshake exchange (X25519, Ed25519) is underway.
     */
    HANDSHAKING,

    /**
     * Handshake successfully completed; mutual authentication and session key derivation succeeded.
     * Ready for bidirectional end-to-end encrypted messaging.
     */
    ESTABLISHED,

    /**
     * Handshake failed, signature verification failed, timeout occurred, or security check failed.
     */
    FAILED,

    /**
     * Session cleanly closed or underlying connection severed.
     */
    CLOSED
}
