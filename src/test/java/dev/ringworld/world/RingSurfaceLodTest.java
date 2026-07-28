package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingSurfaceLodTest {
    @Test
    void textureLuminanceDarkensBiomeTintWithoutChangingHue() {
        assertEquals(0x406020,
                RingSurfaceLod.applyTextureLuminance(0x80C040, 0.5));
    }

    @Test
    void rejectsInvalidTextureLuminance() {
        assertThrows(IllegalArgumentException.class,
                () -> RingSurfaceLod.applyTextureLuminance(0x80C040, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> RingSurfaceLod.applyTextureLuminance(0x80C040, -0.1));
    }

    @Test
    void dedicatedServerZeroTintFallsBackToNonBlackMapColour() {
        assertEquals(0x40591C,
                RingSurfaceLod.applyTextureLuminanceWithMapFallback(
                        0x000000, 0x7FB238, 0.5));
    }

    @Test
    void loadedBiomeTintWinsOverMapFallback() {
        assertEquals(0x406020,
                RingSurfaceLod.applyTextureLuminanceWithMapFallback(
                        0x80C040, 0x7FB238, 0.5));
    }

    @Test
    void flatSurfacePreservesItsMapColour() {
        assertEquals(0x4080C0, RingSurfaceLod.shadeSurfaceColor(
                0x4080C0, 64.0, 64.0, 64.0, 64.0, 64.0, 1.0, 1.0));
    }

    @Test
    void steepReliefDarkensWithoutChangingHueOrdering() {
        int shaded = RingSurfaceLod.shadeSurfaceColor(
                0x80C040, 64.0, 32.0, 96.0, 32.0, 96.0, 8.0, 8.0);
        assertTrue((shaded >> 16 & 0xFF) < 0x80);
        assertTrue((shaded >> 8 & 0xFF) < 0xC0);
        assertTrue((shaded & 0xFF) < 0x40);
        assertTrue((shaded >> 8 & 0xFF) > (shaded >> 16 & 0xFF));
    }

    @Test
    void mipFilterWrapsCircumferenceAndClampsWidth() {
        int[] source = {
                0xFF000000, 0xFF200000, 0xFF400000, 0xFF600000,
                0xFF000020, 0xFF200020, 0xFF400020, 0xFF600020
        };
        int[] mip = RingSurfaceLod.buildNextMipArgb(source, 4, 2);

        assertEquals(2, mip.length);
        assertEquals(0xFF100010, mip[0]);
        assertEquals(0xFF500010, mip[1]);
    }

    @Test
    void onePixelMipRemainsStable() {
        assertEquals(0xFFA0B0C0,
                RingSurfaceLod.buildNextMipArgb(new int[]{0xFFA0B0C0}, 1, 1)[0]);
    }

    @Test
    void rejectsMalformedMipInput() {
        assertThrows(IllegalArgumentException.class,
                () -> RingSurfaceLod.buildNextMipArgb(new int[3], 2, 2));
    }
}
