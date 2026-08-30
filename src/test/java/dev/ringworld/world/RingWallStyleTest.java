package dev.ringworld.world;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingWallStyleTest {
    private static final int CIRCUMFERENCE = 2_048;

    @Test
    void everyPresetRoundTripsThroughTheSavedCodec() {
        for (RingWallStyle.Preset preset : RingWallStyle.Preset.values()) {
            var encoded = RingWallStyle.CODEC.encodeStart(JsonOps.INSTANCE, preset.style())
                    .getOrThrow();
            RingWallStyle decoded = RingWallStyle.CODEC.parse(JsonOps.INSTANCE, encoded)
                    .getOrThrow();
            assertEquals(preset.style(), decoded);
        }
    }

    @Test
    void rejectsUnsafeThicknessDecayAndUnknownIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> RingWallStyle.custom(
                0, RingWallStyle.Palette.WEATHERED, RingWallStyle.Pattern.CLUSTERED, 0));
        assertThrows(IllegalArgumentException.class, () -> RingWallStyle.custom(
                33, RingWallStyle.Palette.WEATHERED, RingWallStyle.Pattern.CLUSTERED, 0));
        assertThrows(IllegalArgumentException.class, () -> RingWallStyle.custom(
                5, RingWallStyle.Palette.WEATHERED, RingWallStyle.Pattern.CLUSTERED, 101));
        assertThrows(IllegalArgumentException.class, () -> RingWallStyle.Palette.fromId(99));
        assertThrows(IllegalArgumentException.class, () -> RingWallStyle.Pattern.fromId(99));
    }

    @Test
    void materialSamplingIsCanonicalAndGenerationOrderIndependent() {
        RingWallStyle style = RingWallStyle.Preset.ANCIENT_MASONRY.style();
        int expected = RingWallPattern.materialRoll(style, 13, 72, 2, CIRCUMFERENCE, 91L);
        assertEquals(expected, RingWallPattern.materialRoll(
                style, 13 + CIRCUMFERENCE, 72, 2, CIRCUMFERENCE, 91L));
        assertEquals(expected, RingWallPattern.materialRoll(
                style, 13 - CIRCUMFERENCE, 72, 2, CIRCUMFERENCE, 91L));
    }

    @Test
    void decayOnlyRemovesTopConnectedColumns() {
        RingWallStyle style = RingWallStyle.Preset.OVERGROWN_RUIN.style();
        int top = 96;
        boolean foundCollapse = false;
        for (int x = 0; x < CIRCUMFERENCE; x++) {
            int removed = RingWallPattern.topCollapseDepth(style, x, CIRCUMFERENCE, 1234L);
            assertEquals(removed, RingWallPattern.topCollapseDepth(
                    style, x + CIRCUMFERENCE, CIRCUMFERENCE, 1234L));
            if (removed == 0) continue;
            foundCollapse = true;
            assertFalse(RingWallPattern.blockPresent(
                    style, x, top - 1, top, CIRCUMFERENCE, 1234L));
            assertTrue(RingWallPattern.blockPresent(
                    style, x, top - removed - 1, top, CIRCUMFERENCE, 1234L));
            for (int y = top - removed; y < top; y++) {
                assertFalse(RingWallPattern.blockPresent(
                        style, x, y, top, CIRCUMFERENCE, 1234L));
            }
        }
        assertTrue(foundCollapse);
    }

    @Test
    void zeroDecayPreservesTheCompleteLegacyTopEdge() {
        for (int x = 0; x < CIRCUMFERENCE; x++) {
            assertEquals(0, RingWallPattern.topCollapseDepth(
                    RingWallStyle.LEGACY, x, CIRCUMFERENCE, 55L));
        }
    }

    @Test
    void thickRimsOwnEveryInwardBlockAcrossChunkBoundaries() {
        RingGeometry geometry = new RingGeometry(128, CIRCUMFERENCE);
        RingWallStyle style = RingWallStyle.custom(32, RingWallStyle.Palette.MONOLITH,
                RingWallStyle.Pattern.PANELS, 0);
        assertEquals(0, RingGenerationBoundary.rimDepthAtZ(geometry, style, -64));
        assertEquals(15, RingGenerationBoundary.rimDepthAtZ(geometry, style, -49));
        assertEquals(16, RingGenerationBoundary.rimDepthAtZ(geometry, style, -48));
        assertEquals(31, RingGenerationBoundary.rimDepthAtZ(geometry, style, -33));
        assertEquals(-1, RingGenerationBoundary.rimDepthAtZ(geometry, style, -32));
        assertEquals(31, RingGenerationBoundary.rimDepthAtZ(geometry, style, 32));
        assertEquals(16, RingGenerationBoundary.rimDepthAtZ(geometry, style, 47));
        assertEquals(15, RingGenerationBoundary.rimDepthAtZ(geometry, style, 48));
        assertEquals(0, RingGenerationBoundary.rimDepthAtZ(geometry, style, 63));
    }
}
