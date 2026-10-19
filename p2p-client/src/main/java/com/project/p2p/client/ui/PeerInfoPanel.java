package com.project.p2p.client.ui;

import com.project.p2p.client.crypto.TrustStore;
import com.project.p2p.client.network.EncryptedPeerSession;
import com.project.p2p.client.ui.common.AppIcons;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * Security and Identity Inspector Panel.
 * Communicates real cryptographic identity verification, TOFU trust state,
 * ephemeral key agreement, and live socket network telemetry.
 */
public class PeerInfoPanel extends VBox {

    private final Label peerNameLabel;
    private final Label trustStatusBadge;
    private final Label trustExplanationLabel;
    private final Label fingerprintLabel;
    private final Button copyFingerprintBtn;
    private final Button trustButton;

    // Cryptographic Session Details
    private final Label sessionStatusLabel;
    private final Label cipherSuiteLabel;
    private final Label keyAgreementLabel;
    private final Label sessionRoleLabel;

    // Network Telemetry Details
    private final Label localEndpointLabel;
    private final Label remoteEndpointLabel;
    private final Label framesSentLabel;
    private final Label framesRecvLabel;

    private String currentPeerUser;
    private String currentFingerprint;
    private final TrustStore trustStore;
    private Consumer<String> onTrustChanged;
    private Runnable onCloseRequested;

