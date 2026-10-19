package com.project.p2p.common.network;

import com.project.p2p.common.protocol.FrameEncoder;
import com.project.p2p.common.protocol.FrameStreamReader;
import com.project.p2p.common.protocol.MessageType;
import com.project.p2p.common.protocol.ProtocolFrame;
import com.project.p2p.common.protocol.exceptions.ProtocolException;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe wrapper around a peer-to-peer TCP socket session.
 * Manages concurrent frame transmissions, continuous background stream reading,
 * and dispatching to registered {@link PeerConnectionListener} instances.
 */
public class PeerConnection implements AutoCloseable {

    private final String id;
    private final Socket socket;
    private final OutputStream outputStream;
    private final FrameStreamReader streamReader;
    private final List<PeerConnectionListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong sequenceGenerator = new AtomicLong(1);
    private final Object writeLock = new Object();
    private Thread readerThread;

    public PeerConnection(Socket socket) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket must not be null");
        this.id = UUID.randomUUID().toString();
        this.outputStream = new BufferedOutputStream(socket.getOutputStream());
        this.streamReader = new FrameStreamReader(socket.getInputStream());
    }

    public PeerConnection(Socket socket, PeerConnectionListener listener) throws IOException {
        this(socket);
        if (listener != null) {
            this.listeners.add(listener);
        }
    }

    public void addListener(PeerConnectionListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(PeerConnectionListener listener) {
        listeners.remove(listener);
    }

    /**
     * Starts the asynchronous background frame reader loop.
     */
    public synchronized void start() {
        if (readerThread != null) {
            return;
        }

        readerThread = new Thread(this::readLoop, "PeerConnection-Reader-" + id.substring(0, 8));
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop() {
        Throwable failureCause = null;
        try {
            while (!closed.get()) {
                ProtocolFrame frame = streamReader.readFrame();
                if (frame == null) {
                    // Clean EOF: peer closed socket normally
                    break;
                }
                for (PeerConnectionListener listener : listeners) {
                    try {
                        listener.onFrameReceived(this, frame);
                    } catch (Exception e) {
                        System.err.println("Error handling frame in listener: " + e.getMessage());
                    }
                }
            }
        } catch (ProtocolException | IOException e) {
            if (!closed.get()) {
                failureCause = e;
            }
        } finally {
            closeQuietly();
            for (PeerConnectionListener listener : listeners) {
                try {
                    listener.onDisconnected(this, failureCause);
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Sends an already constructed ProtocolFrame over the wire.
     *
     * @param frame frame to transmit
     * @throws IOException on I/O failure or if connection is closed
     */
    public void send(ProtocolFrame frame) throws IOException {
        Objects.requireNonNull(frame, "frame must not be null");
        if (closed.get() || socket.isClosed()) {
            throw new IOException("Cannot send frame: connection is closed");
        }

        synchronized (writeLock) {
            FrameEncoder.encodeToStream(frame, outputStream);
        }
    }

    /**
     * Generates a monotonic sequence number, wraps the payload, and sends the frame.
     *
     * @param type message type
     * @param payload raw payload bytes
     * @return the transmitted ProtocolFrame
     * @throws IOException on I/O error
     */
    public ProtocolFrame sendFrame(MessageType type, byte[] payload) throws IOException {
        long seq = sequenceGenerator.getAndIncrement();
        ProtocolFrame frame = ProtocolFrame.of(type, seq, payload);
        send(frame);
        return frame;
    }

    public long getNextSequenceNumber() {
        return sequenceGenerator.getAndIncrement();
    }

    public String getId() {
        return id;
    }

    public SocketAddress getRemoteAddress() {
        return socket.getRemoteSocketAddress();
    }

    public SocketAddress getLocalAddress() {
        return socket.getLocalSocketAddress();
    }

    public boolean isConnected() {
        return !closed.get() && socket.isConnected() && !socket.isClosed();
    }

    private void closeQuietly() {
        if (closed.compareAndSet(false, true)) {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    @Override
    public void close() {
        closeQuietly();
        if (readerThread != null && Thread.currentThread() != readerThread) {
            readerThread.interrupt();
        }
    }
}
