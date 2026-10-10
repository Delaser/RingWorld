package dev.ringworld.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingAtlasCaptureBudgetTest {
    @Test void stopsBeforeAnotherChunkWhenTheTimeBudgetIsUsed() {
        assertTrue(RingAtlasCaptureBudget.allows(1, 4, 1_999_999));
        assertFalse(RingAtlasCaptureBudget.allows(1, 4, 2_000_000));
        assertFalse(RingAtlasCaptureBudget.allows(2, 4, 20_000_000));
    }

    @Test void alwaysMakesOneChunkOfProgressButCapsEvenAnEightRequestOverride() {
        assertTrue(RingAtlasCaptureBudget.allows(0, 1, 20_000_000));
        assertFalse(RingAtlasCaptureBudget.allows(1, 1, 0));
        assertTrue(RingAtlasCaptureBudget.allows(3, 8, 0));
        assertFalse(RingAtlasCaptureBudget.allows(4, 8, 0));
    }

    @Test void captureCeilingFollowsTickPressureAndSlowRecovery() {
        var policy = new RingAtlasAdaptiveConcurrency();
        assertTrue(RingAtlasCaptureBudget.allows(3, policy.limit(), 0));
        policy.observe(0, 45, -1, -1);
        policy.observe(1_000_000_000L, 45, -1, -1);
        assertTrue(RingAtlasCaptureBudget.allows(1, policy.limit(), 0));
        assertFalse(RingAtlasCaptureBudget.allows(2, policy.limit(), 0));
        policy.observe(2_000_000_000L, 100, -1, -1);
        assertFalse(RingAtlasCaptureBudget.allows(1, policy.limit(), 0));
        for (long second = 3; second < 13; second++) policy.observe(second * 1_000_000_000L, 5, -1, -1);
        assertTrue(RingAtlasCaptureBudget.allows(1, policy.limit(), 0));
        assertFalse(RingAtlasCaptureBudget.allows(2, policy.limit(), 0));
    }

    @Test void integratedFramePressureAlsoReducesCaptureAndAutoResetsTheCeiling() {
        var policy = new RingAtlasAdaptiveConcurrency();
        policy.observe(0, 5, 20, 60);
        assertFalse(RingAtlasCaptureBudget.allows(1, policy.limit(), 0));
        policy.useAutomatic();
        assertTrue(RingAtlasCaptureBudget.allows(3, policy.limit(), 0));
    }
}
