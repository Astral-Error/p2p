package com.project.p2p.common.network;

import com.project.p2p.common.protocol.MessageType;
import com.project.p2p.common.protocol.ProtocolFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TcpPeerNetworkingTest {

    private PeerServer server;
    private final List<PeerConnection> connectionsToClose = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() {
        for (PeerConnection conn : connectionsToClose) {
            try {
                conn.close();
            } catch (Exception ignored) {}
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void testDirectTcpConnectionAndFrameExchange() throws Exception {
        CountDownLatch serverReceivedLatch = new CountDownLatch(1);
        CountDownLatch clientReceivedLatch = new CountDownLatch(1);
        AtomicReference<ProtocolFrame> serverReceivedFrame = new AtomicReference<>();
        AtomicReference<ProtocolFrame> clientReceivedFrame = new AtomicReference<>();

        // Start PeerServer on ephemeral port
        server = new PeerServer("127.0.0.1", 0, (srv, serverSideConn) -> {
            connectionsToClose.add(serverSideConn);
            serverSideConn.addListener(new PeerConnectionListener() {
                @Override
                public void onFrameReceived(PeerConnection connection, ProtocolFrame frame) {
                    serverReceivedFrame.set(frame);
                    serverReceivedLatch.countDown();
                    if (frame.getMessageType() == MessageType.CHAT_MESSAGE) {
                        try {
                            connection.send(ProtocolFrame.of(MessageType.ACK_DELIVERED, 100L, "ack-ok".getBytes(StandardCharsets.UTF_8)));
                        } catch (IOException e) {
                            fail("Server failed to send ACK_DELIVERED: " + e.getMessage());
                        }
                    }
                }

                @Override
                public void onDisconnected(PeerConnection connection, Throwable cause) {}
            });
        });
        server.start();

        // Connect client
        PeerConnectionListener clientListener = new PeerConnectionListener() {
            @Override
            public void onFrameReceived(PeerConnection connection, ProtocolFrame frame) {
                clientReceivedFrame.set(frame);
                clientReceivedLatch.countDown();
            }

            @Override
            public void onDisconnected(PeerConnection connection, Throwable cause) {}
        };

        PeerConnection clientConn = PeerClient.connect("127.0.0.1", server.getPort(), clientListener);
        connectionsToClose.add(clientConn);

        assertTrue(clientConn.isConnected());

        // Send a frame from client to server
        byte[] payload = "Hello from Client".getBytes(StandardCharsets.UTF_8);
        ProtocolFrame clientFrame = clientConn.sendFrame(MessageType.CHAT_MESSAGE, payload);

        // Await server receiving frame
        assertTrue(serverReceivedLatch.await(5, TimeUnit.SECONDS), "Server did not receive frame within timeout");
        assertNotNull(serverReceivedFrame.get());
        assertEquals(MessageType.CHAT_MESSAGE, serverReceivedFrame.get().getMessageType());
        assertEquals(clientFrame.getSequenceNumber(), serverReceivedFrame.get().getSequenceNumber());
        assertArrayEquals(payload, serverReceivedFrame.get().getPayload());

        // Await client receiving ACK response from server
        assertTrue(clientReceivedLatch.await(5, TimeUnit.SECONDS), "Client did not receive ACK frame within timeout");
        assertNotNull(clientReceivedFrame.get());
        assertEquals(MessageType.ACK_DELIVERED, clientReceivedFrame.get().getMessageType());
    }

    @Test
    void testMultipleConcurrentPeers() throws Exception {
        int peerCount = 5;
        CountDownLatch allConnectedLatch = new CountDownLatch(peerCount);
        CountDownLatch allFramesReceivedOnServer = new CountDownLatch(peerCount);
        List<ProtocolFrame> receivedOnServer = new CopyOnWriteArrayList<>();

        server = new PeerServer("127.0.0.1", 0, (srv, serverSideConn) -> {
            connectionsToClose.add(serverSideConn);
            serverSideConn.addListener(new PeerConnectionListener() {
                @Override
                public void onFrameReceived(PeerConnection connection, ProtocolFrame frame) {
                    receivedOnServer.add(frame);
                    allFramesReceivedOnServer.countDown();
                }

                @Override
                public void onDisconnected(PeerConnection connection, Throwable cause) {}
            });
            allConnectedLatch.countDown();
        });
        server.start();

        List<PeerConnection> clientConnections = new ArrayList<>();
        for (int i = 0; i < peerCount; i++) {
            PeerConnection conn = PeerClient.connect("127.0.0.1", server.getPort(), new PeerConnectionListener() {
                @Override
                public void onFrameReceived(PeerConnection connection, ProtocolFrame frame) {}

                @Override
                public void onDisconnected(PeerConnection connection, Throwable cause) {}
            });
            clientConnections.add(conn);
            connectionsToClose.add(conn);
        }

        assertTrue(allConnectedLatch.await(5, TimeUnit.SECONDS), "All peers should connect successfully");
        assertEquals(peerCount, server.getActiveConnectionCount());

        // Send frame from each client concurrently
        for (int i = 0; i < peerCount; i++) {
            PeerConnection conn = clientConnections.get(i);
            conn.sendFrame(MessageType.PING, ("peer-" + i).getBytes(StandardCharsets.UTF_8));
        }

        assertTrue(allFramesReceivedOnServer.await(5, TimeUnit.SECONDS), "Server should receive frames from all peers");
        assertEquals(peerCount, receivedOnServer.size());

        // Close all cleanly
        for (PeerConnection conn : clientConnections) {
            conn.close();
            assertFalse(conn.isConnected());
        }
    }

    @Test
    void testOrderlyDisconnectNotification() throws Exception {
        CountDownLatch clientDisconnectLatch = new CountDownLatch(1);
        CountDownLatch serverDisconnectLatch = new CountDownLatch(1);

        server = new PeerServer("127.0.0.1", 0, (srv, serverSideConn) -> {
            connectionsToClose.add(serverSideConn);
            serverSideConn.addListener(new PeerConnectionListener() {
                @Override
                public void onFrameReceived(PeerConnection connection, ProtocolFrame frame) {}

                @Override
                public void onDisconnected(PeerConnection connection, Throwable cause) {
                    serverDisconnectLatch.countDown();
                }
            });
        });
        server.start();

        PeerConnection clientConn = PeerClient.connect("127.0.0.1", server.getPort(), new PeerConnectionListener() {
            @Override
            public void onFrameReceived(PeerConnection connection, ProtocolFrame frame) {}

            @Override
            public void onDisconnected(PeerConnection connection, Throwable cause) {
                clientDisconnectLatch.countDown();
            }
        });
        connectionsToClose.add(clientConn);

        assertTrue(clientConn.isConnected());

        // Close from client end
        clientConn.close();

        assertTrue(clientDisconnectLatch.await(5, TimeUnit.SECONDS), "Client listener should be notified of disconnect");
        assertTrue(serverDisconnectLatch.await(5, TimeUnit.SECONDS), "Server listener should be notified of peer disconnect");
        assertFalse(clientConn.isConnected());
    }
}
