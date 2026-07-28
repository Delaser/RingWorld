package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingDimensionReportTest {
    @Test
    void safeSmallReferencePreservesTheOldVisualWidthWithoutCrossingTheCenter() {
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(416, 2_048), 160);

        assertTrue(report.isValid(), report.errors().toString());
        assertEquals(3_328L, report.canonicalChunkCount());
        assertEquals(13_312L, report.atlasCellCount());
        assertTrue(report.radialClearanceAtHighestPlane() > 69.0);
        assertTrue(report.oppositeAngularWidthDegrees() > 35.0
                && report.oppositeAngularWidthDegrees() < 36.0);
        assertEquals(96, report.wallTopYExclusive());
        assertEquals(104, report.cloudBaseY());
    }

    @Test
    void legacyOneHundredChunkRingFailsFullHeightClearance() {
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(320, 1_600), 160);

        assertFalse(report.isValid());
        assertTrue(report.radialClearanceAtHighestPlane() < 0.0);
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("radial blocks")));
    }

    @Test
    void productionDefaultHasExpectedBoundedCosts() {
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(
                        RingWorldSettings.DEFAULT_WIDTH,
                        RingWorldSettings.DEFAULT_CIRCUMFERENCE),
                RingWorldSettings.DEFAULT_WALL_HEIGHT);

        assertTrue(report.isValid(), report.errors().toString());
        assertEquals(256, report.geometry().widthBlocks());
        assertEquals(15_552L, report.canonicalChunkCount());
        assertEquals(62_208L, report.atlasCellCount());
        assertTrue(report.oppositeAngularWidthDegrees() > 2.9
                && report.oppositeAngularWidthDegrees() < 3.0);
        assertTrue(report.warnings().isEmpty(), report.warnings().toString());
        assertTrue(report.radialClearanceAtHighestPlane() > 2_200.0);
    }

    @Test
    void minimumCircumferenceIsChunkAlignedAndIncludesClearance() {
        assertEquals(2_016, RingDimensionReport.minimumCircumferenceBlocks(
                320, RingGeometry.SURFACE_Y, 64));
    }

    @Test
    void excessiveAtlasIsRejectedBeforeAllocation() {
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(1_048_576, 1_048_576), 160);

        assertFalse(report.isValid());
        assertTrue(report.atlasCellCount() > Integer.MAX_VALUE);
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("terrain atlas")));
    }

    @Test
    void wallAboveBuildCeilingIsRejected() {
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(416, 2_048), 400);

        assertFalse(report.isValid());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("wall top")));
    }

    @Test
    void cloudBaseMustFitBelowTheBuildCeiling() {
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(416, 2_048), 380);

        assertFalse(report.isValid());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("cloud base")));
    }
}
