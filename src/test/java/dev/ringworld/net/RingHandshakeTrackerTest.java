package dev.ringworld.net;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingHandshakeTrackerTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000073");

    @Test
    void correctAcknowledgementCompletesExactlyOnePendingSession() {
        RingHandshakeTracker tracker = new RingHandshakeTracker();
        tracker.begin(PLAYER, 20L);

        assertEquals(RingHandshakeTracker.AcknowledgementResult.ACCEPTED,
                tracker.acknowledge(PLAYER));
        assertTrue(tracker.isAcknowledged(PLAYER));
        assertEquals(RingHandshakeTracker.AcknowledgementResult.ALREADY_ACKNOWLEDGED,
                tracker.acknowledge(PLAYER));
    }

    @Test
    void missingAcknowledgementExpiresAtTheExplicitDeadline() {
        RingHandshakeTracker tracker = new RingHandshakeTracker();
        tracker.begin(PLAYER, 20L);

        assertTrue(tracker.expire(20L + RingHandshakeTracker.ACK_TIMEOUT_TICKS - 1L).isEmpty());
        assertEquals(java.util.Set.of(PLAYER),
                tracker.expire(20L + RingHandshakeTracker.ACK_TIMEOUT_TICKS));
        assertFalse(tracker.isAcknowledged(PLAYER));
        assertEquals(RingHandshakeTracker.AcknowledgementResult.UNEXPECTED,
                tracker.acknowledge(PLAYER));
    }

    @Test
    void reconnectAndDisconnectCannotReuseAcknowledgedState() {
        RingHandshakeTracker tracker = new RingHandshakeTracker();
        tracker.begin(PLAYER, 1L);
        tracker.acknowledge(PLAYER);
        tracker.begin(PLAYER, 100L);
        assertFalse(tracker.isAcknowledged(PLAYER));
        tracker.clear(PLAYER);
        assertEquals(RingHandshakeTracker.AcknowledgementResult.UNEXPECTED,
                tracker.acknowledge(PLAYER));
    }
}
