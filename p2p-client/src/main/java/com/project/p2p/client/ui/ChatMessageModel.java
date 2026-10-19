package com.project.p2p.client.ui;

import com.project.p2p.common.network.MessageDeliveryState;
import javafx.beans.property.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * Observable UI model representing a chat message in the conversation view.
 */
public class ChatMessageModel {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final UUID messageId;
    private final String sender;
    private final String text;
    private final long timestamp;
    private final boolean outgoing;
    private final ObjectProperty<MessageDeliveryState> deliveryState;

    public ChatMessageModel(UUID messageId, String sender, String text, long timestamp, boolean outgoing, MessageDeliveryState state) {
        this.messageId = Objects.requireNonNull(messageId, "messageId must not be null");
        this.sender = Objects.requireNonNull(sender, "sender must not be null");
        this.text = Objects.requireNonNull(text, "text must not be null");
        this.timestamp = timestamp;
        this.outgoing = outgoing;
        this.deliveryState = new SimpleObjectProperty<>(state != null ? state : MessageDeliveryState.PENDING);
    }

    public UUID getMessageId() {
        return messageId;
    }

    public String getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFormattedTime() {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp));
    }

    public boolean isOutgoing() {
        return outgoing;
    }

    public MessageDeliveryState getDeliveryState() {
        return deliveryState.get();
    }

    public ObjectProperty<MessageDeliveryState> deliveryStateProperty() {
        return deliveryState;
    }

    public void setDeliveryState(MessageDeliveryState state) {
        this.deliveryState.set(state);
    }
}
