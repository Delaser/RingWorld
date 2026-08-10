package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void namedPresetsExposeSmallMediumAndLargeLayouts() {
        RingWorldCreationUiModel.Preset small = RingWorldCreationUiModel.SMALL;
        RingWorldCreationUiModel.Preset medium = RingWorldCreationUiModel.MEDIUM;
        RingWorldCreationUiModel.Preset large = RingWorldCreationUiModel.LARGE;

        assertEquals("Small", small.label());
        assertEquals(2_048, small.circumferenceBlocks());
        assertEquals(128, small.widthBlocks());
        assertEquals("Medium", medium.label());
        assertEquals(RingWorldSettings.DEFAULT_CIRCUMFERENCE, medium.circumferenceBlocks());
        assertEquals(RingWorldSettings.DEFAULT_WIDTH, medium.widthBlocks());
        assertEquals("Large", large.label());
        assertEquals(32_768, large.circumferenceBlocks());
        assertEquals(512, large.widthBlocks());
        assertTrue(RingWorldCreationUiModel.validate(
                Integer.toString(small.circumferenceBlocks()),
                Integer.toString(small.widthBlocks()),
                Integer.toString(small.wallHeightBlocks())).canApply());
        assertTrue(RingWorldCreationUiModel.validate(
                Integer.toString(small.circumferenceBlocks()),
                Integer.toString(small.widthBlocks()),
                Integer.toString(small.wallHeightBlocks()))
                .metricLines().getLast().startsWith("Small is experimental:"));
        assertTrue(RingWorldCreationUiModel.validate(
                Integer.toString(large.circumferenceBlocks()),
                Integer.toString(large.widthBlocks()),
                Integer.toString(large.wallHeightBlocks()))
                .metricLines().getLast().startsWith("High cost:"));
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
                "1001", "127", "31");

        assertFalse(validation.canApply());
        assertEquals(5, validation.errors().size());
        assertTrue(validation.errors().stream().anyMatch(error -> error.equals(
                "Circumference must be at least 2048 blocks for a new world.")));
        assertTrue(validation.errors().stream().anyMatch(error -> error.equals("Circumference must be a multiple of 16 blocks.")));
        assertTrue(validation.errors().stream().anyMatch(error -> error.equals("Width must be at least 128 blocks.")));
        assertTrue(validation.errors().stream().anyMatch(error -> error.equals("Width must be a multiple of 16 blocks.")));
        assertTrue(validation.errors().stream().anyMatch(error -> error.equals("Wall height must be at least 32 blocks.")));
    }

    @Test
    void crossFieldValidationKeepsAllReportErrorsVisible() {
        RingWorldCreationUiModel.Validation validation = RingWorldCreationUiModel.validate(
                "2048", "320", "400");

        assertFalse(validation.canApply());
        assertTrue(validation.messages().stream().anyMatch(error -> error.contains("wall top")));
        assertTrue(validation.messages().stream().anyMatch(error -> error.contains("radial blocks")));
    }

    @Test
    void metricsAndConfirmationExplainFirstWorldPersistenceAndMonuments() {
        RingWorldCreationUiModel.Validation validation = RingWorldCreationUiModel.validate(
                "16384", "256", "160");

        assertTrue(validation.canApply());
        assertEquals(8, validation.metricLines().size());
        assertEquals("Lap: 16,384÷4.317 = 1h 03m",
                validation.metricLines().getFirst());
        assertTrue(validation.metricLines().stream().anyMatch(
                line -> line.equals("Radius: 16,384÷2π ≈ 2,607.6; D ≈ 5,215.2")));
        assertTrue(validation.metricLines().stream().anyMatch(
                line -> line.equals("Opposite width: 2atan(256÷10,430) = 2.81°")));
        assertTrue(validation.metricLines().stream().anyMatch(line -> line.contains("1,024×16")));
        assertTrue(validation.metricLines().stream().anyMatch(line -> line.contains("2,048×32")));
        assertTrue(validation.metricLines().stream().anyMatch(
                line -> line.equals("Heights: rim top Y95; clouds Y104")));
        String confirmation = RingWorldCreationUiModel.confirmationCopy(validation.report(), true);
        assertTrue(confirmation.contains("Monument: On"));
        assertTrue(confirmation.contains("locks on first load"));
        assertTrue(RingWorldCreationUiModel.MONUMENT_COPY.contains("result is saved"));

        RingWorldCreationUiModel.Validation visualWarningOnly =
                RingWorldCreationUiModel.validate("32768", "128", "160");
        assertTrue(visualWarningOnly.canApply());
        assertFalse(visualWarningOnly.report().warnings().isEmpty());
        assertFalse(visualWarningOnly.report().hasHighGenerationCost());
        assertTrue(visualWarningOnly.metricLines().stream()
                .anyMatch(line -> line.startsWith("Pregen:")));
    }

    @Test
    void creationAdmissionRejectsLegacySizedCircumferenceButGeometryRetainsIt() {
        assertFalse(RingWorldCreationUiModel.validate("2016", "128", "160").canApply());
        assertThrows(IllegalArgumentException.class,
                () -> RingWorldConfig.validateNewWorldLayout(128, 2016, 160));
        assertDoesNotThrow(() -> RingWorldConfig.validateNewWorldLayout(128, 2_048, 160));
        assertTrue(RingDimensionReport.forVanillaOverworld(
                new RingGeometry(128, 1_024), 160).geometry().circumferenceBlocks() == 1_024);
    }

    @Test
    void smallPresetExplainsTheDeterministicMonumentLimitation() {
        RingGeometry small = new RingGeometry(128, 2_048);
        assertFalse(RingWorldCreationUiModel.monumentAvailable(small));
        assertTrue(RingWorldCreationUiModel.monumentAvailabilityCopy(small).contains("unavailable"));
        assertEquals("Monument: unavailable", RingWorldCreationUiModel.monumentChoice(true, small));
        assertFalse(RingWorldConfig.effectiveOceanMonumentRequest(small, true));
        assertTrue(RingWorldConfig.effectiveOceanMonumentRequest(
                new RingGeometry(160, 2_048), true));
    }

}
