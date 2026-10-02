package dev.ringworld.world;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingStructureLocateBoundsTest {
    @Test void wideRingKeepsFiniteWidthSearchBeyondHalfCircumference() {
        var bounds = RingStructureDensity.searchBounds(new RingGeometry(4096, 2048), 0, 0, 4, 32);
        assertEquals(-128, bounds.minZ());
        assertEquals(127, bounds.maxZ());
        assertEquals(128, bounds.countX());
        var visited = new HashSet<Integer>();
        for (int i = 0; i < bounds.countX(); i++) assertTrue(visited.add(bounds.canonicalX(i)));
    }
    @Test void nearbySearchWrapsWithoutDuplicatingCanonicalChunks() {
        var bounds = RingStructureDensity.searchBounds(new RingGeometry(256, 2048), 0, 0, 0, 2);
        assertEquals(5, bounds.countX());
        assertEquals(126, bounds.canonicalX(0));
        assertEquals(2, bounds.canonicalX(4));
        assertEquals(-2, bounds.minZ());
        assertEquals(2, bounds.maxZ());
    }
    @Test void extremeRadiusDoesNotOverflowBeforeClipping() {
        var bounds = RingStructureDensity.searchBounds(new RingGeometry(4096, 2048), Integer.MIN_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(128, bounds.countX());
        assertEquals(-128, bounds.minZ());
        assertEquals(127, bounds.maxZ());
    }
}
