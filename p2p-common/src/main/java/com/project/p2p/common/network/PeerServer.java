package com.project.p2p.common.network;

import com.project.p2p.common.protocol.ProtocolConstants;
import com.project.p2p.common.protocol.ProtocolFrame;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Multi-peer asynchronous TCP listening server.
 * Accepts inbound peer connections without blocking UI or caller threads.
 */
public class PeerServer implements AutoCloseable {

    private final String bindHost;
    private final int port;
    private final BiConsumer<PeerServer, PeerConnection> onPeerConnected;
    private final List<PeerConnection> activeConnections = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private ExecutorService acceptExecutor;

    public PeerServer(int port, BiConsumer<PeerServer, PeerConnection> onPeerConnected) {
        this("0.0.0.0", port, onPeerConnected);
    }

    public PeerServer(String bindHost, int port, BiConsumer<PeerServer, PeerConnection> onPeerConnected) {
        this.bindHost = Objects.requireNonNull(bindHost, "bindHost must not be null");
        this.port = port;
        this.onPeerConnected = Objects.requireNonNull(onPeerConnected, "onPeerConnected callback must not be null");
    }

    /**
     * Starts listening for peer connections.
     *
     * @throws IOException if server socket fails to bind
     */
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(bindHost, port));
        running.set(true);

        acceptExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PeerServer-Accept-" + getPort());
            t.setDaemon(true);
            return t;
        });

        acceptExecutor.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running.get() && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(ProtocolConstants.DEFAULT_SO_TIMEOUT_MS);

                PeerConnection connection = new PeerConnection(socket);
                activeConnections.add(connection);

                connection.addListener(new PeerConnectionListener() {
                    @Override
                    public void onFrameReceived(PeerConnection conn, ProtocolFrame frame) {}

                    @Override
                    public void onDisconnected(PeerConnection conn, Throwable cause) {
                        activeConnections.remove(conn);
                    }
                });

                try {
                    onPeerConnected.accept(this, connection);
                } catch (Exception e) {
                    System.err.println("Error in onPeerConnected handler: " + e.getMessage());
                }

                connection.start();
            } catch (IOException e) {
                if (!running.get() || serverSocket.isClosed()) {
                    break;
                }
                System.err.println("Accept loop error: " + e.getMessage());
            }
        }
    }

    public void broadcast(ProtocolFrame frame) {
        for (PeerConnection conn : activeConnections) {
            try {
                conn.send(frame);
            } catch (IOException ignored) {}
        }
    }

    public int getPort() {
        return (serverSocket != null) ? serverSocket.getLocalPort() : port;
    }

    public boolean isRunning() {
        return running.get() && serverSocket != null && !serverSocket.isClosed();
    }

    public int getActiveConnectionCount() {
        return activeConnections.size();
    }

    public List<PeerConnection> getActiveConnections() {
        return List.copyOf(activeConnections);
    }

    @Override
    public synchronized void close() {
        if (running.compareAndSet(true, false)) {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException ignored) {}

            for (PeerConnection conn : activeConnections) {
                conn.close();
            }
            activeConnections.clear();

            if (acceptExecutor != null) {
                acceptExecutor.shutdownNow();
            }
        }
    }
}
