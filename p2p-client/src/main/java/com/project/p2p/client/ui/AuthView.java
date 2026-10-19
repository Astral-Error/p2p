package com.project.p2p.client.ui;

import com.project.p2p.client.crypto.ClientKeyManager;
import com.project.p2p.client.model.AuthResult;
import com.project.p2p.client.service.CoordinationServiceClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Authentication and Local Key Setup View.
 * Handles user login, registration, and keystore passphrase unlocking.
 */
public class AuthView extends VBox {

    public interface AuthSuccessHandler {
        void onAuthenticated(String username, String passphrase, AuthResult authResult);
    }

    private final CoordinationServiceClient coordinationClient;
    private final AuthSuccessHandler successHandler;

    private final TextField serverUrlField;
    private final TextField usernameField;
    private final PasswordField passwordField;
    private final PasswordField passphraseField;
    private final Button loginButton;
    private final Button registerButton;
    private final Label statusLabel;
    private final ProgressIndicator progressIndicator;

    public AuthView(CoordinationServiceClient coordinationClient, AuthSuccessHandler successHandler) {
        this.coordinationClient = Objects.requireNonNull(coordinationClient);
        this.successHandler = Objects.requireNonNull(successHandler);

        setAlignment(Pos.CENTER);
        setPadding(new Insets(30));
        setSpacing(20);
        setStyle("-fx-background-color: #f1f5f9;");

        VBox card = new VBox(16);
        card.setMaxWidth(420);
        card.setPadding(new Insets(30));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 12; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 12, 0, 0, 4);");

        Label appTitle = new Label("P2P Encrypted Chat");
        appTitle.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Label appSubtitle = new Label("End-to-End Encrypted Peer-to-Peer Messaging");
        appSubtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(12);
        formGrid.setAlignment(Pos.CENTER);

        // Server URL
        Label serverLabel = new Label("Coordination Server:");
        serverLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: bold;");
        serverUrlField = new TextField("http://localhost:8080");
        serverUrlField.setPrefWidth(240);

        // Username
        Label userLabel = new Label("Username:");
        userLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: bold;");
        usernameField = new TextField();
        usernameField.setPromptText("e.g. alice");

        // Password
        Label passLabel = new Label("Account Password:");
        passLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: bold;");
        passwordField = new PasswordField();
        passwordField.setPromptText("Server account password");

        // Key Passphrase
        Label keyLabel = new Label("Local Key Passphrase:");
        keyLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: bold;");
        passphraseField = new PasswordField();
        passphraseField.setPromptText("Protects private keys on disk");

        formGrid.add(serverLabel, 0, 0);
        formGrid.add(serverUrlField, 1, 0);
        formGrid.add(userLabel, 0, 1);
        formGrid.add(usernameField, 1, 1);
        formGrid.add(passLabel, 0, 2);
        formGrid.add(passwordField, 1, 2);
        formGrid.add(keyLabel, 0, 3);
        formGrid.add(passphraseField, 1, 3);

        loginButton = new Button("Sign In");
        loginButton.setDefaultButton(true);
        loginButton.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 6;");
        loginButton.setOnAction(e -> handleAuth(false));

        registerButton = new Button("Create Account");
        registerButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #2563eb; -fx-border-color: #2563eb; -fx-border-radius: 6; -fx-padding: 7 16;");
        registerButton.setOnAction(e -> handleAuth(true));

        HBox btnBox = new HBox(12, registerButton, loginButton);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        btnBox.setPadding(new Insets(10, 0, 0, 0));

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(24, 24);
        progressIndicator.setVisible(false);

        statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-font-size: 12px;");

        HBox statusBox = new HBox(10, progressIndicator, statusLabel);
        statusBox.setAlignment(Pos.CENTER);

        card.getChildren().addAll(
                appTitle,
                appSubtitle,
                new Separator(),
                formGrid,
                btnBox,
                statusBox
        );

        getChildren().add(card);
    }

    private void handleAuth(boolean isRegister) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String passphrase = passphraseField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Username and password are required.");
            return;
        }

        if (passphrase.isEmpty()) {
            showError("Local key passphrase is required to protect your private key.");
            return;
        }

        setLoading(true);
        statusLabel.setStyle("-fx-text-fill: #475569; -fx-font-size: 12px;");
        statusLabel.setText(isRegister ? "Registering account..." : "Authenticating with server...");

        var authFuture = isRegister
                ? coordinationClient.registerAsync(username, password)
                : coordinationClient.loginAsync(username, password);

        authFuture.whenComplete((authResult, error) -> {
            Platform.runLater(() -> {
                setLoading(false);
                if (error != null) {
                    showError("Auth failed: " + getRootMessage(error));
                } else {
                    statusLabel.setStyle("-fx-text-fill: #16a34a; -fx-font-size: 12px;");
                    statusLabel.setText("Success! Initializing local identity...");
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
        loginButton.setDisable(loading);
        registerButton.setDisable(loading);
        usernameField.setDisable(loading);
        passwordField.setDisable(loading);
        passphraseField.setDisable(loading);
    }

    private void showError(String message) {
        statusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");
        statusLabel.setText(message);
    }

    private String getRootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
