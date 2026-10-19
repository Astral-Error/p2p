package com.project.p2p.common.network;

/**
 * Lifecycle states of an outgoing message.
 */
public enum MessageDeliveryState {
    /** Staged locally, not yet transmitted over the wire */
    PENDING,

    /** Transmitted over the TCP socket to the remote peer; awaiting delivery ACK */
    SENT,

    /** Explicitly acknowledged as received and verified by the remote peer */
    DELIVERED,

    /** Explicitly acknowledged as read by the remote peer */
    READ,

    /** Transmission failed or connection severed before acknowledgement */
    FAILED
}
