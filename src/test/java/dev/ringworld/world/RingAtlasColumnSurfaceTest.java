package dev.ringworld.world;

import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import static dev.ringworld.world.RingAtlasColumnSurface.Material.*;
import static org.junit.jupiter.api.Assertions.*;

class RingAtlasColumnSurfaceTest {
    @Test void omitsThinBuildsAndFindsTerrainUnderStackedPlatforms() {
        assertEquals(64, select(y -> y <= 64 ? TERRAIN : y >= 200 ? BUILT : AIR));
        assertEquals(64, select(y -> y <= 64 ? TERRAIN
                : y >= 200 || y >= 100 && y <= 110 ? BUILT : AIR));
    }
    @Test void keepsNaturalRoofsVegetationUnknownMaterialsAndSupportedBuilds() {
        assertEquals(220, select(y -> y <= 64 || y >= 200 ? TERRAIN : AIR));
        assertEquals(220, select(y -> y <= 64 || y == 200 ? TERRAIN : y > 200 ? BUILT : AIR));
        assertEquals(220, select(y -> y <= 64 ? TERRAIN : BUILT));
        assertEquals(220, select(y -> y <= 200 ? TERRAIN : y >= 208 ? BUILT : AIR));
        assertEquals(220, select(y -> y <= 64 ? TERRAIN : y >= 156 ? BUILT : AIR));
    }
    @Test void acceptsExactEightBlockGapAndSixtyFourBlockThickness() {
        assertEquals(64, select(y -> y <= 64 ? TERRAIN : y >= 73 && y <= 136 ? BUILT : AIR, 136));
        assertEquals(137, select(y -> y <= 64 ? TERRAIN : y >= 73 ? BUILT : AIR, 137));
    }
    @Test void hasBoundedReadsAndRetainsBuildsWithoutGround() {
        int[] reads = {0};
        assertEquals(319, RingAtlasColumnSurface.select(319, -64, y -> {
            assertTrue(y >= -64 && y <= 319); reads[0]++;
            return y == 319 ? BUILT : AIR;
        }));
        assertTrue(reads[0] <= 386);
        assertEquals(-65, RingAtlasColumnSurface.select(-65, -64, y -> {
            fail("empty columns must not read outside the world"); return AIR;
        }));
        reads[0] = 0;
        assertEquals(220, RingAtlasColumnSurface.select(220, -64, y -> { reads[0]++; return TERRAIN; }));
        assertEquals(1, reads[0]);
    }
    private static int select(IntFunction<RingAtlasColumnSurface.Material> column) { return select(column, 220); }
    private static int select(IntFunction<RingAtlasColumnSurface.Material> column, int top) {
        return RingAtlasColumnSurface.select(top, -64, column);
    }
}
