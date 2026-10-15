package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingEntityTrackingTest {
    @Test
    void exteriorEntitiesCanPairOnlyInsideThePeriodicWatchDistance() {
        assertTrue(RingEntityTracking.shouldRemainPaired(false, false, false, true));
        assertFalse(RingEntityTracking.shouldRemainPaired(false, false, false, false));
        assertTrue(RingChunkFilter.isWithinRingDistance(128, 0, 3, 5, -4, 3, 127, 4, true));
        assertTrue(RingChunkFilter.isWithinRingDistance(128, 127, -4, 5, -4, 3, 0, -5, true));
        assertFalse(RingChunkFilter.isWithinRingDistance(128, 0, 3, 5, -4, 3, 0, 20, true));
        assertFalse(RingChunkFilter.isWithinRingDistance(128, 0, 3, 5, -4, 3, 64, 4, true));
    }

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
