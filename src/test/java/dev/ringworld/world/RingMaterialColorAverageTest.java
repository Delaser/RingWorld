package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RingMaterialColorAverageTest {
    @Test void transparentPixelsDoNotDarkenOrTintVisibleMaterial() {
        var color = new RingMaterialColorAverage();
        color.add(0xFF406080);
        color.add(0x00FFFFFF);
        color.add(0x00000000);
        assertEquals(0x406080, color.rgbOr(-1));
    }
    @Test void partiallyTransparentPixelsContributeByCoverage() {
        var color = new RingMaterialColorAverage();
        color.add(0xFF000000);
        color.add(0x55C0C0C0);
        assertEquals(0x303030, color.rgbOr(-1));
    }
    @Test void missingOpaquePixelsUseFallback() {
        var color = new RingMaterialColorAverage();
        color.add(0x00AABBCC);
        assertEquals(0x123456, color.rgbOr(0x123456));
    }
}
