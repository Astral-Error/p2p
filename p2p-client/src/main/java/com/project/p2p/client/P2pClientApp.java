package com.project.p2p.client;

import com.project.p2p.client.crypto.ClientKeyManager;
import com.project.p2p.client.crypto.TrustStore;
import com.project.p2p.client.network.PeerNetworkService;
import com.project.p2p.client.service.CoordinationServiceClient;
import com.project.p2p.client.ui.AuthView;
import com.project.p2p.client.ui.MainChatView;
import com.project.p2p.client.ui.common.ThemeManager;
import com.project.p2p.common.crypto.CryptoPrimitives;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

public class P2pClientApp extends Application {

    private Stage primaryStage;
    private CoordinationServiceClient coordinationClient;
    private ClientKeyManager keyManager;
    private TrustStore trustStore;
    private PeerNetworkService peerNetworkService;
    private MainChatView mainChatView;

    @Override
    public void start(Stage stage) {
        ThemeManager.initialize();

        this.primaryStage = stage;
        this.coordinationClient = new CoordinationServiceClient();
        this.keyManager = new ClientKeyManager();
        this.trustStore = new TrustStore();

        stage.setTitle("P2P Encrypted Chat");
        stage.setWidth(1060);
        stage.setHeight(700);
        stage.setMinWidth(850);
        stage.setMinHeight(550);

        showAuthView();
        stage.show();
    }

    private void showAuthView() {
        AuthView authView = new AuthView(coordinationClient, (username, passphrase, authResult) -> {
            initializeSessionAndShowMain(username, passphrase);
        });

        Scene scene = new Scene(authView, 1060, 700);
        ThemeManager.applyCustomStylesheet(scene);
        primaryStage.setScene(scene);
    }

    private void initializeSessionAndShowMain(String username, String passphrase) {
        try {
            // 1. Initialize local keypair (decrypt or generate new)
            keyManager.initialize(username, passphrase);

            // 2. Publish public keys to directory
            coordinationClient.publishKeysAsync(
                    keyManager.getIdentityPublicKeyBase64(),
                    keyManager.getKeyAgreementPublicKeyBase64(),
                    true
            ).thenCompose(keyRecord -> {
                // 3. Find available local port and start listening PeerServer
                int localPort = findAvailablePort();
                peerNetworkService = new PeerNetworkService(username, keyManager, trustStore, localPort);
                peerNetworkService.setPeerKeyResolver(peerUsername -> {
                    try {
                        var resolvedRecord = coordinationClient.getPeerKeysAsync(peerUsername).get(5, java.util.concurrent.TimeUnit.SECONDS);
                        if (resolvedRecord != null && resolvedRecord.identityPublicKey() != null) {
                            PublicKey key = CryptoPrimitives.decodeEd25519PublicKey(resolvedRecord.identityPublicKey());
                            peerNetworkService.registerPeerPublicKey(peerUsername, key);
                            return key;
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to resolve public key for " + peerUsername + ": " + e.getMessage());
                    }
                    return null;
                });
                try {
                    peerNetworkService.startServer();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to bind peer server", e);
                }

                // 4. Register presence with coordination server
                return coordinationClient.registerPresenceAsync(localPort);
            }).thenAccept(presenceSession -> {
                Platform.runLater(() -> {
                    primaryStage.setTitle("P2P Encrypted Chat - " + username + " [" + keyManager.getIdentityFingerprint().substring(0, 18) + "...]");
                    mainChatView = new MainChatView(
                            username,
                            coordinationClient,
                            keyManager,
                            trustStore,
                            peerNetworkService,
                            presenceSession.sessionId()
                    );
                    Scene scene = new Scene(mainChatView, 1060, 700);
                    ThemeManager.applyCustomStylesheet(scene);
                    primaryStage.setScene(scene);
                });
            }).exceptionally(ex -> {
                Platform.runLater(() -> {
                    System.err.println("Failed to initialize session: " + ex.getMessage());
                });
                return null;
            });

        } catch (GeneralSecurityException | IOException e) {
            System.err.println("Cryptographic keystore initialization failed: " + e.getMessage());
        }
    }

    private int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            return 9000 + (int) (Math.random() * 1000);
        }
    }

    @Override
    public void stop() {
        if (mainChatView != null) {
            mainChatView.cleanup();
        }
        if (peerNetworkService != null) {
            peerNetworkService.close();
        }
    }
}
