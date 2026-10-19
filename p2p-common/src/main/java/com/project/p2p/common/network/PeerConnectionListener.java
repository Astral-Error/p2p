package com.project.p2p.common.network;

import com.project.p2p.common.protocol.ProtocolFrame;

/**
 * Callback listener for peer socket events and frame receptions.
 */
public interface PeerConnectionListener {

    /** Invoked when a frame is received and verified from the peer socket */
    void onFrameReceived(PeerConnection connection, ProtocolFrame frame);

    /** Invoked when the socket connection is severed or orderly closed */
    void onDisconnected(PeerConnection connection, Throwable cause);
}
