package dev.ringworld.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RingAtlasHudProgressTest {
    @Test
    void reportsWholePercentProgressAndDisappearsAtCompletion() {
        assertEquals("Ring Atlas Generating: 0%", RingAtlasHudProgress.label(0, 65_536).orElseThrow());
        assertEquals("Ring Atlas Generating: 4%", RingAtlasHudProgress.label(3_044, 65_536).orElseThrow());
        assertEquals("Ring Atlas Generating: 99%", RingAtlasHudProgress.label(65_535, 65_536).orElseThrow());
        assertTrue(RingAtlasHudProgress.label(65_536, 65_536).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> RingAtlasHudProgress.label(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> RingAtlasHudProgress.label(11, 10));
        assertThrows(IllegalArgumentException.class, () -> RingAtlasHudProgress.label(0, 0));
    }
}
