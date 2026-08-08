package dev.ringworld.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingMultiplayerReadinessGateTest {
    @Test
    void requiresAConsecutiveNormallyPacedWindow() {
        RingMultiplayerReadinessGate gate = new RingMultiplayerReadinessGate();
        long now = 1_000L;
        assertEquals(RingMultiplayerReadinessGate.Result.WARMING, gate.observe(now));

        for (int tick = 1; tick < RingMultiplayerReadinessGate.REQUIRED_CONSECUTIVE_ON_TIME_TICKS; tick++) {
            now += 50_000_000L;
            assertEquals(RingMultiplayerReadinessGate.Result.WARMING, gate.observe(now));
        }

        now += 50_000_000L;
        assertEquals(RingMultiplayerReadinessGate.Result.READY, gate.observe(now));
    }

    @Test
    void coldStallResetsTheConsecutiveWindow() {
        RingMultiplayerReadinessGate gate = new RingMultiplayerReadinessGate();
        long now = 1_000L;
        assertEquals(RingMultiplayerReadinessGate.Result.WARMING, gate.observe(now));
        for (int tick = 1; tick < 20; tick++) {
            now += 50_000_000L;
            assertEquals(RingMultiplayerReadinessGate.Result.WARMING, gate.observe(now));
        }

        now += 22_700_000_000L;
        assertEquals(RingMultiplayerReadinessGate.Result.WARMING, gate.observe(now));
        assertEquals(0, gate.consecutiveOnTimeTicks());
        assertEquals(22_700_000_000L, gate.longestTickIntervalNanos());

        for (int tick = 1; tick < RingMultiplayerReadinessGate.REQUIRED_CONSECUTIVE_ON_TIME_TICKS; tick++) {
            now += 50_000_000L;
            assertEquals(RingMultiplayerReadinessGate.Result.WARMING, gate.observe(now));
        }
        now += 50_000_000L;
        assertEquals(RingMultiplayerReadinessGate.Result.READY, gate.observe(now));
    }

    @Test
    void timesOutWhenTheWindowNeverStabilizes() {
        RingMultiplayerReadinessGate gate = new RingMultiplayerReadinessGate();
        long now = 1_000L;
        assertEquals(RingMultiplayerReadinessGate.Result.WARMING, gate.observe(now));
        now += RingMultiplayerReadinessGate.MAXIMUM_WAIT_NANOS;
        assertEquals(RingMultiplayerReadinessGate.Result.TIMED_OUT, gate.observe(now));
    }

    @Test
    void independentPostPortalBarrierRequiresItsOwnStableWindow() {
        RingMultiplayerReadinessGate preSeam = new RingMultiplayerReadinessGate();
        RingMultiplayerReadinessGate postPortal = new RingMultiplayerReadinessGate();
        long now = 1_000L;
        assertEquals(RingMultiplayerReadinessGate.Result.WARMING, preSeam.observe(now));
        for (int tick = 1; tick <= RingMultiplayerReadinessGate.REQUIRED_CONSECUTIVE_ON_TIME_TICKS; tick++) {
            now += 50_000_000L;
            preSeam.observe(now);
        }
        assertEquals(RingMultiplayerReadinessGate.Result.READY, preSeam.observe(now));

        assertEquals(RingMultiplayerReadinessGate.Result.WARMING, postPortal.observe(now));
        now += 2_500_000_000L;
        assertEquals(RingMultiplayerReadinessGate.Result.WARMING, postPortal.observe(now));
        assertEquals(0, postPortal.consecutiveOnTimeTicks());

        for (int tick = 1; tick < RingMultiplayerReadinessGate.REQUIRED_CONSECUTIVE_ON_TIME_TICKS; tick++) {
            now += 50_000_000L;
            assertEquals(RingMultiplayerReadinessGate.Result.WARMING, postPortal.observe(now));
        }
        now += 50_000_000L;
        assertEquals(RingMultiplayerReadinessGate.Result.READY, postPortal.observe(now));
    }
}
