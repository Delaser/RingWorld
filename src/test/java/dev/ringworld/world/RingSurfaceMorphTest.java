package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingSurfaceMorphTest {
    @Test
    void clampsBeforeAndAfterTheMorphWindow() {
        assertEquals(0.0F, RingSurfaceMorph.progress(-1L));
        assertEquals(0.0F, RingSurfaceMorph.progress(0L));
        assertEquals(1.0F, RingSurfaceMorph.progress(RingSurfaceMorph.DURATION_NANOS));
        assertEquals(1.0F, RingSurfaceMorph.progress(Long.MAX_VALUE));
    }

    @Test
    void usesAStableSymmetricEaseThroughTheTransition() {
        float quarter = RingSurfaceMorph.progress(RingSurfaceMorph.DURATION_NANOS / 4L);
        float half = RingSurfaceMorph.progress(RingSurfaceMorph.DURATION_NANOS / 2L);
        float threeQuarter = RingSurfaceMorph.progress(RingSurfaceMorph.DURATION_NANOS * 3L / 4L);
        assertTrue(quarter > 0.0F && quarter < half);
        assertEquals(0.5F, half, 0.000_001F);
        assertEquals(1.0F - quarter, threeQuarter, 0.000_001F);
    }
}
