package com.project.p2p.common.network;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Tracks message lifecycle states (PENDING -> SENT -> DELIVERED -> READ)
 * and verifies incoming sequence numbers for duplicate prevention.
 */
public class MessageTracker {

    public static class TrackedMessage {
        private final UUID messageId;
        private final long sequenceNumber;
        private volatile MessageDeliveryState state;
        private final long createdTimestamp;
        private volatile long sentTimestamp;
        private volatile long deliveredTimestamp;
        private volatile long readTimestamp;

        public TrackedMessage(UUID messageId, long sequenceNumber) {
            this.messageId = Objects.requireNonNull(messageId, "messageId must not be null");
            this.sequenceNumber = sequenceNumber;
            this.state = MessageDeliveryState.PENDING;
            this.createdTimestamp = System.currentTimeMillis();
        }

        public UUID getMessageId() { return messageId; }
        public long getSequenceNumber() { return sequenceNumber; }
        public MessageDeliveryState getState() { return state; }
        public long getCreatedTimestamp() { return createdTimestamp; }
        public long getSentTimestamp() { return sentTimestamp; }
        public long getDeliveredTimestamp() { return deliveredTimestamp; }
        public long getReadTimestamp() { return readTimestamp; }
    }

    private final Map<UUID, TrackedMessage> outboundMessages = new ConcurrentHashMap<>();
    private final Set<Long> receivedSequences = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final List<BiConsumer<UUID, MessageDeliveryState>> stateChangeListeners = new ArrayList<>();

    public synchronized void addStateChangeListener(BiConsumer<UUID, MessageDeliveryState> listener) {
        stateChangeListeners.add(Objects.requireNonNull(listener));
    }

    private void notifyListeners(UUID messageId, MessageDeliveryState newState) {
        List<BiConsumer<UUID, MessageDeliveryState>> listeners;
        synchronized (this) {
            listeners = new ArrayList<>(stateChangeListeners);
        }
        for (BiConsumer<UUID, MessageDeliveryState> listener : listeners) {
            try {
                listener.accept(messageId, newState);
            } catch (Exception ignored) {}
        }
    }

    public TrackedMessage trackOutgoing(UUID messageId, long sequenceNumber) {
        TrackedMessage msg = new TrackedMessage(messageId, sequenceNumber);
        outboundMessages.put(messageId, msg);
        notifyListeners(messageId, MessageDeliveryState.PENDING);
        return msg;
    }

    public boolean markSent(UUID messageId) {
        TrackedMessage msg = outboundMessages.get(messageId);
        if (msg != null && msg.state == MessageDeliveryState.PENDING) {
            msg.state = MessageDeliveryState.SENT;
            msg.sentTimestamp = System.currentTimeMillis();
            notifyListeners(messageId, MessageDeliveryState.SENT);
            return true;
        }
        return false;
    }

    public boolean markDelivered(UUID messageId) {
        TrackedMessage msg = outboundMessages.get(messageId);
        if (msg != null && (msg.state == MessageDeliveryState.SENT || msg.state == MessageDeliveryState.PENDING)) {
            msg.state = MessageDeliveryState.DELIVERED;
            msg.deliveredTimestamp = System.currentTimeMillis();
            notifyListeners(messageId, MessageDeliveryState.DELIVERED);
            return true;
        }
        return false;
    }

    public boolean markRead(UUID messageId) {
        TrackedMessage msg = outboundMessages.get(messageId);
        if (msg != null && msg.state != MessageDeliveryState.READ && msg.state != MessageDeliveryState.FAILED) {
            msg.state = MessageDeliveryState.READ;
            msg.readTimestamp = System.currentTimeMillis();
            notifyListeners(messageId, MessageDeliveryState.READ);
            return true;
        }
        return false;
    }

    public boolean markFailed(UUID messageId) {
        TrackedMessage msg = outboundMessages.get(messageId);
        if (msg != null && msg.state != MessageDeliveryState.DELIVERED && msg.state != MessageDeliveryState.READ) {
            msg.state = MessageDeliveryState.FAILED;
            notifyListeners(messageId, MessageDeliveryState.FAILED);
            return true;
        }
        return false;
    }

    public MessageDeliveryState getState(UUID messageId) {
        TrackedMessage msg = outboundMessages.get(messageId);
        return (msg != null) ? msg.state : null;
    }

    public TrackedMessage getTrackedMessage(UUID messageId) {
        return outboundMessages.get(messageId);
    }

    /**
     * Records an incoming sequence number.
     *
     * @param sequenceNumber sequence number received from peer
     * @return true if this sequence number has NOT been seen before; false if it is a duplicate
     */
    public boolean recordReceivedSequence(long sequenceNumber) {
        return receivedSequences.add(sequenceNumber);
    }

    public boolean hasReceivedSequence(long sequenceNumber) {
        return receivedSequences.contains(sequenceNumber);
    }

    public int getOutboundTrackedCount() {
        return outboundMessages.size();
    }
}
