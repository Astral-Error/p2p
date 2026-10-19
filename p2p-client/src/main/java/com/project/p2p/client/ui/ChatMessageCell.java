package com.project.p2p.client.ui;

import com.project.p2p.common.network.MessageDeliveryState;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Custom JavaFX ListCell rendering styled message bubbles with timestamps and delivery badges.
 */
public class ChatMessageCell extends ListCell<ChatMessageModel> {

    @Override
    protected void updateItem(ChatMessageModel item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setStyle("-fx-background-color: transparent;");
            return;
        }

        boolean isOut = item.isOutgoing();

        // Message text
        Label textLabel = new Label(item.getText());
        textLabel.setWrapText(true);
        textLabel.setMaxWidth(360);
        textLabel.setStyle(isOut
                ? "-fx-text-fill: white; -fx-font-size: 13px;"
                : "-fx-text-fill: #1e293b; -fx-font-size: 13px;");

        // Timestamp and status info
        Label timeLabel = new Label(item.getFormattedTime());
        timeLabel.setStyle(isOut
                ? "-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 10px;"
                : "-fx-text-fill: #64748b; -fx-font-size: 10px;");

        HBox metaBox = new HBox(6);
        metaBox.setAlignment(isOut ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        metaBox.getChildren().add(timeLabel);

        if (isOut) {
            Label statusBadge = new Label(getStatusText(item.getDeliveryState()));
            statusBadge.setStyle(getStatusStyle(item.getDeliveryState()));
            metaBox.getChildren().add(statusBadge);

            item.deliveryStateProperty().addListener((obs, oldVal, newVal) -> {
                statusBadge.setText(getStatusText(newVal));
                statusBadge.setStyle(getStatusStyle(newVal));
            });
        }

        VBox bubble = new VBox(4, textLabel, metaBox);
        bubble.setPadding(new Insets(8, 12, 8, 12));
        bubble.setMaxWidth(380);

        if (isOut) {
            // Modern rounded outgoing bubble (Indigo/Blue gradient)
            bubble.setStyle("-fx-background-color: linear-gradient(to bottom right, #3b82f6, #2563eb);" +
                    "-fx-background-radius: 14 14 2 14;" +
                    "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 4, 0, 0, 1);");
        } else {
            // Modern rounded incoming bubble (Soft gray)
            bubble.setStyle("-fx-background-color: #f1f5f9;" +
                    "-fx-background-radius: 14 14 14 2;" +
                    "-fx-border-color: #e2e8f0; -fx-border-radius: 14 14 14 2; -fx-border-width: 1;" +
                    "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 3, 0, 0, 1);");
        }

        HBox container = new HBox(bubble);
        container.setPadding(new Insets(3, 8, 3, 8));
        container.setAlignment(isOut ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        setStyle("-fx-background-color: transparent;");
        setGraphic(container);
    }

    private String getStatusText(MessageDeliveryState state) {
        if (state == null) return "…";
        return switch (state) {
            case PENDING -> "⏳";
            case SENT -> "✓";
            case DELIVERED -> "✓✓";
            case READ -> "✓✓";
            case FAILED -> "✕";
        };
    }

    private String getStatusStyle(MessageDeliveryState state) {
        if (state == null) return "-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 10px;";
        return switch (state) {
            case PENDING -> "-fx-text-fill: rgba(255,255,255,0.6); -fx-font-size: 10px;";
            case SENT -> "-fx-text-fill: rgba(255,255,255,0.8); -fx-font-size: 11px; -fx-font-weight: bold;";
            case DELIVERED -> "-fx-text-fill: #cbd5e1; -fx-font-size: 11px; -fx-font-weight: bold;";
            case READ -> "-fx-text-fill: #38bdf8; -fx-font-size: 11px; -fx-font-weight: bold;";
            case FAILED -> "-fx-text-fill: #f87171; -fx-font-size: 11px; -fx-font-weight: bold;";
        };
    }
}
