package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
