package com.project.p2p.client.ui;

import com.project.p2p.client.crypto.ClientKeyManager;
import com.project.p2p.client.crypto.TrustStore;
import com.project.p2p.client.model.OnlinePeer;
import com.project.p2p.client.network.EncryptedPeerSession;
import com.project.p2p.client.network.PeerNetworkService;
import com.project.p2p.client.network.SessionState;
import com.project.p2p.client.service.CoordinationServiceClient;
import com.project.p2p.client.ui.common.AppIcons;
import com.project.p2p.client.ui.common.AvatarView;
import com.project.p2p.client.ui.common.ThemeManager;
import com.project.p2p.common.crypto.CryptoPrimitives;
import com.project.p2p.common.network.MessageDeliveryState;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main P2P Chat Application Window.
 * GitHub Primer-inspired 3-pane adaptive layout coordinating peer discovery,
 * end-to-end encrypted messaging, message delivery states, and cryptographic identity inspection.
 */
public class MainChatView extends BorderPane {

    private final String localUsername;
    private final CoordinationServiceClient coordinationClient;
    private final ClientKeyManager keyManager;
    private final TrustStore trustStore;
    private final PeerNetworkService peerNetworkService;

    // Contacts & Peer Discovery
    private final ObservableList<OnlinePeer> onlinePeersList = FXCollections.observableArrayList();
    private final FilteredList<OnlinePeer> filteredPeersList = new FilteredList<>(onlinePeersList, p -> true);
    private final ListView<OnlinePeer> peerListView = new ListView<>(filteredPeersList);
    private final Map<String, ObservableList<ChatMessageModel>> conversationHistory = new ConcurrentHashMap<>();

    // Active Chat State
    private String activePeerUsername;
    private final ObservableList<ChatMessageModel> activeMessages = FXCollections.observableArrayList();
    private final ListView<ChatMessageModel> messageListView = new ListView<>(activeMessages);

    // Chat Area Components
    private final HBox activePeerAvatarContainer = new HBox();
    private final Label activePeerHeaderLabel = new Label("Select a peer to chat");
    private final Label activePeerStatusBadge = new Label("No active conversation");
    private final TextField messageInputField = new TextField();
    private final Button sendButton = new Button("Send");
    private final Button toggleInspectorButton = new Button();
    private final StackPane chatCenterStack = new StackPane();
    private final VBox chatEmptyState = new VBox();

    // Right Sidebar: Security & Network Diagnostics
    private final PeerInfoPanel peerInfoPanel;
    private boolean inspectorVisible = true;

