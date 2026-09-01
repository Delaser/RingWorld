package dev.ringworld.server;

import dev.ringworld.world.RingSurfaceLod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingAtlasSurfaceLightTest {
    @Test
    void exposedSampleKeepsStrongestEmissionOrSurfaceLight() {
        assertEquals(13, RingAtlasPregenerationService.exposedBlockLight(13, 4, 9));
        assertEquals(12, RingAtlasPregenerationService.exposedBlockLight(3, 12, 8));
        assertEquals(15, RingAtlasPregenerationService.exposedBlockLight(0, 7, 15));
    }

    @Test
    void myceliumUsesItsTopTextureColour() {
        assertEquals(0x6F6365, RingSurfaceLod.VANILLA_MYCELIUM_TOP_RGB);
    }
}
