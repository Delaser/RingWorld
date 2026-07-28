package dev.ringworld.world;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingDimensionMatrixTest {
    private static Stream<Arguments> playableLayouts() {
        return Stream.of(
                Arguments.of("safe small", 2_048, 416),
                Arguments.of("narrow safe small", 2_048, 256),
                Arguments.of("production default", 15_552, 4_096),
                Arguments.of("long narrow", 32_768, 512),
                Arguments.of("wide medium", 4_096, 2_048));
    }

    @ParameterizedTest(name = "{0}: C={1}, W={2}")
    @MethodSource("playableLayouts")
    void geometryTopologyAndResourcesScaleTogether(String name,
                                                   int circumference,
                                                   int width) {
        RingGeometry geometry = new RingGeometry(width, circumference);
        RingDimensionReport report = RingDimensionReport.forVanillaOverworld(
                geometry, RingWorldSettings.DEFAULT_WALL_HEIGHT);

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
    }

    private static Stream<Arguments> renderLayoutsAndDistances() {
        return Stream.of(
                Arguments.of(2_048, 416, 6),
                Arguments.of(2_048, 416, 12),
                Arguments.of(2_048, 416, 28),
                Arguments.of(15_552, 4_096, 28),
                Arguments.of(32_768, 512, 64));
    }

    @ParameterizedTest(name = "C={0}, W={1}, view={2} chunks")
    @MethodSource("renderLayoutsAndDistances")
    void renderProfileRemainsOrderedAndBounded(int circumference,
                                               int width,
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
}
