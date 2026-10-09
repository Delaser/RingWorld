package dev.ringworld.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingAtlasAdaptiveConcurrencyTest {
    @Test void sustainedFramePressureStepsDownWithoutReactingToOneSample() {
        var policy = new RingAtlasAdaptiveConcurrency();
        assertEquals(4, policy.limit());
        assertEquals(4, policy.observe(0, 5, 40, 60));
        assertEquals(2, policy.observe(1_000_000_000L, 5, 40, 60));
        assertEquals(2, policy.observe(2_000_000_000L, 5, 40, 60));
        assertEquals(1, policy.observe(3_000_000_000L, 5, 40, 60));
    }
    @Test void frameCapAndBriefDipsDoNotThrottle() {
        var policy = new RingAtlasAdaptiveConcurrency();
        policy.observe(0, 5, 40, 60);
        policy.observe(1_000_000_000L, 5, 60, 60);
        policy.observe(2_000_000_000L, 5, 40, 60);
        assertEquals(4, policy.limit());
        for (long second = 3; second < 20; second++) policy.observe(second*1_000_000_000L, 5, 30, 30);
        assertEquals(4, policy.limit());
    }
    @Test void severeFrameOrTickPressureDropsStraightToOne() {
        assertEquals(1, new RingAtlasAdaptiveConcurrency().observe(0, 5, 20, 60));
        assertEquals(1, new RingAtlasAdaptiveConcurrency().observe(0, 100, -1, -1));
    }
    @Test void dedicatedServerBacksOffFromTicksWithoutFrameFeedback() {
        var policy = new RingAtlasAdaptiveConcurrency();
        policy.observe(0, 45, -1, -1);
        assertEquals(2, policy.observe(1_000_000_000L, 45, -1, -1));
        policy.observe(2_000_000_000L, 45, -1, -1);
        assertEquals(1, policy.observe(3_000_000_000L, 45, -1, -1));
    }
    @Test void healthyRecoveryNeedsTenSecondsForEachStep() {
        var policy = new RingAtlasAdaptiveConcurrency();
        policy.observe(0, 150, -1, -1);
        for (long second = 1; second <= 9; second++) assertEquals(1, policy.observe(second*1_000_000_000L, 5, 60, 60));
        assertEquals(2, policy.observe(10_000_000_000L, 5, 60, 60));
        for (long second = 11; second <= 19; second++) assertEquals(2, policy.observe(second*1_000_000_000L, 5, 60, 60));
        assertEquals(4, policy.observe(20_000_000_000L, 5, 60, 60));
        assertEquals(4, policy.observe(21_000_000_000L, 5, 60, 60));
    }
    @Test void repeatedCallsAndBorderlinePerformanceCannotAccelerateRecovery() {
        var policy = new RingAtlasAdaptiveConcurrency();
        policy.observe(0, 150, -1, -1);
        for (int i=1; i<100; i++) policy.observe(i*1_000_000L, 5, 60, 60);
        assertEquals(1, policy.limit());
        for (long second=1; second<=20; second++) policy.observe(second*1_000_000_000L, 30, 53, 60);
        assertEquals(1, policy.limit());
    }
    @Test void missingOrInvalidMeasurementsAreNotHealthyRecoveryEvidence() {
        var policy = new RingAtlasAdaptiveConcurrency();
        policy.observe(0, 150, -1, -1);
        for (long second=1; second<=20; second++) policy.observe(second*1_000_000_000L, Double.NaN, Double.NaN, -1);
        assertEquals(1, policy.limit());
    }
}