    public PeerInfoPanel(TrustStore trustStore) {
        this.trustStore = trustStore;

        getStyleClass().add("inspector-container");
        setPrefWidth(320);
        setMinWidth(280);
        setMaxWidth(380);
        setSpacing(14);

        // Header with title and close button
        Label headerTitle = new Label("SECURITY & IDENTITY");
        headerTitle.getStyleClass().add("inspector-header-title");

        Button closeBtn = new Button();
        closeBtn.setGraphic(AppIcons.create(AppIcons.Icon.CLOSE, 14, Color.web("#8b949e")));
        closeBtn.setStyle("-fx-background-color: transparent; -fx-padding: 4; -fx-cursor: hand;");
        closeBtn.setTooltip(new Tooltip("Hide Security Inspector"));
        closeBtn.setOnAction(e -> {
            if (onCloseRequested != null) {
                onCloseRequested.run();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBar = new HBox(headerTitle, spacer, closeBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);

        // --- Card 1: Peer Identity & TOFU ---
        Label identityCardTitle = new Label("PEER IDENTITY");
        identityCardTitle.getStyleClass().add("inspector-card-title");

        peerNameLabel = new Label("No Peer Selected");
        peerNameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: -color-fg-default;");

        trustStatusBadge = new Label("UNVERIFIED");
        trustStatusBadge.getStyleClass().add("trust-badge-unverified");

        HBox peerIdentityRow = new HBox(8, peerNameLabel, trustStatusBadge);
        peerIdentityRow.setAlignment(Pos.CENTER_LEFT);

        trustExplanationLabel = new Label("Identity key has not yet been pinned in local trust store.");
        trustExplanationLabel.setWrapText(true);
        trustExplanationLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");

        Label fpTitle = new Label("Ed25519 Identity Fingerprint (SHA-256):");
        fpTitle.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-subtle; -fx-padding: 4 0 0 0;");

        fingerprintLabel = new Label("None");
        fingerprintLabel.setWrapText(true);
        fingerprintLabel.getStyleClass().add("fingerprint-box");
        fingerprintLabel.setMaxWidth(Double.MAX_VALUE);

        copyFingerprintBtn = new Button("Copy Fingerprint");
        copyFingerprintBtn.setGraphic(AppIcons.create(AppIcons.Icon.COPY, 13, Color.web("#8b949e")));
        copyFingerprintBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10; -fx-cursor: hand;");
        copyFingerprintBtn.setOnAction(e -> handleCopyFingerprint());
        copyFingerprintBtn.setDisable(true);

        trustButton = new Button("Trust Peer Key (TOFU)");
        trustButton.setGraphic(AppIcons.create(AppIcons.Icon.SHIELD_CHECK, 14, Color.WHITE));
        trustButton.setMaxWidth(Double.MAX_VALUE);
        trustButton.setStyle("-fx-background-color: -color-accent-emphasis; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 7 14; -fx-background-radius: 6;");
        trustButton.setOnAction(e -> handleTrustClicked());
        trustButton.setDisable(true);

        VBox identityCard = new VBox(8,
                identityCardTitle,
                peerIdentityRow,
                trustExplanationLabel,
                fpTitle,
                fingerprintLabel,
                copyFingerprintBtn,
                trustButton
        );
        identityCard.getStyleClass().add("inspector-card");

        // --- Card 2: Cryptographic Session ---
        Label cryptoCardTitle = new Label("ENCRYPTED SESSION");
        cryptoCardTitle.getStyleClass().add("inspector-card-title");

        sessionStatusLabel = createTelemetryRowLabel("Disconnected");
        cipherSuiteLabel = createTelemetryRowLabel("AES-256-GCM (256-bit)");
        keyAgreementLabel = createTelemetryRowLabel("X25519 (ECDH)");
        sessionRoleLabel = createTelemetryRowLabel("None");

        GridPane cryptoGrid = new GridPane();
        cryptoGrid.setHgap(8);
        cryptoGrid.setVgap(6);
        addGridRow(cryptoGrid, 0, "Session:", sessionStatusLabel);
        addGridRow(cryptoGrid, 1, "Cipher:", cipherSuiteLabel);
        addGridRow(cryptoGrid, 2, "Key Exchange:", keyAgreementLabel);
        addGridRow(cryptoGrid, 3, "Role:", sessionRoleLabel);

        VBox cryptoCard = new VBox(8, cryptoCardTitle, cryptoGrid);
        cryptoCard.getStyleClass().add("inspector-card");

        // --- Card 3: Network Diagnostics ---
        Label networkCardTitle = new Label("NETWORK TELEMETRY");
        networkCardTitle.getStyleClass().add("inspector-card-title");

        localEndpointLabel = createTelemetryRowLabel("None");
        remoteEndpointLabel = createTelemetryRowLabel("None");
        framesSentLabel = createTelemetryRowLabel("0");
        framesRecvLabel = createTelemetryRowLabel("0");

        GridPane networkGrid = new GridPane();
        networkGrid.setHgap(8);
        networkGrid.setVgap(6);
        addGridRow(networkGrid, 0, "Local:", localEndpointLabel);
        addGridRow(networkGrid, 1, "Remote:", remoteEndpointLabel);
        addGridRow(networkGrid, 2, "Frames Sent:", framesSentLabel);
        addGridRow(networkGrid, 3, "Frames Recv:", framesRecvLabel);

        VBox networkCard = new VBox(8, networkCardTitle, networkGrid);
        networkCard.getStyleClass().add("inspector-card");

        VBox contentBox = new VBox(12, topBar, identityCard, cryptoCard, networkCard);
        contentBox.setFillWidth(true);

        ScrollPane scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().add(scrollPane);
    }

    private void addGridRow(GridPane grid, int row, String labelText, Label valueLabel) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("telemetry-row-label");
        grid.add(lbl, 0, row);
        grid.add(valueLabel, 1, row);
    }

    private Label createTelemetryRowLabel(String defaultText) {
        Label lbl = new Label(defaultText);
        lbl.getStyleClass().add("telemetry-row-value");
        lbl.setWrapText(true);
        return lbl;
    }

    public void setOnTrustChanged(Consumer<String> callback) {
        this.onTrustChanged = callback;
    }

    public void setOnCloseRequested(Runnable callback) {
        this.onCloseRequested = callback;
    }

    public void updatePeer(String username, String fingerprint, EncryptedPeerSession session) {
        this.currentPeerUser = username;
        this.currentFingerprint = fingerprint;

        if (username == null) {
            peerNameLabel.setText("No Peer Selected");
            fingerprintLabel.setText("None");
            copyFingerprintBtn.setDisable(true);
            trustButton.setDisable(true);
            updateTrustState(false, false);
            sessionStatusLabel.setText("Disconnected");
            sessionRoleLabel.setText("None");
            localEndpointLabel.setText("None");
            remoteEndpointLabel.setText("None");
            framesSentLabel.setText("0");
            framesRecvLabel.setText("0");
            return;
        }

        peerNameLabel.setText(username);
        fingerprintLabel.setText(fingerprint != null ? formatFingerprint(fingerprint) : "Awaiting handshake...");
        copyFingerprintBtn.setDisable(fingerprint == null);

        boolean isTrusted = (fingerprint != null) && trustStore.isTrusted(username, fingerprint);
        boolean hasChanged = (fingerprint != null) && trustStore.hasKeyChanged(username, fingerprint);

        updateTrustState(isTrusted, hasChanged);
        trustButton.setDisable(fingerprint == null || isTrusted);

        if (session != null && session.getConnection().isConnected()) {
            String roleStr = session.isInitiator() ? "Initiator (Outbound)" : "Responder (Inbound)";
            sessionRoleLabel.setText(roleStr);

            String localEp = String.valueOf(session.getConnection().getLocalAddress());
            String remoteEp = String.valueOf(session.getConnection().getRemoteAddress());
            localEndpointLabel.setText(cleanEndpoint(localEp));
            remoteEndpointLabel.setText(cleanEndpoint(remoteEp));

            if (session.isSecureEstablished()) {
                sessionStatusLabel.setText("Connected & Encrypted");
                sessionStatusLabel.setStyle("-fx-text-fill: -color-success-fg; -fx-font-weight: bold;");
            } else {
                sessionStatusLabel.setText(session.getState().toString());
                sessionStatusLabel.setStyle("-fx-text-fill: -color-warning-fg; -fx-font-weight: bold;");
            }

            framesSentLabel.setText(String.valueOf(session.getEncryptedFramesSent()));
            framesRecvLabel.setText(String.valueOf(session.getEncryptedFramesReceived()));
        } else if (session != null) {
            sessionStatusLabel.setText(session.getState().toString());
            sessionStatusLabel.setStyle("-fx-text-fill: -color-fg-muted;");
            framesSentLabel.setText(String.valueOf(session.getEncryptedFramesSent()));
            framesRecvLabel.setText(String.valueOf(session.getEncryptedFramesReceived()));
        } else {
            sessionStatusLabel.setText("Disconnected");
            sessionStatusLabel.setStyle("-fx-text-fill: -color-fg-muted;");
            sessionRoleLabel.setText("None");
            localEndpointLabel.setText("None");
            remoteEndpointLabel.setText("None");
            framesSentLabel.setText("0");
            framesRecvLabel.setText("0");
        }
    }

    private void updateTrustState(boolean trusted, boolean changed) {
        trustStatusBadge.getStyleClass().removeAll("trust-badge-verified", "trust-badge-unverified", "trust-badge-danger");
        if (changed) {
            trustStatusBadge.setText("KEY MISMATCH (MITM ALERT)");
            trustStatusBadge.getStyleClass().add("trust-badge-danger");
            trustExplanationLabel.setText("CRITICAL: Peer's Ed25519 identity key differs from previously trusted key!");
            trustExplanationLabel.setStyle("-fx-text-fill: -color-danger-fg; -fx-font-size: 11px; -fx-font-weight: bold;");
        } else if (trusted) {
            trustStatusBadge.setText("VERIFIED (TOFU)");
            trustStatusBadge.getStyleClass().add("trust-badge-verified");
            trustExplanationLabel.setText("Peer identity is trusted. Key matches recorded Trust-on-First-Use fingerprint.");
            trustExplanationLabel.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");
        } else {
            trustStatusBadge.setText("UNVERIFIED");
            trustStatusBadge.getStyleClass().add("trust-badge-unverified");
            trustExplanationLabel.setText("Identity key is fresh. Click 'Trust Peer Key' below to pin to local trust store.");
            trustExplanationLabel.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");
        }
    }

    private void handleTrustClicked() {
        if (currentPeerUser != null && currentFingerprint != null) {
            trustStore.trustFingerprint(currentPeerUser, currentFingerprint);
            updateTrustState(true, false);
            trustButton.setDisable(true);
            if (onTrustChanged != null) {
                onTrustChanged.accept(currentPeerUser);
            }
        }
    }

    private void handleCopyFingerprint() {
        if (currentFingerprint != null) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(currentFingerprint);
            clipboard.setContent(content);

            String originalText = copyFingerprintBtn.getText();
            copyFingerprintBtn.setText("Copied to Clipboard!");
            PauseTransition pt = new PauseTransition(Duration.seconds(2));
            pt.setOnFinished(e -> copyFingerprintBtn.setText(originalText));
            pt.play();
        }
    }

    private String formatFingerprint(String fp) {
        if (fp == null) return "None";
        // Format SHA256:xx:xx into two clean rows if long
        if (fp.startsWith("SHA256:") && fp.length() > 40) {
            int mid = fp.indexOf(':', 40);
            if (mid > 0) {
                return fp.substring(0, mid) + "\n" + fp.substring(mid + 1);
            }
        }
        return fp;
    }

    private String cleanEndpoint(String ep) {
        if (ep == null) return "None";
        if (ep.startsWith("/")) return ep.substring(1);
        return ep;
    }
}
