package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingMacroTerrainTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(256, 16_384);

    @Test
    void riverIsContinuousAtTheCanonicalSeam() {
        RingWorldGenerationSettings settings = RingWorldGenerationSettings.DEFAULT
                .withContinuousRiver(true);
        RingMacroTerrain field = new RingMacroTerrain(GEOMETRY, 991L, settings);
        assertEquals(field.riverCenterZ(0.0), field.riverCenterZ(16_384.0), 1.0e-9);
        assertEquals(field.riverWidth(-0.25), field.riverWidth(16_383.75), 1.0e-9);
        assertTrue(field.riverInfluence(7_000, field.riverCenterZ(7_000)) > 0.99);
    }

    @Test
    void archipelagoIsPeriodicAndContainsWaterAndLand() {
        RingWorldGenerationSettings settings = RingWorldGenerationSettings.DEFAULT
                .withLayout(RingWorldLayout.ARCHIPELAGO);
        RingMacroTerrain field = new RingMacroTerrain(GEOMETRY, 12345L, settings);
        assertEquals(field.landBias(31.25, 17.0), field.landBias(16_415.25, 17.0), 1.0e-9);
        boolean land = false;
        boolean water = false;
        for (int x = 0; x < GEOMETRY.circumferenceBlocks(); x += 128) {
            for (int z = GEOMETRY.minWidthZ(); z <= GEOMETRY.maxWidthZ(); z += 32) {
                land |= field.landBias(x, z) > 0.1;
                water |= field.landBias(x, z) < -0.5;
            }
        }
        assertTrue(land, "layout must retain islands");
        assertTrue(water, "layout must retain ocean channels");
    }

    @Test
    void vanillaDefaultHasNoMacroBias() {
        RingMacroTerrain field = new RingMacroTerrain(GEOMETRY, 1L,
                RingWorldGenerationSettings.DEFAULT);
        assertFalse(field.active());
        assertEquals(0.0, field.landBias(100, 0));
        assertEquals(0.0, field.riverInfluence(100, 0));
    }
}
