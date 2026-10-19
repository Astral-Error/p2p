package com.project.p2p.common.network;

import com.project.p2p.common.protocol.ProtocolConstants;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;

/**
 * TCP Peer connector that establishes outbound connections to other peers.
 */
public final class PeerClient {

    private PeerClient() {}

    /**
     * Establishes a direct TCP connection to a remote peer.
     *
     * @param host remote host or IP address
     * @param port remote TCP port
     * @param listener event listener for incoming frames and lifecycle events
     * @return active PeerConnection ready for communication
     * @throws IOException if connection fails or times out
     */
    public static PeerConnection connect(String host, int port, PeerConnectionListener listener) throws IOException {
        return connect(host, port, listener, ProtocolConstants.CONNECT_TIMEOUT_MS);
    }

    /**
     * Establishes a direct TCP connection with explicit timeout.
     *
     * @param host remote host or IP address
     * @param port remote TCP port
     * @param listener event listener
     * @param timeoutMs connection timeout in milliseconds
     * @return active PeerConnection
     * @throws IOException on connection failure
     */
    public static PeerConnection connect(String host, int port, PeerConnectionListener listener, int timeoutMs) throws IOException {
        Objects.requireNonNull(host, "host must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        Socket socket = new Socket();
        socket.setReuseAddress(true);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(ProtocolConstants.DEFAULT_SO_TIMEOUT_MS);

        socket.connect(new InetSocketAddress(host, port), timeoutMs);

        PeerConnection connection = new PeerConnection(socket, listener);
        connection.start();
        return connection;
    }
}
