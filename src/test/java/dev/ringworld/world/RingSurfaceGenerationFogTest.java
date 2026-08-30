package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingSurfaceGenerationFogTest {
    @Test
    void startsHeavyAndClearsCompletelyWithAtlasProgress() {
        assertEquals(RingSurfaceGenerationFog.MAX_FOG,
                RingSurfaceGenerationFog.amount(0.0));
        assertEquals(RingSurfaceGenerationFog.MAX_FOG * 0.5F,
                RingSurfaceGenerationFog.amount(0.5), 0.000_001F);
        assertEquals(0.0F, RingSurfaceGenerationFog.amount(1.0));
        assertTrue(RingSurfaceGenerationFog.amount(0.25)
                > RingSurfaceGenerationFog.amount(0.75));
    }

    @Test
    void clampsMalformedCompletionAtTheSafeEndpoints() {
        assertEquals(RingSurfaceGenerationFog.MAX_FOG,
                RingSurfaceGenerationFog.amount(-10.0));
        assertEquals(0.0F, RingSurfaceGenerationFog.amount(10.0));
        assertEquals(RingSurfaceGenerationFog.MAX_FOG,
                RingSurfaceGenerationFog.amount(Double.NaN));
    }

    @Test
    void seedPreviewKeepsSomeHazeWithoutHidingTheMap() {
        assertEquals(RingSurfaceGenerationFog.MAX_SEED_PREVIEW_FOG,
                RingSurfaceGenerationFog.amount(0.0, true));
        assertEquals(RingSurfaceGenerationFog.MAX_SEED_PREVIEW_FOG * 0.5F,
                RingSurfaceGenerationFog.amount(0.5, true), 0.000_001F);
        assertEquals(0.0F, RingSurfaceGenerationFog.amount(1.0, true));
    }
}
