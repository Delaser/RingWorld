package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingEntityTrackingTest {
    @Test
    void vanillaTrackedEntityRemainsEligible() {
        assertTrue(RingEntityTracking.shouldRemainPaired(true, false, false, false));
    }

    @Test
    void pendingCanonicalFoldPreservesExistingPairingInsideWatchWindow() {
        assertTrue(RingEntityTracking.shouldRemainPaired(false, true, true, false));
    }

    @Test
    void pendingChunkCannotStartANewPairing() {
        assertFalse(RingEntityTracking.shouldRemainPaired(false, false, true, false));
    }

    @Test
    void leavingPeriodicWatchWindowRemovesExistingPairing() {
        assertFalse(RingEntityTracking.shouldRemainPaired(false, true, false, false));
    }

    @Test
    void exteriorVoidDoesNotRequireTerrainDeliveryForInitialPairing() {
        assertTrue(RingEntityTracking.shouldRemainPaired(false, false, false, true));
        assertTrue(RingEntityTracking.shouldRemainPaired(false, true, false, true));
    }

    @Test
    void exteriorWatchRemainsBoundedAndPeriodic() {
        assertTrue(RingChunkFilter.isWithinRingDistance(128, 0, 3, 5, -4, 3, 127, 4, true));
        assertTrue(RingChunkTopology.isWithinVanillaDistance(128, 0, 3, 5, 127, 4, true));
        assertFalse(RingChunkTopology.isWithinVanillaDistance(128, 0, 3, 5, 0, 20, true));
        assertFalse(RingChunkTopology.isWithinVanillaDistance(128, 0, 3, 5, 64, 4, true));
        assertTrue(RingChunkFilter.isWithinRingDistance(128, 127, -4, 5, -4, 3, 0, -5, true));
        assertTrue(RingChunkTopology.isWithinVanillaDistance(128, 127, -4, 5, 0, -5, true));
    }
}
