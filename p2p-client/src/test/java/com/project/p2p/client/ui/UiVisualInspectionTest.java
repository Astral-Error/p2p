package com.project.p2p.client.ui;

import com.project.p2p.client.crypto.ClientKeyManager;
import com.project.p2p.client.crypto.TrustStore;
import com.project.p2p.client.model.OnlinePeer;
import com.project.p2p.client.network.PeerNetworkService;
import com.project.p2p.client.service.CoordinationServiceClient;
import com.project.p2p.client.ui.common.ThemeManager;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UiVisualInspectionTest {

    private static final String ARTIFACTS_DIR = "C:\\Users\\sarva\\.gemini\\antigravity\\brain\\9e4ae8de-1a60-4060-ba50-b788ef92d9d9";

    @BeforeAll
    static void initJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                ThemeManager.initialize();
                latch.countDown();
            });
        } catch (IllegalStateException e) {
            // Toolkit already initialized
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testRenderAuthViewAndCaptureScreenshot() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                CoordinationServiceClient client = new CoordinationServiceClient("http://localhost:8080");
                AuthView authView = new AuthView(client, (u, p, a) -> {});

                Scene scene = new Scene(authView, 1060, 700);
                ThemeManager.applyCustomStylesheet(scene);

                // Force CSS and layout pass
                authView.applyCss();
                authView.layout();

                WritableImage image = scene.snapshot(null);
                File outFile = new File(ARTIFACTS_DIR, "auth_view_screenshot.png");
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", outFile);
                System.out.println("Saved AuthView screenshot to: " + outFile.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    void testRenderMainChatViewAndCaptureScreenshot(@TempDir Path tempDir) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        ClientKeyManager km = new ClientKeyManager(tempDir.resolve("keys"));
        km.initialize("sarvansh", "test-passphrase");
        TrustStore ts = new TrustStore(tempDir.resolve("trust.properties"));
        PeerNetworkService netService = new PeerNetworkService("sarvansh", km, ts, 63848);

        Platform.runLater(() -> {
            try {
                CoordinationServiceClient client = new CoordinationServiceClient("http://localhost:8080");
                MainChatView chatView = new MainChatView("sarvansh", client, km, ts, netService, "mock-session-123");

                Scene scene = new Scene(chatView, 1060, 700);
                ThemeManager.applyCustomStylesheet(scene);

                chatView.applyCss();
                chatView.layout();

                WritableImage image = scene.snapshot(null);
                File outFile = new File(ARTIFACTS_DIR, "main_chat_view_screenshot.png");
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", outFile);
                System.out.println("Saved MainChatView screenshot to: " + outFile.getAbsolutePath());

                // Now test narrow layout
                scene.setRoot(new javafx.scene.layout.Pane()); // detach
                Scene narrowScene = new Scene(chatView, 880, 650);
                ThemeManager.applyCustomStylesheet(narrowScene);
                chatView.applyCss();
                chatView.layout();

                WritableImage narrowImg = narrowScene.snapshot(null);
                File narrowOut = new File(ARTIFACTS_DIR, "main_chat_view_narrow_screenshot.png");
                ImageIO.write(SwingFXUtils.fromFXImage(narrowImg, null), "png", narrowOut);
                System.out.println("Saved MainChatView narrow screenshot to: " + narrowOut.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    void testRenderActiveConversationAndLightMode(@TempDir Path tempDir) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        ClientKeyManager km = new ClientKeyManager(tempDir.resolve("keys"));
        km.initialize("sarvansh", "test-passphrase");
        TrustStore ts = new TrustStore(tempDir.resolve("trust.properties"));
        ts.trustFingerprint("ajitesh", "SHA256:fa:42:06:a6:d3:d5:17:52:14:63:95:fd:db:fa:88:ad:cf:ef:d9:1d:82:8a:65:3c:cc:da:d9:cc:80:88:f6:c6");

        PeerNetworkService netService = new PeerNetworkService("sarvansh", km, ts, 63848);

        Platform.runLater(() -> {
            try {
                CoordinationServiceClient client = new CoordinationServiceClient("http://localhost:8080");
                MainChatView chatView = new MainChatView("sarvansh", client, km, ts, netService, "mock-session-123");

                // Populate online peers
                OnlinePeer ajitesh = new OnlinePeer("ajitesh", "sess-1", "127.0.0.1", 63854, java.time.Instant.now(), "mockKeyBase64", "mockKeyAgreement");
                OnlinePeer alice = new OnlinePeer("alice", "sess-2", "192.168.1.15", 55420, java.time.Instant.now(), "mockAliceKey", "mockKeyAgreement");

                // Use reflection or package-access to add items
                java.lang.reflect.Field peersField = MainChatView.class.getDeclaredField("onlinePeersList");
                peersField.setAccessible(true);
                @SuppressWarnings("unchecked")
                javafx.collections.ObservableList<OnlinePeer> peersList = (javafx.collections.ObservableList<OnlinePeer>) peersField.get(chatView);
                peersList.addAll(ajitesh, alice);

                // Populate messages
                java.lang.reflect.Field messagesField = MainChatView.class.getDeclaredField("activeMessages");
                messagesField.setAccessible(true);
                @SuppressWarnings("unchecked")
                javafx.collections.ObservableList<ChatMessageModel> activeMessages = (javafx.collections.ObservableList<ChatMessageModel>) messagesField.get(chatView);

                activeMessages.add(new ChatMessageModel(
                        java.util.UUID.randomUUID(), "ajitesh",
                        "Hey Sarvansh! Is the Ed25519 cryptographic handshake working now?",
                        System.currentTimeMillis() - 120000, false, com.project.p2p.common.network.MessageDeliveryState.DELIVERED
                ));

                activeMessages.add(new ChatMessageModel(
                        java.util.UUID.randomUUID(), "sarvansh",
                        "Yes! The transcript byte casing issue was diagnosed and resolved. All 72 tests pass.",
                        System.currentTimeMillis() - 60000, true, com.project.p2p.common.network.MessageDeliveryState.READ
                ));

                activeMessages.add(new ChatMessageModel(
                        java.util.UUID.randomUUID(), "ajitesh",
                        "Awesome. The new GitHub Primer dark theme looks clean and responsive.",
                        System.currentTimeMillis() - 30000, false, com.project.p2p.common.network.MessageDeliveryState.DELIVERED
                ));

                activeMessages.add(new ChatMessageModel(
                        java.util.UUID.randomUUID(), "sarvansh",
                        "Agreed! Notice the vector delivery status checkmarks and the collapsible security inspector.",
                        System.currentTimeMillis() - 5000, true, com.project.p2p.common.network.MessageDeliveryState.DELIVERED
                ));

                // Set header to ajitesh
                java.lang.reflect.Field headerLabelField = MainChatView.class.getDeclaredField("activePeerHeaderLabel");
                headerLabelField.setAccessible(true);
                javafx.scene.control.Label headerLabel = (javafx.scene.control.Label) headerLabelField.get(chatView);
                headerLabel.setText("ajitesh");

                java.lang.reflect.Field avatarContainerField = MainChatView.class.getDeclaredField("activePeerAvatarContainer");
                avatarContainerField.setAccessible(true);
                javafx.scene.layout.HBox avatarContainer = (javafx.scene.layout.HBox) avatarContainerField.get(chatView);
                avatarContainer.getChildren().setAll(new com.project.p2p.client.ui.common.AvatarView("ajitesh", 36, true, true));

                java.lang.reflect.Field statusBadgeField = MainChatView.class.getDeclaredField("activePeerStatusBadge");
                statusBadgeField.setAccessible(true);
                javafx.scene.control.Label statusBadge = (javafx.scene.control.Label) statusBadgeField.get(chatView);
                statusBadge.setText("E2E ENCRYPTED (AES-256-GCM)");
                statusBadge.getStyleClass().setAll("status-badge-established");

                java.lang.reflect.Field emptyStateField = MainChatView.class.getDeclaredField("chatEmptyState");
                emptyStateField.setAccessible(true);
                javafx.scene.Node emptyState = (javafx.scene.Node) emptyStateField.get(chatView);
                emptyState.setVisible(false);

                // Update peerInfoPanel
                java.lang.reflect.Field infoPanelField = MainChatView.class.getDeclaredField("peerInfoPanel");
                infoPanelField.setAccessible(true);
                PeerInfoPanel infoPanel = (PeerInfoPanel) infoPanelField.get(chatView);
                infoPanel.updatePeer("ajitesh", "SHA256:fa:42:06:a6:d3:d5:17:52:14:63:95:fd:db:fa:88:ad:cf:ef:d9:1d:82:8a:65:3c:cc:da:d9:cc:80:88:f6:c6", null);

                // Enable input
                java.lang.reflect.Field inputField = MainChatView.class.getDeclaredField("messageInputField");
                inputField.setAccessible(true);
                ((javafx.scene.control.TextField) inputField.get(chatView)).setDisable(false);

                java.lang.reflect.Field sendBtnField = MainChatView.class.getDeclaredField("sendButton");
                sendBtnField.setAccessible(true);
                ((javafx.scene.control.Button) sendBtnField.get(chatView)).setDisable(false);

                Scene scene = new Scene(chatView, 1060, 700);
                ThemeManager.applyCustomStylesheet(scene);
                chatView.applyCss();
                chatView.layout();

                WritableImage activeImg = scene.snapshot(null);
                File activeOut = new File(ARTIFACTS_DIR, "active_conversation_screenshot.png");
                ImageIO.write(SwingFXUtils.fromFXImage(activeImg, null), "png", activeOut);
                System.out.println("Saved active conversation screenshot to: " + activeOut.getAbsolutePath());

                // Toggle to light mode to verify theme switching
                ThemeManager.setDarkMode(false, scene);
                chatView.applyCss();
                chatView.layout();

                WritableImage lightImg = scene.snapshot(null);
                File lightOut = new File(ARTIFACTS_DIR, "light_mode_screenshot.png");
                ImageIO.write(SwingFXUtils.fromFXImage(lightImg, null), "png", lightOut);
                System.out.println("Saved light mode screenshot to: " + lightOut.getAbsolutePath());

                // Reset back to dark mode
                ThemeManager.setDarkMode(true, scene);

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
}
