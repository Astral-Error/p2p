package com.project.p2p.client.ui;

import com.project.p2p.client.crypto.TrustStore;
import com.project.p2p.client.network.EncryptedPeerSession;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Collapsible panel displaying peer verification status, Ed25519 cryptographic fingerprint,
 * TOFU trust controls, and connection diagnostics.
 */
public class PeerInfoPanel extends VBox {

    private final Label peerNameLabel;
    private final Label fingerprintLabel;
    private final Label trustStatusLabel;
    private final Label connectionDetailsLabel;
    private final Label cipherSuiteLabel;
    private final Label statsLabel;
    private final Button trustButton;

    private String currentPeerUser;
    private String currentFingerprint;
    private final TrustStore trustStore;
    private Consumer<String> onTrustChanged;

    public PeerInfoPanel(TrustStore trustStore) {
        this.trustStore = trustStore;

        setSpacing(10);
        setPadding(new Insets(14));
        setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 0 1;");
        setPrefWidth(280);

        Label headerLabel = new Label("SECURITY & IDENTITY");
        headerLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #64748b; -fx-letter-spacing: 1px;");

        peerNameLabel = new Label("No Peer Selected");
        peerNameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        // Trust badge
        trustStatusLabel = new Label("UNVERIFIED");
        trustStatusLabel.setPadding(new Insets(3, 8, 3, 8));
        updateTrustBadge(false, false);

        HBox topBox = new HBox(8, peerNameLabel, trustStatusLabel);
        topBox.setAlignment(Pos.CENTER_LEFT);

        // Fingerprint section
        Label fpHeader = new Label("IDENTITY FINGERPRINT (SHA-256)");
        fpHeader.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");

        fingerprintLabel = new Label("None");
        fingerprintLabel.setWrapText(true);
        fingerprintLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-text-fill: #334155; " +
                "-fx-background-color: #ffffff; -fx-padding: 8px; -fx-border-color: #cbd5e1; -fx-border-radius: 4; -fx-background-radius: 4;");

        trustButton = new Button("Trust Peer Key (TOFU)");
        trustButton.setMaxWidth(Double.MAX_VALUE);
        trustButton.setStyle("-fx-background-color: #0284c7; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 12;");
        trustButton.setOnAction(e -> handleTrustClicked());
        trustButton.setDisable(true);

        // Diagnostics section
        Label diagHeader = new Label("CONNECTION DIAGNOSTICS");
        diagHeader.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #94a3b8; -fx-padding: 10 0 0 0;");

        cipherSuiteLabel = new Label("Cipher: AES-256-GCM / X25519");
        cipherSuiteLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        connectionDetailsLabel = new Label("Status: Disconnected");
        connectionDetailsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        statsLabel = new Label("Sent: 0 frames | Received: 0 frames");
        statsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        VBox diagBox = new VBox(6, diagHeader, cipherSuiteLabel, connectionDetailsLabel, statsLabel);
        diagBox.setPadding(new Insets(10));
        diagBox.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6;");

        getChildren().addAll(
                headerLabel,
                topBox,
                fpHeader,
                fingerprintLabel,
                trustButton,
                diagBox
        );
    }

    public void setOnTrustChanged(Consumer<String> callback) {
        this.onTrustChanged = callback;
    }

    public void updatePeer(String username, String fingerprint, EncryptedPeerSession session) {
        this.currentPeerUser = username;
        this.currentFingerprint = fingerprint;

        if (username == null) {
            peerNameLabel.setText("No Peer Selected");
            fingerprintLabel.setText("None");
            trustButton.setDisable(true);
            updateTrustBadge(false, false);
            connectionDetailsLabel.setText("Status: Disconnected");
            statsLabel.setText("Sent: 0 frames | Received: 0 frames");
            return;
        }

        peerNameLabel.setText(username);
        fingerprintLabel.setText(fingerprint != null ? fingerprint : "Awaiting handshake...");

        boolean isTrusted = (fingerprint != null) && trustStore.isTrusted(username, fingerprint);
        boolean hasChanged = (fingerprint != null) && trustStore.hasKeyChanged(username, fingerprint);

        updateTrustBadge(isTrusted, hasChanged);
        trustButton.setDisable(fingerprint == null || isTrusted);

        if (session != null && session.getConnection().isConnected()) {
            String roleStr = session.isInitiator() ? "Initiator (Outbound)" : "Responder (Inbound)";
            String localEp = String.valueOf(session.getConnection().getLocalAddress());
            String remoteEp = String.valueOf(session.getConnection().getRemoteAddress());
            String statusStr = session.isSecureEstablished() ? "Connected & Encrypted" : session.getState().toString();

            connectionDetailsLabel.setText(String.format("Status: %s\nRole: %s\nLocal: %s\nRemote: %s",
                    statusStr, roleStr, localEp, remoteEp));
            statsLabel.setText(String.format("Sent: %d frames | Recv: %d frames",
                    session.getEncryptedFramesSent(), session.getEncryptedFramesReceived()));
        } else if (session != null) {
            connectionDetailsLabel.setText("Status: " + session.getState());
            statsLabel.setText(String.format("Sent: %d frames | Recv: %d frames",
                    session.getEncryptedFramesSent(), session.getEncryptedFramesReceived()));
        } else {
            connectionDetailsLabel.setText("Status: Disconnected");
            statsLabel.setText("Sent: 0 frames | Recv: 0 frames");
        }
    }

    private void updateTrustBadge(boolean trusted, boolean changed) {
        if (changed) {
            trustStatusLabel.setText("KEY CHANGED ⚠");
            trustStatusLabel.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-font-size: 10px; -fx-background-radius: 4;");
        } else if (trusted) {
            trustStatusLabel.setText("VERIFIED ✓");
            trustStatusLabel.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #16a34a; -fx-font-weight: bold; -fx-font-size: 10px; -fx-background-radius: 4;");
        } else {
            trustStatusLabel.setText("UNVERIFIED");
            trustStatusLabel.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-font-weight: bold; -fx-font-size: 10px; -fx-background-radius: 4;");
        }
    }

    private void handleTrustClicked() {
        if (currentPeerUser != null && currentFingerprint != null) {
            trustStore.trustFingerprint(currentPeerUser, currentFingerprint);
            updateTrustBadge(true, false);
            trustButton.setDisable(true);
            if (onTrustChanged != null) {
                onTrustChanged.accept(currentPeerUser);
            }
        }
    }
}
