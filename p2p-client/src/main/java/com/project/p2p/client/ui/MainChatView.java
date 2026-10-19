package com.project.p2p.client.ui;

import com.project.p2p.client.crypto.ClientKeyManager;
import com.project.p2p.client.crypto.TrustStore;
import com.project.p2p.client.model.OnlinePeer;
import com.project.p2p.client.network.EncryptedPeerSession;
import com.project.p2p.client.network.PeerNetworkService;
import com.project.p2p.client.network.SessionState;
import com.project.p2p.client.service.CoordinationServiceClient;
import com.project.p2p.common.crypto.CryptoPrimitives;
import com.project.p2p.common.network.MessageDeliveryState;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main P2P Chat Application Window.
 * Coordinates sidebar peer discovery, real-time encrypted messaging,
 * delivery/read state tracking, and security diagnostics.
 */
public class MainChatView extends BorderPane {

    private final String localUsername;
    private final CoordinationServiceClient coordinationClient;
    private final ClientKeyManager keyManager;
    private final TrustStore trustStore;
    private final PeerNetworkService peerNetworkService;

    // Contacts & Peer Discovery
    private final ObservableList<OnlinePeer> onlinePeersList = FXCollections.observableArrayList();
    private final ListView<OnlinePeer> peerListView = new ListView<>(onlinePeersList);
    private final Map<String, ObservableList<ChatMessageModel>> conversationHistory = new ConcurrentHashMap<>();

    // Active Chat State
    private String activePeerUsername;
    private final ObservableList<ChatMessageModel> activeMessages = FXCollections.observableArrayList();
    private final ListView<ChatMessageModel> messageListView = new ListView<>(activeMessages);

    // Chat Area Components
    private final Label activePeerHeaderLabel = new Label("Select a peer to chat");
    private final Label activePeerStatusLabel = new Label("No active session");
    private final TextField messageInputField = new TextField();
    private final Button sendButton = new Button("Send");

    // Right Sidebar: Security & Network Diagnostics
    private final PeerInfoPanel peerInfoPanel;

