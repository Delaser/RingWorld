package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingDimensionReportTest {
    @Test
    void smallPresetHasExactUsableBandAndMeasuredCosts() {
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(128, 2_048), 160);

        assertTrue(report.isValid(), report.errors().toString());
        assertEquals(118, report.playableInteriorBlocks());
        assertEquals(241_664L, report.playableInteriorAreaBlocks());
        assertEquals(256, report.atlasColumns());
        assertEquals(16, report.atlasRows());
        assertEquals(4_096L, report.atlasCellCount());
        assertEquals(28_672L, report.estimatedAtlasBytes());
        assertEquals(52L, report.costEstimate().estimatedPregenerationSeconds());
        assertEquals(11_095_245L, report.costEstimate().estimatedGeneratedWorldBytes());
        assertEquals(11.2, report.oppositeAngularWidthDegrees(), 0.1);
        assertEquals(651.9, report.diameterBlocks(), 0.1);
    }

    @Test
    void safeSmallReferencePreservesTheOldVisualWidthWithoutCrossingTheCenter() {
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(416, 2_048), 160);

        assertTrue(report.isValid(), report.errors().toString());
        assertEquals(3_328L, report.canonicalChunkCount());
        assertEquals(13_312L, report.atlasCellCount());
        assertEquals(8L, report.costEstimate().minimumAtlasTransferTicks());
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
        assertEquals(16_384L, report.canonicalChunkCount());
        assertEquals(65_536L, report.atlasCellCount());
        assertEquals(2_048, report.atlasColumns());
        assertEquals(32, report.atlasRows());
        assertEquals(246, report.playableInteriorBlocks());
        assertEquals(4_030_464L, report.playableInteriorAreaBlocks());
        assertEquals(817L, report.costEstimate().estimatedPregenerationSeconds());
        assertEquals(177_523_917L, report.costEstimate().estimatedGeneratedWorldBytes());
        assertEquals(459_264L, report.costEstimate().estimatedAtlasWireBytes());
        assertEquals(32L, report.costEstimate().minimumAtlasTransferTicks());
        assertTrue(report.oppositeAngularWidthDegrees() > 2.8
                && report.oppositeAngularWidthDegrees() < 2.9);
        assertTrue(report.warnings().isEmpty(), report.warnings().toString());
        assertTrue(report.radialClearanceAtHighestPlane() > 2_350.0);
    }

    @Test
    void largePresetRetainsBoundedAtlasCostsButWarnsForGenerationScale() {
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(512, 32_768), 160);

        assertTrue(report.isValid(), report.errors().toString());
        assertEquals(65_536L, report.canonicalChunkCount());
        assertEquals(4_096, report.atlasColumns());
        assertEquals(64, report.atlasRows());
        assertEquals(262_144L, report.atlasCellCount());
        assertEquals(1_835_008L, report.estimatedAtlasBytes());
        assertEquals(3_268L, report.costEstimate().estimatedPregenerationSeconds());
        assertEquals(710_095_668L, report.costEstimate().estimatedGeneratedWorldBytes());
        assertTrue(report.warnings().stream().anyMatch(
                warning -> warning.contains("measured-reference full generation")));
    }

    private static Stream<Arguments> productionAtlasFidelityCandidates() {
        return Stream.of(
                Arguments.of(8, 65_536L, 458_752L),
                Arguments.of(4, 262_144L, 1_835_008L),
                Arguments.of(2, 1_048_576L, 7_340_032L),
                Arguments.of(1, 4_194_304L, 29_360_128L));
    }

    @ParameterizedTest(name = "production step {0} has checked atlas budgets")
    @MethodSource("productionAtlasFidelityCandidates")
    void productionAtlasFidelityCandidatesHaveCheckedCosts(
            int sampleStep, long expectedCells, long expectedRawBytes) {
        RingDimensionReport report = RingDimensionReport.evaluate(
                new RingGeometry(256, 16_384), 160,
                RingDimensionReport.VANILLA_OVERWORLD_BOTTOM_Y,
                RingDimensionReport.VANILLA_OVERWORLD_TOP_Y_EXCLUSIVE,
                RingGenerationBoundary.RIM_THICKNESS, sampleStep);

        assertTrue(report.isValid(), report.errors().toString());
        assertEquals(expectedCells, report.atlasCellCount());
        assertEquals(expectedRawBytes, report.estimatedAtlasBytes());
        assertEquals(sampleStep == 1, report.warnings().stream()
                .anyMatch(warning -> warning.contains("terrain atlas")));
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

    @Test
    void alignedMinimumPlayableCircumferenceIsAcceptedButTheStructuralMinimumIsNot() {
        RingDimensionReport playable = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(256, 2_016), 160);
        RingDimensionReport structuralOnly = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(256, RingWorldSettings.MIN_CIRCUMFERENCE), 160);

        assertTrue(playable.isValid(), playable.errors().toString());
        assertFalse(structuralOnly.isValid());
        assertTrue(structuralOnly.errors().stream().anyMatch(error -> error.contains("radial blocks")));
    }

    @Test
    void maximumTechnicalCircumferenceWithA256BlockBandRemainsAValidatedWarningCase() {
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(256, RingDimensionReport.MAX_AXIS_BLOCKS),
                160);

        assertTrue(report.isValid(), report.errors().toString());
        assertEquals(4_194_304L, report.atlasCellCount());
        assertTrue(report.warnings().stream().anyMatch(error -> error.contains("pregeneration")));
        assertTrue(report.warnings().stream().anyMatch(error -> error.contains("terrain atlas")));
    }

    @Test
    void broadCustomRingWarnsFromMeasuredGenerationAndDiskCosts() {
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(4_096, 16_384), 160);

        assertTrue(report.isValid(), report.errors().toString());
        assertEquals(262_144L, report.canonicalChunkCount());
        assertTrue(report.costEstimate().estimatedPregenerationSeconds() > 3L * 3_600L);
        assertTrue(report.costEstimate().estimatedGeneratedWorldBytes() > 2L * 1_024L * 1_024L * 1_024L);
        assertTrue(report.warnings().stream().anyMatch(
                warning -> warning.contains("measured-reference full generation")));
    }

    @Test
    void customWallHeightMovesBothWallAndCloudBase() {
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(640, 4_096), 192);

        assertTrue(report.isValid(), report.errors().toString());
        assertEquals(128, report.wallTopYExclusive());
        assertEquals(136, report.cloudBaseY());
    }

    private static Stream<Arguments> invalidStructuralLayouts() {
        return Stream.of(
                Arguments.of("width alignment", 2_048, 257),
                Arguments.of("circumference alignment", 2_017, 256),
                Arguments.of("width below supported minimum", 2_048, 112));
    }

    @ParameterizedTest(name = "{0} is rejected before creating a layout")
    @MethodSource("invalidStructuralLayouts")
    void invalidStructuralLayoutsAreRejected(String name, int circumference, int width) {
        assertThrows(IllegalArgumentException.class,
                () -> new RingGeometry(width, circumference));
    }
}
