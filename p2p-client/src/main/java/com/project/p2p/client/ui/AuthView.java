package com.project.p2p.client.ui;

import com.project.p2p.client.model.AuthResult;
import com.project.p2p.client.service.CoordinationServiceClient;
import com.project.p2p.client.ui.common.AppIcons;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.Objects;

/**
 * Modern Authentication and Keystore Setup View adhering to GitHub Primer design foundations.
 * Supports switching between Sign In and Account Creation with cryptographic passphrase protection.
 */
public class AuthView extends StackPane {

    public interface AuthSuccessHandler {
        void onAuthenticated(String username, String passphrase, AuthResult authResult);
    }

    private final CoordinationServiceClient coordinationClient;
    private final AuthSuccessHandler successHandler;

    private boolean isRegisterMode = false;

    private final TextField serverUrlField;
    private final TextField usernameField;
    private final PasswordField passwordField;
    private final PasswordField passphraseField;
    private final Button submitButton;
    private final Button signInTabBtn;
    private final Button registerTabBtn;
    private final Label statusBanner;
    private final ProgressIndicator progressIndicator;
    private final VBox serverConfigContainer;
    private final Hyperlink toggleServerConfigLink;

    public AuthView(CoordinationServiceClient coordinationClient, AuthSuccessHandler successHandler) {
        this.coordinationClient = Objects.requireNonNull(coordinationClient);
        this.successHandler = Objects.requireNonNull(successHandler);

        getStyleClass().add("auth-root-pane");
        setAlignment(Pos.CENTER);
        setPadding(new Insets(30));

        VBox card = new VBox(20);
        card.getStyleClass().add("auth-card");
        card.setMaxWidth(460);
        card.setAlignment(Pos.TOP_CENTER);

        // Header: Brand & Security Mission
        Node shieldLogo = AppIcons.create(AppIcons.Icon.SHIELD, 32, Color.web("#2f81f7"));
        Label appTitle = new Label("P2P Encrypted Chat");
        appTitle.getStyleClass().add("auth-header-title");

        Label appSubtitle = new Label("Decentralized \u00B7 End-to-End Encrypted \u00B7 Zero-Knowledge");
        appSubtitle.getStyleClass().add("auth-header-subtitle");

        VBox headerBox = new VBox(8, shieldLogo, appTitle, appSubtitle);
        headerBox.setAlignment(Pos.CENTER);

        // Mode Switcher (Segmented Buttons: Sign In / Create Account)
        signInTabBtn = new Button("Sign In");
        signInTabBtn.getStyleClass().addAll("auth-tab-selected");
        signInTabBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8 20; -fx-cursor: hand; -fx-background-radius: 6 0 0 6;");

        registerTabBtn = new Button("Create Account");
        registerTabBtn.getStyleClass().addAll("auth-tab-unselected");
        registerTabBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8 20; -fx-cursor: hand; -fx-background-radius: 0 6 6 0; -fx-border-color: -color-border-default; -fx-border-width: 1 1 1 0;");

        signInTabBtn.setOnAction(e -> setMode(false));
        registerTabBtn.setOnAction(e -> setMode(true));

        HBox modeSwitcher = new HBox(signInTabBtn, registerTabBtn);
        modeSwitcher.setAlignment(Pos.CENTER);

        // Status Banner (Errors / Progress)
        statusBanner = new Label();
        statusBanner.setWrapText(true);
        statusBanner.setVisible(false);
        statusBanner.setManaged(false);
        statusBanner.setMaxWidth(Double.MAX_VALUE);
        statusBanner.setStyle("-fx-padding: 8 12; -fx-background-radius: 6; -fx-font-size: 12px;");

        // Form Fields
        VBox formContainer = new VBox(14);
        formContainer.setAlignment(Pos.CENTER_LEFT);

        // 1. Username
        Label userLabel = new Label("Username");
        userLabel.getStyleClass().add("auth-field-label");
        usernameField = new TextField();
        usernameField.setPromptText("Enter your username");
        usernameField.getStyleClass().add("auth-text-field");
        VBox userBox = new VBox(4, userLabel, usernameField);

        // 2. Account Password
        Label passLabel = new Label("Account Password");
        passLabel.getStyleClass().add("auth-field-label");
        passwordField = new PasswordField();
        passwordField.setPromptText("Server authentication password");
        passwordField.getStyleClass().add("auth-text-field");
        VBox passBox = new VBox(4, passLabel, passwordField);

        // 3. Local Key Passphrase
        Label keyLabel = new Label("Local Keystore Passphrase");
        keyLabel.getStyleClass().add("auth-field-label");
        Label keyHelper = new Label("Encrypts your private Ed25519/X25519 identity key on disk via PBKDF2/AES-GCM.");
        keyHelper.getStyleClass().add("auth-field-helper");
        keyHelper.setWrapText(true);
        passphraseField = new PasswordField();
        passphraseField.setPromptText("Protects cryptographic keystore on this device");
        passphraseField.getStyleClass().add("auth-text-field");
        VBox keyBox = new VBox(4, keyLabel, keyHelper, passphraseField);

        // 4. Advanced Server Configuration (Collapsible)
        toggleServerConfigLink = new Hyperlink("Server Settings \u25BE");
        toggleServerConfigLink.setStyle("-fx-text-fill: -color-accent-fg; -fx-font-size: 11px; -fx-padding: 0;");
        toggleServerConfigLink.setOnAction(e -> toggleServerConfig());

        Label serverLabel = new Label("Coordination Server URL");
        serverLabel.getStyleClass().add("auth-field-label");
        serverUrlField = new TextField("http://localhost:8080");
        serverUrlField.getStyleClass().add("auth-text-field");

        serverConfigContainer = new VBox(4, serverLabel, serverUrlField);
        serverConfigContainer.setVisible(false);
        serverConfigContainer.setManaged(false);

        formContainer.getChildren().addAll(userBox, passBox, keyBox, toggleServerConfigLink, serverConfigContainer);

        // Submit Action Button
        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(16, 16);
        progressIndicator.setVisible(false);

        submitButton = new Button("Sign In");
        submitButton.setDefaultButton(true);
        submitButton.setMaxWidth(Double.MAX_VALUE);
        submitButton.setStyle("-fx-background-color: -color-accent-emphasis; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 10 20; -fx-background-radius: 6; -fx-cursor: hand;");
        submitButton.setOnAction(e -> handleAuth());

        HBox actionBox = new HBox(8, progressIndicator, submitButton);
        HBox.setHgrow(submitButton, Priority.ALWAYS);
        actionBox.setAlignment(Pos.CENTER);

        card.getChildren().addAll(
                headerBox,
                modeSwitcher,
                statusBanner,
                formContainer,
                actionBox
        );

        getChildren().add(card);
    }

