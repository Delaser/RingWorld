package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure model coverage for every layout class exercised through the creation
 * screen. Minecraft-owned widget rendering remains a client runtime check.
 */
class RingWorldCreationUiModelTest {
    @ParameterizedTest(name = "{0} produces a valid cost preview")
    @MethodSource("dev.ringworld.world.RingDimensionFixtures#playableLayouts")
    void validCreationLayoutsProduceBoundedCostPreviews(
            String name, int circumference, int width, int wallHeight) {
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(width, circumference), wallHeight);
        RingRenderProfile profile = RingRenderProfile.create(
                report.geometry(), 28 * 16.0);

        assertTrue(report.isValid(), name + ": " + report.errors());
        assertTrue(report.canonicalChunkCount() > 0);
        assertTrue(report.atlasCellCount() > 0);
        assertTrue(report.estimatedAtlasBytes() > 0);
        assertTrue(report.costEstimate().estimatedPregenerationSeconds() > 0);
        assertTrue(report.costEstimate().estimatedGeneratedWorldBytes() > 0);
        assertTrue(report.costEstimate().estimatedAtlasWireBytes() > 0);
        assertTrue(report.costEstimate().minimumAtlasTransferTicks() > 0);
        assertTrue(profile.textureColumns() > 0);
        assertTrue(profile.textureRows() > 0);
        assertTrue(profile.estimatedGpuTextureBytes() > 0);
        assertTrue(profile.estimatedGpuMeshBytes() > 0);
        assertTrue(profile.estimatedTextureBuildScratchBytes() > 0);
    }

    @Test
    void invalidCreationLayoutCannotBeApplied() {
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                new RingGeometry(320, 1_600), 400);

        assertFalse(report.isValid());
        assertTrue(report.errors().stream().anyMatch(
                error -> error.contains("wall top")));
        assertTrue(report.errors().stream().anyMatch(
                error -> error.contains("radial blocks")));
    }

    @Test
    void namedPresetsExposeTheRecommendedAndSafeSmallLayouts() {
        RingWorldCreationUiModel.Preset production =
                RingWorldCreationUiModel.PRODUCTION_RECOMMENDED;
        RingWorldCreationUiModel.Preset safeSmall = RingWorldCreationUiModel.SAFE_SMALL_TEST;

        assertEquals("Production (recommended)", production.label());
        assertEquals(RingWorldSettings.DEFAULT_CIRCUMFERENCE, production.circumferenceBlocks());
        assertEquals(RingWorldSettings.DEFAULT_WIDTH, production.widthBlocks());
        assertEquals(2_048, safeSmall.circumferenceBlocks());
        assertEquals(416, safeSmall.widthBlocks());
        assertTrue(RingWorldCreationUiModel.validate(
                Integer.toString(safeSmall.circumferenceBlocks()),
                Integer.toString(safeSmall.widthBlocks()),
                Integer.toString(safeSmall.wallHeightBlocks())).canApply());
    }

    @Test
    void parsingReportsEveryMalformedFieldTogether() {
        RingWorldCreationUiModel.Validation validation = RingWorldCreationUiModel.validate(
                "around", "", "160.5");

        assertFalse(validation.canApply());
        assertEquals(3, validation.errors().size());
        assertTrue(validation.errors().stream().anyMatch(error -> error.startsWith("Circumference")));
        assertTrue(validation.errors().stream().anyMatch(error -> error.startsWith("Width")));
        assertTrue(validation.errors().stream().anyMatch(error -> error.startsWith("Wall height")));
    }

    @Test
    void structuralValidationNamesEveryInvalidField() {
        RingWorldCreationUiModel.Validation validation = RingWorldCreationUiModel.validate(
                "1001", "255", "31");

        assertFalse(validation.canApply());
        assertEquals(5, validation.errors().size());
        assertTrue(validation.errors().stream().anyMatch(error -> error.equals("Circumference must be at least 1024 blocks.")));
        assertTrue(validation.errors().stream().anyMatch(error -> error.equals("Circumference must be a multiple of 16 blocks.")));
        assertTrue(validation.errors().stream().anyMatch(error -> error.equals("Width must be at least 256 blocks.")));
        assertTrue(validation.errors().stream().anyMatch(error -> error.equals("Width must be a multiple of 16 blocks.")));
        assertTrue(validation.errors().stream().anyMatch(error -> error.equals("Wall height must be at least 32 blocks.")));
    }

    @Test
    void crossFieldValidationKeepsAllReportErrorsVisible() {
        RingWorldCreationUiModel.Validation validation = RingWorldCreationUiModel.validate(
                "1600", "320", "400");

        assertFalse(validation.canApply());
        assertTrue(validation.messages().stream().anyMatch(error -> error.contains("wall top")));
        assertTrue(validation.messages().stream().anyMatch(error -> error.contains("radial blocks")));
    }

    @Test
    void validPreviewAndConfirmationExplainFirstWorldPersistenceAndMonuments() {
        RingWorldCreationUiModel.Validation validation = RingWorldCreationUiModel.validate(
                "16384", "256", "160");

        assertTrue(validation.canApply());
        assertEquals(4, validation.summaryLines().size());
        assertTrue(validation.summaryLines().getFirst().contains("16,384 blocks around"));
        String confirmation = RingWorldCreationUiModel.confirmationCopy(validation.report(), true);
        assertTrue(confirmation.contains("Ocean monument guarantee: On"));
        assertTrue(confirmation.contains("cannot be changed here"));
        assertTrue(RingWorldCreationUiModel.MONUMENT_COPY.contains("valid ocean-monument location"));
    }
}
