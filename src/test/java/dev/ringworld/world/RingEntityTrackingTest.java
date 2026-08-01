package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingEntityTrackingTest {
    @Test
    void vanillaTrackedEntityRemainsEligible() {
        assertTrue(RingEntityTracking.shouldRemainPaired(true, false, false));
    }

    @Test
    void pendingCanonicalFoldPreservesExistingPairingInsideWatchWindow() {
        assertTrue(RingEntityTracking.shouldRemainPaired(false, true, true));
    }

    @Test
    void pendingChunkCannotStartANewPairing() {
        assertFalse(RingEntityTracking.shouldRemainPaired(false, false, true));
    }

    @Test
    void leavingPeriodicWatchWindowRemovesExistingPairing() {
        assertFalse(RingEntityTracking.shouldRemainPaired(false, true, false));
    }
}
