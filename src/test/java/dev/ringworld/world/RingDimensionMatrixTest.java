package dev.ringworld.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingDimensionMatrixTest {
    @ParameterizedTest(name = "{0}: C={1}, W={2}, wall={3}")
    @MethodSource("dev.ringworld.world.RingDimensionFixtures#playableLayouts")
    void geometryTopologyAndResourcesScaleTogether(String name,
                                                   int circumference,
                                                   int width,
                                                   int wallHeight) {
        RingGeometry geometry = new RingGeometry(width, circumference);
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                geometry, wallHeight);

        assertTrue(report.isValid(), name + ": " + report.errors());
        assertEquals(circumference / 16, geometry.circumferenceChunks());
        assertEquals(width / 16, geometry.widthChunks());
        assertEquals(0.0, geometry.shortestCircumferenceDelta(
                circumference - 0.25, -0.25), 1.0e-9);
        assertEquals(circumference - 1, geometry.wrapBlockX(-1));
        assertEquals(0, RingChunkCoordinates.wrapChunkX(
                geometry.circumferenceChunks(), geometry));
        assertEquals((long)geometry.circumferenceChunks() * geometry.widthChunks(),
                report.canonicalChunkCount());
        assertTrue(geometry.isInsideWidth(0.0));
        assertFalse(geometry.isInsideWidth(geometry.minWidthZ()));
        assertFalse(geometry.isInsideWidth(geometry.maxWidthZ()));
    }

    @ParameterizedTest(name = "{0}: C={1}, W={2}, wall={3}, view={4} chunks")
    @MethodSource("dev.ringworld.world.RingDimensionFixtures#visualLayouts")
    void renderProfileRemainsOrderedAndBounded(String name, int circumference,
                                               int width, int wallHeight,
                                               int viewDistanceChunks) {
        RingGeometry geometry = new RingGeometry(width, circumference);
        RingRenderProfile profile = RingRenderProfile.create(
                geometry, viewDistanceChunks * 16.0);

        assertTrue(profile.liveFadeStartBlocks() < profile.liveFadeEndBlocks());
        assertTrue(profile.proxyFadeStartBlocks() < profile.proxyFadeEndBlocks());
        assertTrue(profile.detailStartBlocks() < profile.detailEndBlocks());
        assertTrue(profile.liveFadeEndBlocks() <= circumference / 2.0);
        assertTrue(profile.proxyFadeEndBlocks() <= circumference / 2.0);
        assertTrue(profile.detailEndBlocks() <= circumference / 2.0);
        assertTrue(profile.textureColumns() <= RingRenderProfile.MAX_TEXTURE_COLUMNS);
        assertTrue(profile.textureRows() <= RingRenderProfile.MAX_TEXTURE_ROWS);
        assertTrue(profile.circumferenceSegments()
                <= RingRenderProfile.MAX_CIRCUMFERENCE_SEGMENTS);
        assertTrue(profile.widthBands() <= RingRenderProfile.MAX_WIDTH_BANDS);
        assertEquals((long)profile.circumferenceSegments()
                        * profile.widthBands() * 6L,
                profile.vertexCount());
    }

    @ParameterizedTest(name = "{0}: worldgen coordinate seam and finite-band limits")
    @MethodSource("dev.ringworld.world.RingDimensionFixtures#playableLayouts")
    void worldgenCoordinatesAndFiniteBandLimitsUseTheSelectedLayout(
            String name, int circumference, int width, int wallHeight) {
        RingGeometry geometry = new RingGeometry(width, circumference);
        RingNoiseCoordinates coordinates = RingNoiseCoordinates.forGeometry(geometry);

        assertEquals(coordinates.ringX(0), coordinates.ringX(circumference));
        assertEquals(coordinates.ringZ(0, 37), coordinates.ringZ(circumference, 37));
        assertEquals(width / 16, geometry.maxChunkZ() - geometry.minChunkZ() + 1);
        assertTrue(geometry.isExteriorChunkZ(geometry.minChunkZ() - 1));
        assertTrue(geometry.isExteriorChunkZ(geometry.maxChunkZ() + 1));
        assertEquals(-width / 2, geometry.minWidthZ());
        assertEquals(width / 2 - 1, geometry.maxWidthZ());
    }

    @ParameterizedTest(name = "{0}: spawn remains in the finite interior")
    @MethodSource("dev.ringworld.world.RingDimensionFixtures#playableLayouts")
    void spawnSelectionUsesOnlyTheProvidedGeometry(
            String name, int circumference, int width, int wallHeight) {
        RingGeometry geometry = new RingGeometry(width, circumference);
        int safeMargin = Math.min(32, Math.max(1, width / 4));
        int minimumSafeZ = geometry.minWidthZ() + safeMargin;
        int maximumSafeZ = geometry.maxWidthZ() - safeMargin;

        assertEquals(0, RingSpawnBounds.constrainInitialSpawnZ(geometry.minWidthZ(), geometry));
        assertEquals(0, RingSpawnBounds.constrainInitialSpawnZ(geometry.maxWidthZ(), geometry));
        assertEquals(minimumSafeZ,
                RingSpawnBounds.constrainInitialSpawnZ(minimumSafeZ, geometry));
        assertEquals(maximumSafeZ,
                RingSpawnBounds.constrainInitialSpawnZ(maximumSafeZ, geometry));
        assertEquals(0, RingSpawnBounds.constrainInitialSpawnZ(minimumSafeZ - 1, geometry));
        assertEquals(0, RingSpawnBounds.constrainInitialSpawnZ(maximumSafeZ + 1, geometry));
        assertEquals(0, RingSpawnBounds.constrainInitialSpawnZ(0, geometry));

        // The final vanilla spawn spiral can select either seam alias after
        // its initial climate candidate. Persist one canonical BlockPos only.
        for (int rawX : new int[]{-1, circumference, circumference + 19, -circumference + 23}) {
            BlockPos raw = new BlockPos(rawX, 96, minimumSafeZ);
            BlockPos canonical = RingSpawnBounds.canonicalInitialSpawn(raw, geometry);
            assertEquals(geometry.wrapBlockX(rawX), canonical.getX());
            assertEquals(raw.getY(), canonical.getY());
            assertEquals(raw.getZ(), canonical.getZ());
            assertEquals(geometry.toPhysical(rawX, raw.getY(), raw.getZ()),
                    geometry.toPhysical(canonical.getX(), canonical.getY(), canonical.getZ()));
        }
    }

    @ParameterizedTest(name = "{0}: atlas dimensions follow immutable C×W")
    @MethodSource("dev.ringworld.world.RingDimensionFixtures#playableLayouts")
    void atlasDimensionsFollowTheSelectedLayout(
            String name, int circumference, int width, int wallHeight) {
        RingTerrainAtlas atlas = new RingTerrainAtlas(new RingGeometry(width, circumference),
                0x5EEDL);

        assertEquals(circumference / RingTerrainAtlas.SAMPLE_STEP_BLOCKS, atlas.columns());
        assertEquals(width / RingTerrainAtlas.SAMPLE_STEP_BLOCKS, atlas.rows());
        assertEquals((long)atlas.columns() * atlas.rows(), atlas.cellCount());
    }
}
