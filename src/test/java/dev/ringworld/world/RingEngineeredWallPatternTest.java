package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingEngineeredWallPatternTest {
    private RingWallStyle style(int decay) {
        return RingWallStyle.custom(7, RingWallStyle.Palette.INDUSTRIAL,
                RingWallStyle.Pattern.ENGINEERED, decay);
    }
    @Test void baysAndWearRepeatOnlyAtTheCanonicalRingBoundary() {
        for (int circumference : new int[] {2048, 2064, 16384}) {
            for (int x = -24; x < 100; x++) {
                assertEquals(RingEngineeredWallPattern.bay(x, circumference),
                        RingEngineeredWallPattern.bay(x + circumference, circumference));
                for (int d = 0; d < 7; d++) {
                    assertEquals(RingWallPattern.materialRoll(style(70), x, 75, d, circumference, 42),
                            RingWallPattern.materialRoll(style(70), x - circumference, 75, d, circumference, 42));
                    assertEquals(RingWallPattern.topCollapseDepth(style(70), x, d, circumference, 42),
                            RingWallPattern.topCollapseDepth(style(70), x + circumference, d, circumference, 42));
                }
            }
        }
    }
    @Test void unweatheredMaterialsKeepTheOriginalIndustrialDistribution() {
        var old = RingWallStyle.custom(7, RingWallStyle.Palette.INDUSTRIAL, RingWallStyle.Pattern.PANELS, 0);
        for (int x = 0; x < 512; x++) for (int y = -64; y < 128; y += 3) {
            int before = RingWallPattern.materialRoll(old, x, y, 2, 2048, 42);
            int after = RingWallPattern.materialRoll(style(0), x, y, 2, 2048, 42);
            assertEquals(materialBucket(before), materialBucket(after));
        }
    }
    private int materialBucket(int roll) {
        return roll < 40 ? 0 : roll < 68 ? 1 : roll < 86 ? 2 : roll < 97 ? 3 : 4;
    }
    @Test void decayIsMonotonicAndDoesNotPerforateLowerWall() {
        boolean broken = false;
        for (int x = 0; x < 512; x++) for (int d = 0; d < 7; d++) {
            int previous = 0;
            for (int decay : new int[] {0, 10, 40, 70, 100}) {
                int removed = RingWallPattern.topCollapseDepth(style(decay), x, d, 2048, 1234);
                if (decay == 0) assertEquals(0, removed);
                assertTrue(removed >= previous);
                assertTrue(removed <= 34);
                assertTrue(RingWallPattern.blockPresent(style(decay), x, 60, d, 96, 2048, 1234));
                previous = removed;
                broken |= removed > 0;
            }
        }
        assertTrue(broken);
    }
    @Test void approvedDecayDoesNotAlterOriginalMaterialRolls() {
        var original = RingWallStyle.custom(7, RingWallStyle.Palette.INDUSTRIAL,
                RingWallStyle.Pattern.PANELS, 0);
        for (int decay : new int[] {0, 10, 40, 70, 100}) {
            for (int x = 0; x < 256; x++) for (int y = 0; y < 64; y++) {
                assertEquals(RingWallPattern.materialRoll(original, x, y, 0, 2048, 71),
                        RingWallPattern.materialRoll(style(decay), x, y, 0, 2048, 71));
            }
        }
    }
    @Test void existingIndustrialPanelStyleKeepsItsSavedIdentity() {
        var old = new RingWallStyle(7, RingWallStyle.Palette.INDUSTRIAL, RingWallStyle.Pattern.PANELS, 10, 1);
        assertEquals(3, old.pattern().id());
        assertNotEquals(old, RingWallStyle.Preset.INDUSTRIAL_SUPERSTRUCTURE.style());
        assertEquals(6, style(10).pattern().id());
    }
}