    private void setMode(boolean isRegister) {
        this.isRegisterMode = isRegister;
        statusBanner.setVisible(false);
        statusBanner.setManaged(false);

        if (isRegister) {
            registerTabBtn.setStyle("-fx-background-color: -color-canvas-overlay; -fx-text-fill: -color-fg-default; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 20; -fx-cursor: hand; -fx-background-radius: 0 6 6 0; -fx-border-color: -color-accent-fg; -fx-border-width: 1;");
            signInTabBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -color-fg-muted; -fx-font-size: 13px; -fx-padding: 8 20; -fx-cursor: hand; -fx-background-radius: 6 0 0 6; -fx-border-color: -color-border-default; -fx-border-width: 1 0 1 1;");
            submitButton.setText("Create Account & Generate Keys");
        } else {
            signInTabBtn.setStyle("-fx-background-color: -color-canvas-overlay; -fx-text-fill: -color-fg-default; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 20; -fx-cursor: hand; -fx-background-radius: 6 0 0 6; -fx-border-color: -color-accent-fg; -fx-border-width: 1;");
            registerTabBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -color-fg-muted; -fx-font-size: 13px; -fx-padding: 8 20; -fx-cursor: hand; -fx-background-radius: 0 6 6 0; -fx-border-color: -color-border-default; -fx-border-width: 1 1 1 0;");
            submitButton.setText("Sign In");
        }
    }

    private void toggleServerConfig() {
        boolean show = !serverConfigContainer.isVisible();
        serverConfigContainer.setVisible(show);
        serverConfigContainer.setManaged(show);
        toggleServerConfigLink.setText(show ? "Server Settings \u25B4" : "Server Settings \u25BE");
    }

    private void handleAuth() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String passphrase = passphraseField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Username and password are required.");
            return;
        }

        if (passphrase.isEmpty()) {
            showError("Local key passphrase is required to protect your private cryptographic identity.");
            return;
        }

        setLoading(true);
        showInfo(isRegisterMode ? "Registering account with coordination server..." : "Authenticating with server...");

        var authFuture = isRegisterMode
                ? coordinationClient.registerAsync(username, password)
                : coordinationClient.loginAsync(username, password);

        authFuture.whenComplete((authResult, error) -> {
            Platform.runLater(() -> {
                setLoading(false);
                if (error != null) {
                    showError("Authentication failed: " + getRootMessage(error));
                } else {
                    showSuccess("Success! Initializing local cryptographic identity...");
                    String canonicalUsername = (authResult != null && authResult.username() != null)
                            ? authResult.username().trim().toLowerCase()
                            : username.trim().toLowerCase();
                    successHandler.onAuthenticated(canonicalUsername, passphrase, authResult);
                }
            });
        });
    }

    private void setLoading(boolean loading) {
        progressIndicator.setVisible(loading);
        submitButton.setDisable(loading);
        usernameField.setDisable(loading);
        passwordField.setDisable(loading);
        passphraseField.setDisable(loading);
        signInTabBtn.setDisable(loading);
        registerTabBtn.setDisable(loading);
    }

    private void showError(String message) {
        statusBanner.setText(message);
        statusBanner.setStyle("-fx-background-color: -color-danger-subtle; -fx-border-color: -color-danger-emphasis; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: -color-danger-fg; -fx-font-size: 12px; -fx-padding: 8 12;");
        statusBanner.setVisible(true);
        statusBanner.setManaged(true);
    }

    private void showInfo(String message) {
        statusBanner.setText(message);
        statusBanner.setStyle("-fx-background-color: -color-accent-subtle; -fx-border-color: -color-accent-fg; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: -color-accent-fg; -fx-font-size: 12px; -fx-padding: 8 12;");
        statusBanner.setVisible(true);
        statusBanner.setManaged(true);
    }

    private void showSuccess(String message) {
        statusBanner.setText(message);
        statusBanner.setStyle("-fx-background-color: -color-success-subtle; -fx-border-color: -color-success-emphasis; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: -color-success-fg; -fx-font-size: 12px; -fx-padding: 8 12;");
        statusBanner.setVisible(true);
        statusBanner.setManaged(true);
    }

    private String getRootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
