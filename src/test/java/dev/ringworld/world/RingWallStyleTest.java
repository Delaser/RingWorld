package dev.ringworld.world;

import com.mojang.serialization.JsonOps;
import java.util.List;
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
            assertEquals(preset.style(), RingWallStyle.CODEC.parse(JsonOps.INSTANCE, encoded)
                    .getOrThrow());
        }
    }

    @Test
    void stableIdsAndRetiredPatternsRemainDecodable() {
        for (RingWallStyle.Palette palette : RingWallStyle.Palette.values()) {
            assertEquals(palette, RingWallStyle.Palette.fromId(palette.id()));
        }
        for (RingWallStyle.Pattern pattern : RingWallStyle.Pattern.values()) {
            assertEquals(pattern, RingWallStyle.Pattern.fromId(pattern.id()));
        }
        assertEquals(List.of(
                        RingWallStyle.Pattern.MASONRY,
                        RingWallStyle.Pattern.PANELS,
                        RingWallStyle.Pattern.GRADIENT,
                        RingWallStyle.Pattern.HYBRID),
                List.of(RingWallStyle.Pattern.selectableValues()));
        assertEquals(RingWallStyle.Pattern.MASONRY, RingWallStyle.Pattern.CLUSTERED.next());
        assertEquals(RingWallStyle.Pattern.MASONRY, RingWallStyle.Pattern.STRATA.next());
    }

    @Test
    void validatesStyleBoundsAndStableIdentifiers() {
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
    void materialAndDecaySamplingUseCanonicalLongitude() {
        RingWallStyle style = RingWallStyle.Preset.OVERGROWN_RUIN.style();
        for (int x : new int[] {0, 13, 1_024, 2_047}) {
            int material = RingWallPattern.materialRoll(style, x, 72, 2, CIRCUMFERENCE, 91L);
            int collapse = RingWallPattern.topCollapseDepth(
                    style, x, 3, CIRCUMFERENCE, 91L);
            assertEquals(material, RingWallPattern.materialRoll(
                    style, x + CIRCUMFERENCE, 72, 2, CIRCUMFERENCE, 91L));
            assertEquals(collapse, RingWallPattern.topCollapseDepth(
                    style, x - CIRCUMFERENCE, 3, CIRCUMFERENCE, 91L));
        }
    }

    @Test
    void decayRemainsTopConnected() {
        RingWallStyle style = RingWallStyle.Preset.OVERGROWN_RUIN.style();
        int wallTop = 96;
        boolean foundCollapse = false;
        for (int x = 0; x < CIRCUMFERENCE; x++) {
            int removed = RingWallPattern.topCollapseDepth(style, x, CIRCUMFERENCE, 1234L);
            if (removed == 0) continue;
            foundCollapse = true;
            assertTrue(RingWallPattern.blockPresent(
                    style, x, wallTop - removed - 1, wallTop, CIRCUMFERENCE, 1234L));
            for (int y = wallTop - removed; y < wallTop; y++) {
                assertFalse(RingWallPattern.blockPresent(
                        style, x, y, wallTop, CIRCUMFERENCE, 1234L));
            }
        }
        assertTrue(foundCollapse);
    }

    @Test
    void decayNoiseClosesSmoothlyAcrossANonDivisibleCellSeam() {
        int circumference = 2_064;
        long seed = 0x51A7_0F1EL;
        for (int xScale : new int[] {32, 16, 8, 4}) {
            double atSeam = RingWallPattern.smoothNoise2d(
                    seed, 0, 3, xScale, 8, circumference);
            assertEquals(atSeam, RingWallPattern.smoothNoise2d(
                    seed, circumference, 3, xScale, 8, circumference));
            assertEquals(atSeam, RingWallPattern.smoothNoise2d(
                    seed, -circumference, 3, xScale, 8, circumference));

            double beforeSeam = RingWallPattern.smoothNoise2d(
                    seed, circumference - 1, 3, xScale, 8, circumference);
            assertEquals(beforeSeam, RingWallPattern.smoothNoise2d(
                    seed, -1, 3, xScale, 8, circumference));
            if (xScale == 32) {
                assertTrue(Math.abs(atSeam - beforeSeam) < 0.01,
                        "the partial 32-block interval must close smoothly at the seam");
            }
        }
    }
}
