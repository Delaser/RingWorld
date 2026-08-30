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
    void retiredPatternsRemainDecodableButAreNotSelectable() {
        assertEquals(java.util.List.of(
                        RingWallStyle.Pattern.MASONRY,
                        RingWallStyle.Pattern.PANELS,
                        RingWallStyle.Pattern.GRADIENT,
                        RingWallStyle.Pattern.HYBRID),
                java.util.List.of(RingWallStyle.Pattern.selectableValues()));
        assertEquals(RingWallStyle.Pattern.CLUSTERED,
                RingWallStyle.Pattern.fromId(RingWallStyle.Pattern.CLUSTERED.id()));
        assertEquals(RingWallStyle.Pattern.STRATA,
                RingWallStyle.Pattern.fromId(RingWallStyle.Pattern.STRATA.id()));
        assertEquals(RingWallStyle.Pattern.MASONRY, RingWallStyle.Pattern.CLUSTERED.next());
        assertEquals(RingWallStyle.Pattern.MASONRY, RingWallStyle.Pattern.STRATA.next());
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
    void industrialAccentSamplingIsCanonicalAndApproximatelyOnePerThousand() {
        RingWallStyle style = RingWallStyle.Preset.INDUSTRIAL_SUPERSTRUCTURE.style();
        int accents = 0;
        int samples = 200_000;
        for (int sample = 0; sample < samples; sample++) {
            int x = sample % CIRCUMFERENCE;
            int y = -64 + sample / CIRCUMFERENCE;
            int depth = sample % style.thicknessBlocks();
            boolean accent = RingWallPattern.rareAccent(
                    style, x, y, depth, CIRCUMFERENCE, 8128L, 1);
            assertEquals(accent, RingWallPattern.rareAccent(
                    style, x + CIRCUMFERENCE, y, depth, CIRCUMFERENCE, 8128L, 1));
            if (accent) accents++;
        }
        assertTrue(accents > 140 && accents < 260,
                "0.1% accent sampler produced " + accents + " hits");
    }

    @Test
    void rareAccentRejectsInvalidPrecisionInputs() {
        RingWallStyle style = RingWallStyle.Preset.INDUSTRIAL_SUPERSTRUCTURE.style();
        assertThrows(IllegalArgumentException.class, () -> RingWallPattern.rareAccent(
                style, 0, 64, 0, CIRCUMFERENCE, 1L, -1));
        assertThrows(IllegalArgumentException.class, () -> RingWallPattern.rareAccent(
                style, 0, 64, 0, CIRCUMFERENCE, 1L, 1_001));
    }

    @Test
    void materialPatternsDoNotRepeatOnTheOldFixedTilePeriods() {
        for (RingWallStyle.Pattern pattern : RingWallStyle.Pattern.values()) {
            RingWallStyle style = RingWallStyle.custom(5,
                    RingWallStyle.Palette.WEATHERED, pattern, 25);
            int verticalMatches = 0;
            int horizontalMatches = 0;
            for (int sample = 0; sample < 96; sample++) {
                int x = sample * 5 + 3;
                int y = -48 + sample;
                int roll = RingWallPattern.materialRoll(
                        style, x, y, 2, CIRCUMFERENCE, 19L);
                if (roll == RingWallPattern.materialRoll(
                        style, x, y + 128, 2, CIRCUMFERENCE, 19L)) {
                    verticalMatches++;
                }
                if (roll == RingWallPattern.materialRoll(
                        style, x + 16, y, 2, CIRCUMFERENCE, 19L)) {
                    horizontalMatches++;
                }
            }
            assertTrue(verticalMatches < 48, pattern + " repeated vertically");
            assertTrue(horizontalMatches < 48, pattern + " repeated horizontally");
        }
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
    void decayFormsCoherentCollapseRegionsRatherThanPerBlockNoise() {
        RingWallStyle style = RingWallStyle.Preset.OVERGROWN_RUIN.style();
        int changes = 0;
        int severeJumps = 0;
        int previous = RingWallPattern.topCollapseDepth(style, 0, CIRCUMFERENCE, 4321L);
        for (int x = 1; x < 512; x++) {
            int current = RingWallPattern.topCollapseDepth(style, x, CIRCUMFERENCE, 4321L);
            if (current != previous) changes++;
            if (Math.abs(current - previous) > 3) severeJumps++;
            previous = current;
        }
        assertTrue(changes > 35, "decay profile has become a smooth repeating wave");
        assertTrue(changes < 240, "decay profile is still independent per-block noise");
        assertTrue(severeJumps < 12, "adjacent rubble columns do not form a gradient");
    }

    @Test
    void decayVariesAcrossWallThicknessWithoutCreatingInternalHoles() {
        RingWallStyle style = RingWallStyle.Preset.OVERGROWN_RUIN.style();
        int top = 96;
        int longitudesWithDepthVariation = 0;
        for (int x = 0; x < 512; x++) {
            int first = RingWallPattern.topCollapseDepth(
                    style, x, 0, CIRCUMFERENCE, 9876L);
            boolean differs = false;
            for (int depth = 0; depth < style.thicknessBlocks(); depth++) {
                int removed = RingWallPattern.topCollapseDepth(
                        style, x, depth, CIRCUMFERENCE, 9876L);
                differs |= removed != first;
                assertEquals(removed, RingWallPattern.topCollapseDepth(
                        style, x + CIRCUMFERENCE, depth, CIRCUMFERENCE, 9876L));
                assertTrue(RingWallPattern.blockPresent(
                        style, x, top - removed - 1, depth, top, CIRCUMFERENCE, 9876L));
                for (int y = top - removed; y < top; y++) {
                    assertFalse(RingWallPattern.blockPresent(
                            style, x, y, depth, top, CIRCUMFERENCE, 9876L));
                }
            }
            if (differs) longitudesWithDepthVariation++;
        }
        assertTrue(longitudesWithDepthVariation > 100,
                "decay still cuts too many solid slots through the wall");
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