    // Heartbeat & Discovery Scheduler
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Discovery-Scheduler");
        t.setDaemon(true);
        return t;
    });

    private String presenceSessionId;

    public MainChatView(
            String localUsername,
            CoordinationServiceClient coordinationClient,
            ClientKeyManager keyManager,
            TrustStore trustStore,
            PeerNetworkService peerNetworkService,
            String presenceSessionId) {

        this.localUsername = localUsername.trim().toLowerCase();
        this.coordinationClient = coordinationClient;
        this.keyManager = keyManager;
        this.trustStore = trustStore;
        this.peerNetworkService = peerNetworkService;
        this.presenceSessionId = presenceSessionId;
        this.peerInfoPanel = new PeerInfoPanel(trustStore);

        initUi();
        initNetworkingHooks();
        startPeriodicDiscovery();
    }

    private void initUi() {
        setLeft(buildSidebar());
        setCenter(buildChatArea());
        setRight(peerInfoPanel);
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(12));
        sidebar.setPrefWidth(220);
        sidebar.setStyle("-fx-background-color: #0f172a;");

        Label myProfileLabel = new Label("Logged in as: " + localUsername);
        myProfileLabel.setStyle("-fx-text-fill: #38bdf8; -fx-font-weight: bold; -fx-font-size: 13px;");

        Label portLabel = new Label("Listening Port: " + peerNetworkService.getListeningPort());
        portLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        Label contactsTitle = new Label("ONLINE PEERS");
        contactsTitle.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 10 0 0 0;");

        peerListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(OnlinePeer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    Label name = new Label(item.username());
                    name.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
                    Label addr = new Label(item.observedIp() + ":" + item.listeningPort());
                    addr.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10px;");

                    VBox box = new VBox(2, name, addr);
                    box.setPadding(new Insets(4, 0, 4, 0));
                    setGraphic(box);
                    setStyle("-fx-background-color: transparent;");
                }
            }
        });

        peerListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectPeer(newVal);
            }
        });

        Button refreshBtn = new Button("↻ Refresh Peers");
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        refreshBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #cbd5e1; -fx-font-size: 11px;");
        refreshBtn.setOnAction(e -> refreshOnlinePeers());

        VBox.setVgrow(peerListView, Priority.ALWAYS);
        sidebar.getChildren().addAll(myProfileLabel, portLabel, new Separator(), contactsTitle, peerListView, refreshBtn);
        return sidebar;
    }

    private VBox buildChatArea() {
        VBox chatArea = new VBox();
        chatArea.setStyle("-fx-background-color: #f8fafc;");

        // Top Header
        VBox headerBox = new VBox(2, activePeerHeaderLabel, activePeerStatusLabel);
        headerBox.setPadding(new Insets(12, 16, 12, 16));
        headerBox.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        activePeerHeaderLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        activePeerStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        // Message List
        messageListView.setCellFactory(lv -> new ChatMessageCell());
        messageListView.setStyle("-fx-background-color: #ffffff; -fx-border-width: 0;");
        VBox.setVgrow(messageListView, Priority.ALWAYS);

        // Input Area
        messageInputField.setPromptText("Type an end-to-end encrypted message...");
        messageInputField.setStyle("-fx-padding: 8 12; -fx-background-radius: 6; -fx-border-color: #cbd5e1; -fx-border-radius: 6;");
        HBox.setHgrow(messageInputField, Priority.ALWAYS);

        sendButton.setDefaultButton(true);
        sendButton.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 18; -fx-background-radius: 6;");
        sendButton.setOnAction(e -> handleSendMessage());
        sendButton.setDisable(true);
        messageInputField.setDisable(true);

        HBox inputBar = new HBox(10, messageInputField, sendButton);
        inputBar.setPadding(new Insets(12, 16, 12, 16));
        inputBar.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1 0 0 0;");
        inputBar.setAlignment(Pos.CENTER);

        chatArea.getChildren().addAll(headerBox, messageListView, inputBar);
        return chatArea;
    }

    private void initNetworkingHooks() {
        peerNetworkService.addEventListener(new PeerNetworkService.NetworkEventListener() {
            @Override
            public void onSessionStateChanged(EncryptedPeerSession session, SessionState oldState, SessionState newState) {
                Platform.runLater(() -> {
                    String peerUser = session.getPeerUsername();
                    if (peerUser != null && peerUser.equalsIgnoreCase(activePeerUsername)) {
                        switch (newState) {
                            case CONNECTING -> activePeerStatusLabel.setText("Connecting to peer...");
                            case HANDSHAKING -> activePeerStatusLabel.setText("Verifying cryptographic handshake...");
                            case ESTABLISHED -> {
                                activePeerStatusLabel.setText("Encrypted Session Active (AES-256-GCM)");
                                sendButton.setDisable(false);
                                messageInputField.setDisable(false);
                            }
                            case FAILED -> {
                                activePeerStatusLabel.setText("Handshake Failed");
                                sendButton.setDisable(true);
                            }
                            case CLOSED -> {
                                EncryptedPeerSession active = peerNetworkService.getSession(peerUser);
                                if (active == null || !active.isSecureEstablished()) {
                                    activePeerStatusLabel.setText("Peer disconnected");
                                    sendButton.setDisable(true);
                                    messageInputField.setDisable(true);
                                }
                            }
                        }
                        peerInfoPanel.updatePeer(peerUser, session.getPeerFingerprint(), session);
                    }
                });
            }

            @Override
            public void onSessionEstablished(EncryptedPeerSession session) {
                Platform.runLater(() -> {
                    String peerUser = session.getPeerUsername();
                    if (peerUser != null && peerUser.equalsIgnoreCase(activePeerUsername)) {
                        activePeerStatusLabel.setText("Encrypted Session Active (AES-256-GCM)");
                        sendButton.setDisable(false);
                        messageInputField.setDisable(false);
                        peerInfoPanel.updatePeer(peerUser, session.getPeerFingerprint(), session);
                    }
                });
            }

            @Override
            public void onMessageReceived(EncryptedPeerSession session, UUID messageId, String text, long timestamp) {
                Platform.runLater(() -> {
                    String peerUser = session.getPeerUsername();
                    if (peerUser == null) return;

                    ChatMessageModel model = new ChatMessageModel(
                            messageId, peerUser, text, timestamp, false, MessageDeliveryState.DELIVERED
                    );
                    appendMessage(peerUser, model);
                    session.sendReadAck(messageId);

                    if (peerUser.equalsIgnoreCase(activePeerUsername)) {
                        peerInfoPanel.updatePeer(peerUser, session.getPeerFingerprint(), session);
                    }
                });
            }

            @Override
            public void onDeliveryAckReceived(EncryptedPeerSession session, UUID messageId) {
                Platform.runLater(() -> {
                    String peerUser = session.getPeerUsername();
                    if (peerUser != null) {
                        updateMessageState(peerUser, messageId, MessageDeliveryState.DELIVERED);
                        if (peerUser.equalsIgnoreCase(activePeerUsername)) {
                            peerInfoPanel.updatePeer(peerUser, session.getPeerFingerprint(), session);
                        }
                    }
                });
            }

            @Override
            public void onReadAckReceived(EncryptedPeerSession session, UUID messageId) {
                Platform.runLater(() -> {
                    String peerUser = session.getPeerUsername();
                    if (peerUser != null) {
                        updateMessageState(peerUser, messageId, MessageDeliveryState.READ);
                        if (peerUser.equalsIgnoreCase(activePeerUsername)) {
                            peerInfoPanel.updatePeer(peerUser, session.getPeerFingerprint(), session);
                        }
                    }
                });
            }

            @Override
            public void onSessionClosed(EncryptedPeerSession session, Throwable cause) {
                Platform.runLater(() -> {
                    String peerUser = session.getPeerUsername();
                    if (peerUser != null && peerUser.equalsIgnoreCase(activePeerUsername)) {
                        EncryptedPeerSession active = peerNetworkService.getSession(peerUser);
                        if (active == null || !active.isSecureEstablished()) {
                            activePeerStatusLabel.setText("Peer disconnected");
                            sendButton.setDisable(true);
                            messageInputField.setDisable(true);
                            peerInfoPanel.updatePeer(peerUser, session.getPeerFingerprint(), null);
                        }
                    }
                });
            }
        });
    }

    private void selectPeer(OnlinePeer peer) {
        if (peer.username().equalsIgnoreCase(localUsername)) {
            activePeerHeaderLabel.setText(peer.username() + " (Self)");
            activePeerStatusLabel.setText("Cannot message yourself");
            sendButton.setDisable(true);
            messageInputField.setDisable(true);
            peerInfoPanel.updatePeer(peer.username(), null, null);
            return;
        }

        this.activePeerUsername = peer.username().trim().toLowerCase();
        activePeerHeaderLabel.setText("Chat with " + peer.username());

        // Switch message list to selected peer
        ObservableList<ChatMessageModel> history = conversationHistory.computeIfAbsent(
                peer.username().toLowerCase(), k -> FXCollections.observableArrayList()
        );
        activeMessages.setAll(history);

        // Check if existing session is already established
        EncryptedPeerSession existing = peerNetworkService.getSession(peer.username());
        if (existing != null && existing.isSecureEstablished() && existing.getConnection().isConnected()) {
            activePeerStatusLabel.setText("Encrypted Session Active (AES-256-GCM)");
            sendButton.setDisable(false);
            messageInputField.setDisable(false);
            peerInfoPanel.updatePeer(peer.username(), existing.getPeerFingerprint(), existing);
            return;
        }

        // If existing connection is underway, await completion
        if (existing != null && (existing.getState() == SessionState.CONNECTING || existing.getState() == SessionState.HANDSHAKING)) {
            activePeerStatusLabel.setText("Connecting & verifying handshake...");
            sendButton.setDisable(true);
            messageInputField.setDisable(true);
            peerInfoPanel.updatePeer(peer.username(), null, existing);
            return;
        }

        // Establish session asynchronously
        activePeerStatusLabel.setText("Connecting & verifying handshake...");
        sendButton.setDisable(true);
        messageInputField.setDisable(true);
        peerInfoPanel.updatePeer(peer.username(), null, null);

        PublicKey peerIdentityKey = null;
        try {
            if (peer.identityPublicKey() != null) {
                peerIdentityKey = CryptoPrimitives.decodeEd25519PublicKey(peer.identityPublicKey());
            }
        } catch (Exception ignored) {}

        peerNetworkService.connectToPeerAsync(
                peer.username(),
                peer.observedIp(),
                peer.listeningPort(),
                peerIdentityKey,
                null
        ).whenComplete((session, error) -> {
            Platform.runLater(() -> {
                if (peer.username().equalsIgnoreCase(activePeerUsername)) {
                    if (error != null) {
                        activePeerStatusLabel.setText("Connection failed: " + error.getMessage());
                        peerInfoPanel.updatePeer(peer.username(), null, null);
                    } else if (session != null && session.isSecureEstablished()) {
                        activePeerStatusLabel.setText("Encrypted Session Active (AES-256-GCM)");
                        sendButton.setDisable(false);
                        messageInputField.setDisable(false);
                        peerInfoPanel.updatePeer(peer.username(), session.getPeerFingerprint(), session);
                    }
                }
            });
        });
    }

    private void handleSendMessage() {
        String text = messageInputField.getText().trim();
        if (text.isEmpty() || activePeerUsername == null) {
            return;
        }

        EncryptedPeerSession session = peerNetworkService.getSession(activePeerUsername);
        if (session == null || !session.isSecureEstablished()) {
            activePeerStatusLabel.setText("Cannot send: Session not established");
            return;
        }

        messageInputField.clear();
        UUID tempId = UUID.randomUUID();
        long now = System.currentTimeMillis();

        ChatMessageModel msgModel = new ChatMessageModel(
                tempId, localUsername, text, now, true, MessageDeliveryState.PENDING
        );
        appendMessage(activePeerUsername, msgModel);

        session.sendChatMessage(text).whenComplete((sentId, error) -> {
            Platform.runLater(() -> {
                if (error != null) {
                    msgModel.setDeliveryState(MessageDeliveryState.FAILED);
                } else {
                    msgModel.setDeliveryState(MessageDeliveryState.SENT);
                }
                peerInfoPanel.updatePeer(activePeerUsername, session.getPeerFingerprint(), session);
            });
        });
    }

    private void appendMessage(String peerUser, ChatMessageModel msg) {
        ObservableList<ChatMessageModel> history = conversationHistory.computeIfAbsent(
                peerUser.toLowerCase(), k -> FXCollections.observableArrayList()
        );
        history.add(msg);
        if (peerUser.equalsIgnoreCase(activePeerUsername)) {
            activeMessages.add(msg);
            messageListView.scrollTo(activeMessages.size() - 1);
        }
    }

    private void updateMessageState(String peerUser, UUID messageId, MessageDeliveryState newState) {
        ObservableList<ChatMessageModel> history = conversationHistory.get(peerUser.toLowerCase());
        if (history != null) {
            for (ChatMessageModel msg : history) {
                if (msg.getMessageId().equals(messageId)) {
                    msg.setDeliveryState(newState);
                    break;
                }
            }
        }
    }

    private void startPeriodicDiscovery() {
        // Immediate fetch
        refreshOnlinePeers();

        // Heartbeat every 20 seconds, peer discovery every 10 seconds
        scheduler.scheduleAtFixedRate(() -> {
            if (presenceSessionId != null) {
                coordinationClient.sendHeartbeatAsync(presenceSessionId);
            }
            refreshOnlinePeers();
        }, 10, 10, TimeUnit.SECONDS);
    }

    private void refreshOnlinePeers() {
        coordinationClient.getOnlinePeersAsync().whenComplete((peers, error) -> {
            if (error == null && peers != null) {
                Platform.runLater(() -> {
                    onlinePeersList.setAll(peers);
                    for (OnlinePeer p : peers) {
                        if (p.identityPublicKey() != null) {
                            try {
                                PublicKey pub = CryptoPrimitives.decodeEd25519PublicKey(p.identityPublicKey());
                                peerNetworkService.registerPeerPublicKey(p.username(), pub);
                            } catch (Exception ignored) {}
                        }
                    }
                });
            }
        });
    }

    public void cleanup() {
        scheduler.shutdownNow();
        if (presenceSessionId != null) {
            coordinationClient.disconnectPresenceAsync(presenceSessionId);
        }
        peerNetworkService.close();
    }
}
