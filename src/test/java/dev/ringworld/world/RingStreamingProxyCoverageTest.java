package dev.ringworld.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RingStreamingProxyCoverageTest {
    @Test
    void makesTheAtlasOpaqueWheneverTheDrawableWindowIsIncomplete() {
        assertEquals(new RingStreamingProxyCoverage.Span(0.0, 0.0),
                RingStreamingProxyCoverage.span(16, false));
    }

    @Test
    void publishesANoOpBeyondTheRequestedRadiusOnceComplete() {
        assertEquals(new RingStreamingProxyCoverage.Span(256.0, 256.0),
                RingStreamingProxyCoverage.span(16, true));
    }

    @Test
    void completeSpanRemainsANoOpAtMinimumViewDistance() {
        assertEquals(new RingStreamingProxyCoverage.Span(32.0, 32.0),
                RingStreamingProxyCoverage.span(2, true));
    }

    @Test
    void completeFallbackDoesNotAlterExperimentNineteenAtAnySupportedRadius() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        for (int chunks : new int[] {2, 3, 4, 6, 12, 16, 28, 32}) {
            RingRenderProfile profile = RingRenderProfile.create(geometry, chunks * 16.0);
            RingStreamingProxyCoverage.Span fallback =
                    RingStreamingProxyCoverage.span(chunks, true);
            double legacyBaseStart = 2.0 * profile.proxyFadeStartBlocks()
                    - profile.liveFadeStartBlocks();
            double legacyBaseEnd = profile.proxyFadeStartBlocks();
            for (int sample = 0; sample <= 2_000; sample++) {
                double distance = profile.effectiveViewDistanceBlocks() * sample / 1_000.0;
                double base = smootherstep(legacyBaseStart, legacyBaseEnd, distance);
                double combined = Math.max(base, smootherstep(
                        fallback.fadeStartBlocks(), fallback.opaqueFromBlocks(), distance));
                assertEquals(base, combined, 1.0e-12,
                        "complete fallback changed VD" + chunks + " at " + distance);
            }
        }
    }

    @Test
    void incompleteFallbackIsOpaqueImmediatelyAwayFromTheCameraPoint() {
        RingStreamingProxyCoverage.Span fallback =
                RingStreamingProxyCoverage.span(32, false);
        assertEquals(1.0, smootherstep(
                fallback.fadeStartBlocks(), fallback.opaqueFromBlocks(), 0.001), 0.0);
    }

    @Test
    void rejectsImpossibleAvailabilityInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> RingStreamingProxyCoverage.span(0, false));
    }

    @Test
    void adjacentTransferIsSafeOnlyWhenTheFixedProxyCoversTheNewFringe() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        for (int chunks : new int[] {2, 3}) {
            RingRenderProfile profile = RingRenderProfile.create(
                    geometry, chunks * 16.0);
            assertFalse(RingStreamingProxyCoverage.coversAdjacentNewFringe(
                    chunks, profile.proxyFadeStartBlocks()), "VD" + chunks);
        }
        for (int chunks : new int[] {4, 6, 12, 16, 28, 32}) {
            RingRenderProfile profile = RingRenderProfile.create(
                    geometry, chunks * 16.0);
            assertTrue(RingStreamingProxyCoverage.coversAdjacentNewFringe(
                    chunks, profile.proxyFadeStartBlocks()), "VD" + chunks);
        }
    }

    @Test
    void adjacentTransferUsesExactTrackingViewDiagonalLowerBounds() {
        double diagonalFringeBlocks = Math.sqrt(8.0) * 16.0;
        assertTrue(RingStreamingProxyCoverage.coversAdjacentNewFringe(
                4, diagonalFringeBlocks));
        assertFalse(RingStreamingProxyCoverage.coversAdjacentNewFringe(
                4, Math.nextUp(diagonalFringeBlocks)));

        double viewDistanceFiveFringeBlocks = Math.sqrt(13.0) * 16.0;
        assertTrue(RingStreamingProxyCoverage.coversAdjacentNewFringe(
                5, viewDistanceFiveFringeBlocks));
        assertFalse(RingStreamingProxyCoverage.coversAdjacentNewFringe(
                5, Math.nextUp(viewDistanceFiveFringeBlocks)));
    }

    @Test
    void rejectsImpossibleAdjacentTransferInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> RingStreamingProxyCoverage.coversAdjacentNewFringe(0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> RingStreamingProxyCoverage.coversAdjacentNewFringe(4, -1.0));
        assertThrows(IllegalArgumentException.class,
                () -> RingStreamingProxyCoverage.coversAdjacentNewFringe(
                        4, Double.NaN));
    }

    @Test
    void identifiesSectionBoxesInsideTheNonOpaqueProxyRegion() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        assertTrue(RingStreamingProxyCoverage.intersectsNonOpaqueProxyRegion(
                geometry, 8.0, 8.0, 0.0, 16.0, 0.0, 16.0, 32.0));
        assertTrue(RingStreamingProxyCoverage.intersectsNonOpaqueProxyRegion(
                geometry, 0.0, 0.0, 3.0, 19.0, 4.0, 20.0, 6.0));
        assertFalse(RingStreamingProxyCoverage.intersectsNonOpaqueProxyRegion(
                geometry, 0.0, 0.0, 3.0, 19.0, 4.0, 20.0, 5.0));
    }

    @Test
    void treatsTheExactOpaqueBoundaryAsSafeAndUsesNearestPeriodicX() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        assertFalse(RingStreamingProxyCoverage.intersectsNonOpaqueProxyRegion(
                geometry, 0.0, 0.0, 32.0, 48.0, 0.0, 16.0, 32.0));
        assertTrue(RingStreamingProxyCoverage.intersectsNonOpaqueProxyRegion(
                geometry, 0.0, 0.0, 31.999, 48.0,
                0.0, 16.0, 32.0));
        assertTrue(RingStreamingProxyCoverage.intersectsNonOpaqueProxyRegion(
                geometry, 16_382.0, 8.0, 0.0, 16.0, 0.0, 16.0, 3.0));
        assertFalse(RingStreamingProxyCoverage.intersectsNonOpaqueProxyRegion(
                geometry, 16_382.0, 8.0, 32.0, 48.0, 0.0, 16.0, 3.0));
    }

    private static double smootherstep(double edge0, double edge1, double value) {
        double t = Math.clamp((value - edge0) / Math.max(0.0001, edge1 - edge0),
                0.0, 1.0);
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }
}
