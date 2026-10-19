package com.project.p2p.client.ui;

import com.project.p2p.client.ui.common.AppIcons;
import com.project.p2p.common.network.MessageDeliveryState;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * Custom ListCell rendering modern Primer-styled message bubbles with vector
 * delivery badges, accessible tooltips, and secondary timestamps.
 */
public class ChatMessageCell extends ListCell<ChatMessageModel> {

    @Override
    protected void updateItem(ChatMessageModel item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setStyle("-fx-background-color: transparent; -fx-padding: 0;");
            return;
        }

        boolean isOut = item.isOutgoing();

        // Message text using Text inside TextFlow for natural wrapping without truncation
        Text textNode = new Text(item.getText());
        textNode.getStyleClass().add(isOut ? "message-text-outgoing" : "message-text-incoming");

        TextFlow textFlow = new TextFlow(textNode);
        textFlow.maxWidthProperty().bind(widthProperty().multiply(0.65));

        // Timestamp
        Label timeLabel = new Label(item.getFormattedTime());
        timeLabel.getStyleClass().add(isOut ? "message-meta-outgoing" : "message-meta-incoming");

        HBox metaBox = new HBox(6);
        metaBox.setAlignment(isOut ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        metaBox.getChildren().add(timeLabel);

        // Vector delivery state badge for outgoing messages
        if (isOut) {
            Node statusIcon = buildStatusIcon(item.getDeliveryState());
            metaBox.getChildren().add(statusIcon);

            item.deliveryStateProperty().addListener((obs, oldVal, newVal) -> {
                metaBox.getChildren().set(metaBox.getChildren().size() - 1, buildStatusIcon(newVal));
            });
        }

        VBox bubble = new VBox(4, textFlow, metaBox);
        bubble.maxWidthProperty().bind(widthProperty().multiply(0.68));
        bubble.getStyleClass().add(isOut ? "message-bubble-outgoing" : "message-bubble-incoming");

        HBox container = new HBox(bubble);
        container.setPadding(new Insets(4, 16, 4, 16));
        container.setAlignment(isOut ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        setGraphic(container);
    }

    private Node buildStatusIcon(MessageDeliveryState state) {
        if (state == null) {
            return AppIcons.create(AppIcons.Icon.CLOCK, 12, Color.web("rgba(255, 255, 255, 0.6)"));
        }

        Node iconNode = switch (state) {
            case PENDING -> AppIcons.create(AppIcons.Icon.CLOCK, 12, Color.web("rgba(255, 255, 255, 0.6)"));
            case SENT -> AppIcons.create(AppIcons.Icon.CHECK, 12, Color.web("rgba(255, 255, 255, 0.8)"));
            case DELIVERED -> AppIcons.create(AppIcons.Icon.DOUBLE_CHECK, 13, Color.web("#e6edf3"));
            case READ -> AppIcons.create(AppIcons.Icon.DOUBLE_CHECK, 13, Color.web("#79c0ff"));
            case FAILED -> AppIcons.create(AppIcons.Icon.ALERT_CIRCLE, 12, Color.web("#ff7b72"));
        };

        String tooltipText = switch (state) {
            case PENDING -> "Sending...";
            case SENT -> "Sent to peer";
            case DELIVERED -> "Delivered to peer device";
            case READ -> "Read by peer";
            case FAILED -> "Failed to deliver";
        };

        Tooltip.install(iconNode, new Tooltip(tooltipText));
        return iconNode;
    }
}
