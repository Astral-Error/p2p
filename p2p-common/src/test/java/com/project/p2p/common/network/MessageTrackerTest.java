package com.project.p2p.common.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageTrackerTest {

    @Test
    void testLifecycleTransitions() {
        MessageTracker tracker = new MessageTracker();
        UUID id = UUID.randomUUID();

        List<MessageDeliveryState> recordedStates = new ArrayList<>();
        tracker.addStateChangeListener((msgId, state) -> {
            if (msgId.equals(id)) {
                recordedStates.add(state);
            }
        });

        // 1. Initial track
        MessageTracker.TrackedMessage msg = tracker.trackOutgoing(id, 1L);
        assertEquals(MessageDeliveryState.PENDING, tracker.getState(id));

        // 2. Mark sent (after TCP socket write)
        assertTrue(tracker.markSent(id));
        assertEquals(MessageDeliveryState.SENT, tracker.getState(id));
        assertTrue(msg.getSentTimestamp() > 0);

        // 3. Mark delivered (only on actual ACK_DELIVERED reception)
        assertTrue(tracker.markDelivered(id));
        assertEquals(MessageDeliveryState.DELIVERED, tracker.getState(id));
        assertTrue(msg.getDeliveredTimestamp() >= msg.getSentTimestamp());

        // 4. Mark read (on ACK_READ reception)
        assertTrue(tracker.markRead(id));
        assertEquals(MessageDeliveryState.READ, tracker.getState(id));
        assertTrue(msg.getReadTimestamp() >= msg.getDeliveredTimestamp());

        assertEquals(List.of(
                MessageDeliveryState.PENDING,
                MessageDeliveryState.SENT,
                MessageDeliveryState.DELIVERED,
                MessageDeliveryState.READ
        ), recordedStates);
    }

    @Test
    void testDeliveryRequiresExplicitAck() {
        MessageTracker tracker = new MessageTracker();
        UUID id = UUID.randomUUID();

        tracker.trackOutgoing(id, 10L);
        tracker.markSent(id);

        // Crucial requirement: SENT state must NOT be considered DELIVERED without evidence
        assertEquals(MessageDeliveryState.SENT, tracker.getState(id));
        assertNotEquals(MessageDeliveryState.DELIVERED, tracker.getState(id));
    }

    @Test
    void testDuplicateSequenceDetection() {
        MessageTracker tracker = new MessageTracker();

        assertTrue(tracker.recordReceivedSequence(1L), "First encounter of sequence 1 must return true");
        assertTrue(tracker.recordReceivedSequence(2L), "First encounter of sequence 2 must return true");
        assertFalse(tracker.recordReceivedSequence(1L), "Duplicate sequence 1 must return false");
        assertFalse(tracker.recordReceivedSequence(2L), "Duplicate sequence 2 must return false");
        assertTrue(tracker.recordReceivedSequence(3L), "First encounter of sequence 3 must return true");

        assertTrue(tracker.hasReceivedSequence(1L));
        assertTrue(tracker.hasReceivedSequence(2L));
        assertTrue(tracker.hasReceivedSequence(3L));
        assertFalse(tracker.hasReceivedSequence(4L));
    }

    @Test
    void testFailedTransition() {
        MessageTracker tracker = new MessageTracker();
        UUID id = UUID.randomUUID();

        tracker.trackOutgoing(id, 5L);
        tracker.markFailed(id);

        assertEquals(MessageDeliveryState.FAILED, tracker.getState(id));
        // Delivery can no longer transition from FAILED
        assertFalse(tracker.markDelivered(id));
    }
}
