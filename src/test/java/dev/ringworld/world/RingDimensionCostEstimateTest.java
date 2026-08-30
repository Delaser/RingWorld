package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingDimensionCostEstimateTest {
    @Test
    void productionReferenceRetainsMeasuredAndExactTransportCosts() {
        RingDimensionCostEstimate estimate = RingDimensionCostEstimate.estimate(
                new RingGeometry(256, 16_384), 8);

        assertEquals(817L, estimate.estimatedPregenerationSeconds());
        assertEquals(177_523_917L, estimate.estimatedGeneratedWorldBytes());
        assertEquals(524_800L, estimate.estimatedAtlasWireBytes());
        assertEquals(32L, estimate.minimumAtlasTransferTicks());
    }

    @Test
    void estimateScalesUpWithoutFloatingPointOrOverflowAtSupportedMaximum() {
        RingDimensionCostEstimate estimate = RingDimensionCostEstimate.estimate(
                new RingGeometry(RingWorldSettings.MIN_WIDTH,
                        RingDimensionReport.MAX_AXIS_BLOCKS), 8);

        assertTrue(estimate.estimatedPregenerationSeconds() > 0L);
        assertTrue(estimate.estimatedGeneratedWorldBytes() > 0L);
        assertEquals(1_024L, estimate.minimumAtlasTransferTicks());
        assertThrows(IllegalArgumentException.class,
                () -> RingDimensionCostEstimate.estimate(
                        new RingGeometry(256, 16_384), 3));
    }
}
