package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingAtlasSurfaceInvalidationTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(256, 2_048);

    @Test
    void mapsPresentationXToOneCanonicalCellAndBoundsFiniteZ() {
        assertEquals(new RingAtlasSurfaceInvalidation.Cell(0, 0),
                RingAtlasSurfaceInvalidation.cellFor(
                        GEOMETRY, 8, 2_052, GEOMETRY.minWidthZ() + 4).orElseThrow());
        assertTrue(RingAtlasSurfaceInvalidation.cellFor(
                GEOMETRY, 8, 0, GEOMETRY.maxWidthZ() + 1).isEmpty());
    }

    @Test
    void onlyChangesAtOrAboveStoredTopCanAffectSurface() {
        assertFalse(RingAtlasSurfaceInvalidation.mayAffectSurface(62, 64));
        assertTrue(RingAtlasSurfaceInvalidation.mayAffectSurface(63, 64));
        assertTrue(RingAtlasSurfaceInvalidation.mayAffectSurface(90, 64));
    }
}