    // Heartbeat & Discovery Scheduler
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Discovery-Scheduler");
        t.setDaemon(true);
        return t;
    });

    private final String presenceSessionId;

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

        getStyleClass().add("app-window");

        initUi();
        initNetworkingHooks();
        startPeriodicDiscovery();
    }

    private void initUi() {
        setLeft(buildSidebar());
        setCenter(buildChatArea());
        setRight(peerInfoPanel);

        peerInfoPanel.setOnCloseRequested(this::toggleInspector);

        // Adaptive listener: collapse inspector if window gets narrow
        widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() < 940 && inspectorVisible) {
                setInspectorVisible(false);
            }
        });
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.getStyleClass().add("sidebar-container");
        sidebar.setPadding(new Insets(14));
        sidebar.setPrefWidth(260);
        sidebar.setMinWidth(240);
        sidebar.setMaxWidth(300);

        // App Brand Header with Theme Toggle
        Node shieldLogo = AppIcons.create(AppIcons.Icon.SHIELD, 20, Color.web("#2f81f7"));
        Label brandTitle = new Label("P2P Secure Chat");
        brandTitle.getStyleClass().add("brand-title");

        Button themeToggleBtn = new Button();
        themeToggleBtn.setGraphic(AppIcons.create(ThemeManager.isDarkMode() ? AppIcons.Icon.SUN : AppIcons.Icon.MOON, 14, Color.web("#8b949e")));
        themeToggleBtn.setStyle("-fx-background-color: transparent; -fx-padding: 4; -fx-cursor: hand;");
        themeToggleBtn.setTooltip(new Tooltip("Toggle Dark / Light Theme"));
        themeToggleBtn.setOnAction(e -> {
            ThemeManager.toggleTheme(getScene());
            themeToggleBtn.setGraphic(AppIcons.create(ThemeManager.isDarkMode() ? AppIcons.Icon.SUN : AppIcons.Icon.MOON, 14, Color.web("#8b949e")));
        });

        Region brandSpacer = new Region();
        HBox.setHgrow(brandSpacer, Priority.ALWAYS);
        HBox brandBar = new HBox(8, shieldLogo, brandTitle, brandSpacer, themeToggleBtn);
        brandBar.setAlignment(Pos.CENTER_LEFT);

        // Local User Profile Card
        AvatarView myAvatar = new AvatarView(localUsername, 32, true, true);
        Label myProfileLabel = new Label(localUsername);
        myProfileLabel.getStyleClass().add("user-profile-name");

        Label portBadge = new Label(":" + peerNetworkService.getListeningPort());
        portBadge.getStyleClass().add("listening-port-badge");
        Tooltip.install(portBadge, new Tooltip("P2P Listening Port: " + peerNetworkService.getListeningPort()));

        VBox userDetails = new VBox(2, myProfileLabel, portBadge);
        HBox userCard = new HBox(10, myAvatar, userDetails);
        userCard.getStyleClass().add("user-profile-card");
        userCard.setAlignment(Pos.CENTER_LEFT);

        // Peer Search Field
        TextField searchField = new TextField();
        searchField.setPromptText("Filter peers...");
        searchField.getStyleClass().add("peer-search-field");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                filteredPeersList.setPredicate(p -> true);
            } else {
                String filter = newVal.trim().toLowerCase();
                filteredPeersList.setPredicate(p -> p.username().toLowerCase().contains(filter));
            }
        });

        Label contactsTitle = new Label("ONLINE PEERS");
        contactsTitle.getStyleClass().add("sidebar-section-title");

        // Custom Peer List View
        peerListView.getStyleClass().add("peer-list-view");
        peerListView.setCellFactory(lv -> new PeerListCell(peerNetworkService));

        // Empty state when no peers online
        VBox emptyPeersState = new VBox(6);
        emptyPeersState.setAlignment(Pos.CENTER);
        emptyPeersState.setPadding(new Insets(24, 10, 24, 10));
        Node usersIcon = AppIcons.create(AppIcons.Icon.USERS, 28, Color.web("#6e7681"));
        Label emptyTitle = new Label("No Peers Online");
        emptyTitle.getStyleClass().add("empty-state-title");
        Label emptySubtitle = new Label("Peers running on your network will appear here automatically.");
        emptySubtitle.getStyleClass().add("empty-state-subtitle");
        emptySubtitle.setWrapText(true);
        emptyPeersState.getChildren().addAll(usersIcon, emptyTitle, emptySubtitle);
        peerListView.setPlaceholder(emptyPeersState);

        peerListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectPeer(newVal);
            }
        });

        // Refresh Peers Action
        Button refreshBtn = new Button("Refresh Peers");
        refreshBtn.setGraphic(AppIcons.create(AppIcons.Icon.REFRESH, 13, Color.web("#8b949e")));
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        refreshBtn.setStyle("-fx-background-color: -color-canvas-overlay; -fx-border-color: -color-border-default; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 11px; -fx-padding: 6 12; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> refreshOnlinePeers());

        VBox.setVgrow(peerListView, Priority.ALWAYS);
        sidebar.getChildren().addAll(brandBar, userCard, searchField, contactsTitle, peerListView, refreshBtn);
        return sidebar;
    }

    private VBox buildChatArea() {
        VBox chatArea = new VBox();
        chatArea.getStyleClass().add("chat-area-container");

        // Top Header
        activePeerHeaderLabel.getStyleClass().add("chat-header-title");
        activePeerStatusBadge.getStyleClass().add("status-badge-disconnected");

        VBox titleBox = new VBox(3, activePeerHeaderLabel, activePeerStatusBadge);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        HBox peerHeaderInfo = new HBox(12, activePeerAvatarContainer, titleBox);
        peerHeaderInfo.setAlignment(Pos.CENTER_LEFT);

        // Security Inspector Toggle Button
        toggleInspectorButton.setGraphic(AppIcons.create(AppIcons.Icon.SHIELD_CHECK, 16, Color.web("#2f81f7")));
        toggleInspectorButton.setText("Security");
        toggleInspectorButton.setStyle("-fx-background-color: -color-canvas-overlay; -fx-border-color: -color-border-default; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 6 12; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
        toggleInspectorButton.setTooltip(new Tooltip("Toggle Security & Identity Inspector"));
        toggleInspectorButton.setOnAction(e -> toggleInspector());

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        HBox headerBar = new HBox(peerHeaderInfo, headerSpacer, toggleInspectorButton);
        headerBar.getStyleClass().add("chat-header-bar");
        headerBar.setAlignment(Pos.CENTER_LEFT);

        // Message List
        messageListView.getStyleClass().add("chat-message-list");
        messageListView.setCellFactory(lv -> new ChatMessageCell());

        // Empty conversation splash
        chatEmptyState.setAlignment(Pos.CENTER);
        chatEmptyState.setPadding(new Insets(40));
        chatEmptyState.setSpacing(10);
        Node splashLock = AppIcons.create(AppIcons.Icon.LOCK, 36, Color.web("#6e7681"));
        Label splashTitle = new Label("End-to-End Encrypted Messaging");
        splashTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: -color-fg-muted;");
        Label splashSubtitle = new Label("Select a peer from the left sidebar to establish a secure peer-to-peer session.");
        splashSubtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-fg-subtle; -fx-text-alignment: center;");
        splashSubtitle.setWrapText(true);
        splashSubtitle.setMaxWidth(380);
        chatEmptyState.getChildren().addAll(splashLock, splashTitle, splashSubtitle);

        chatCenterStack.getChildren().addAll(messageListView, chatEmptyState);
        VBox.setVgrow(chatCenterStack, Priority.ALWAYS);

        // Input Composer Bar
        messageInputField.setPromptText("Type an end-to-end encrypted message (Enter to send)...");
        messageInputField.getStyleClass().add("chat-input-field");
        messageInputField.setOnAction(e -> handleSendMessage());
        HBox.setHgrow(messageInputField, Priority.ALWAYS);

        sendButton.setDefaultButton(true);
        sendButton.setGraphic(AppIcons.create(AppIcons.Icon.SEND, 14, Color.WHITE));
        sendButton.setStyle("-fx-background-color: -color-accent-emphasis; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 18; -fx-background-radius: 8; -fx-cursor: hand;");
        sendButton.setOnAction(e -> handleSendMessage());
        sendButton.setDisable(true);
        messageInputField.setDisable(true);

        HBox inputBar = new HBox(10, messageInputField, sendButton);
        inputBar.getStyleClass().add("chat-input-bar");
        inputBar.setAlignment(Pos.CENTER);

        chatArea.getChildren().addAll(headerBar, chatCenterStack, inputBar);
        return chatArea;
    }

    private void toggleInspector() {
        setInspectorVisible(!inspectorVisible);
    }

    private void setInspectorVisible(boolean visible) {
        this.inspectorVisible = visible;
        if (visible) {
            setRight(peerInfoPanel);
            toggleInspectorButton.setStyle("-fx-background-color: -color-accent-subtle; -fx-border-color: -color-accent-fg; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 6 12; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
        } else {
            setRight(null);
            toggleInspectorButton.setStyle("-fx-background-color: -color-canvas-overlay; -fx-border-color: -color-border-default; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 6 12; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
        }
    }

    private void initNetworkingHooks() {
        peerNetworkService.addEventListener(new PeerNetworkService.NetworkEventListener() {
            @Override
            public void onSessionStateChanged(EncryptedPeerSession session, SessionState oldState, SessionState newState) {
                Platform.runLater(() -> {
                    String peerUser = session.getPeerUsername();
                    if (peerUser != null && peerUser.equalsIgnoreCase(activePeerUsername)) {
                        updateSessionStatusUI(newState);
                        peerInfoPanel.updatePeer(peerUser, session.getPeerFingerprint(), session);
                    }
                    peerListView.refresh();
                });
            }

            @Override
            public void onSessionEstablished(EncryptedPeerSession session) {
                Platform.runLater(() -> {
                    String peerUser = session.getPeerUsername();
                    if (peerUser != null && peerUser.equalsIgnoreCase(activePeerUsername)) {
                        updateSessionStatusUI(SessionState.ESTABLISHED);
                        peerInfoPanel.updatePeer(peerUser, session.getPeerFingerprint(), session);
                    }
                    peerListView.refresh();
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
                            updateSessionStatusUI(SessionState.CLOSED);
                            peerInfoPanel.updatePeer(peerUser, session.getPeerFingerprint(), null);
                        }
                    }
                    peerListView.refresh();
                });
            }
        });
    }

    private void updateSessionStatusUI(SessionState state) {
        activePeerStatusBadge.getStyleClass().removeAll("status-badge-established", "status-badge-pending", "status-badge-disconnected");
        switch (state) {
            case CONNECTING -> {
                activePeerStatusBadge.setText("CONNECTING...");
                activePeerStatusBadge.getStyleClass().add("status-badge-pending");
                sendButton.setDisable(true);
                messageInputField.setDisable(true);
            }
            case HANDSHAKING -> {
                activePeerStatusBadge.setText("VERIFYING HANDSHAKE...");
                activePeerStatusBadge.getStyleClass().add("status-badge-pending");
                sendButton.setDisable(true);
                messageInputField.setDisable(true);
            }
            case ESTABLISHED -> {
                activePeerStatusBadge.setText("E2E ENCRYPTED (AES-256-GCM)");
                activePeerStatusBadge.getStyleClass().add("status-badge-established");
                sendButton.setDisable(false);
                messageInputField.setDisable(false);
            }
            case FAILED -> {
                activePeerStatusBadge.setText("HANDSHAKE FAILED");
                activePeerStatusBadge.getStyleClass().add("status-badge-disconnected");
                sendButton.setDisable(true);
                messageInputField.setDisable(true);
            }
            case CLOSED -> {
                activePeerStatusBadge.setText("PEER DISCONNECTED");
                activePeerStatusBadge.getStyleClass().add("status-badge-disconnected");
                sendButton.setDisable(true);
                messageInputField.setDisable(true);
            }
        }
    }

    private void selectPeer(OnlinePeer peer) {
        if (peer.username().equalsIgnoreCase(localUsername)) {
            activePeerHeaderLabel.setText(peer.username() + " (Self)");
            activePeerStatusBadge.setText("Cannot message yourself");
            activePeerStatusBadge.getStyleClass().removeAll("status-badge-established", "status-badge-pending", "status-badge-disconnected");
            activePeerStatusBadge.getStyleClass().add("status-badge-disconnected");
            activePeerAvatarContainer.getChildren().setAll(new AvatarView(peer.username(), 36));
            sendButton.setDisable(true);
            messageInputField.setDisable(true);
            chatEmptyState.setVisible(true);
            peerInfoPanel.updatePeer(peer.username(), null, null);
            return;
        }

        this.activePeerUsername = peer.username().trim().toLowerCase();
        activePeerHeaderLabel.setText(peer.username());
        activePeerAvatarContainer.getChildren().setAll(new AvatarView(peer.username(), 36, true, true));

        // Switch message list to selected peer
        ObservableList<ChatMessageModel> history = conversationHistory.computeIfAbsent(
                peer.username().toLowerCase(), k -> FXCollections.observableArrayList()
        );
        activeMessages.setAll(history);
        chatEmptyState.setVisible(activeMessages.isEmpty());

        // Check if existing session is already established
        EncryptedPeerSession existing = peerNetworkService.getSession(peer.username());
        if (existing != null && existing.isSecureEstablished() && existing.getConnection().isConnected()) {
            updateSessionStatusUI(SessionState.ESTABLISHED);
            peerInfoPanel.updatePeer(peer.username(), existing.getPeerFingerprint(), existing);
            return;
        }

        // If existing connection is underway, await completion
        if (existing != null && (existing.getState() == SessionState.CONNECTING || existing.getState() == SessionState.HANDSHAKING)) {
            updateSessionStatusUI(existing.getState());
            peerInfoPanel.updatePeer(peer.username(), null, existing);
            return;
        }

        // Establish session asynchronously
        updateSessionStatusUI(SessionState.CONNECTING);
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
                        activePeerStatusBadge.setText("CONNECTION FAILED");
                        activePeerStatusBadge.getStyleClass().removeAll("status-badge-established", "status-badge-pending", "status-badge-disconnected");
                        activePeerStatusBadge.getStyleClass().add("status-badge-disconnected");
                        peerInfoPanel.updatePeer(peer.username(), null, null);
                    } else if (session != null && session.isSecureEstablished()) {
                        updateSessionStatusUI(SessionState.ESTABLISHED);
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
            activePeerStatusBadge.setText("CANNOT SEND: SESSION NOT ESTABLISHED");
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
            chatEmptyState.setVisible(false);
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
        refreshOnlinePeers();

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
