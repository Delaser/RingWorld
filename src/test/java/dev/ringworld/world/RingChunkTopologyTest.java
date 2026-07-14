package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingChunkTopologyTest {
    private final RingChunkTopology topology = new RingChunkTopology(100);

    @Test
    void canonicalizesEveryPeriodicImage() {
        assertEquals(0, topology.canonicalX(100));
        assertEquals(99, topology.canonicalX(-1));
        assertEquals(2, topology.canonicalX(302));
    }

    @Test
    void chunkDistanceCrossesTheJoinedEdge() {
        assertEquals(1, topology.distanceX(99, 0));
        assertEquals(2, topology.distanceX(99, 1));
        assertEquals(2, topology.distanceX(-1, 101));
    }

    @Test
    void watchWindowUsesPeriodicDistance() {
        assertTrue(topology.isWithinVanillaDistance(99, 0, 6, 0, 0, true));
        assertTrue(topology.isWithinVanillaDistance(0, 0, 6, 100, 0, true));
        assertFalse(topology.isWithinVanillaDistance(99, 0, 6, 50, 0, true));
    }

    @Test
    void seamStepDiffsTheWatchWindowIncrementally() {
        assertFalse(RingChunkFilter.requiresFullRekey(
                99, 0, 6, 6, 100, 100, -10, 9, -10, 9));
        assertFalse(RingChunkFilter.requiresFullRekey(
                0, 99, 6, 6, 100, 100, -10, 9, -10, 9));
    }

    @Test
    void longTeleportRekeysTheWholeWatchWindow() {
        assertTrue(RingChunkFilter.requiresFullRekey(
                0, 40, 6, 6, 100, 100, -10, 9, -10, 9));
        assertTrue(RingChunkFilter.requiresFullRekey(
                40, 0, 6, 6, 100, 100, -10, 9, -10, 9));
    }

    @Test
    void wholeRingViewLoadsEveryTerrainChunkButNoExteriorVoid() {
        int included = 0;
        for (int x = 0; x < 100; x++) {
            for (int z = -10; z <= 9; z++) {
                if (RingChunkFilter.isWithinRingDistance(
                        100, 0, 0, 100, -10, 9, x, z, false)) included++;
            }
        }

        assertEquals(2_000, included);
        assertTrue(RingChunkFilter.isWithinRingDistance(
                100, 0, 0, 100, -10, 9, 50, -10, false));
        assertTrue(RingChunkFilter.isWithinRingDistance(
                100, 0, 0, 100, -10, 9, 50, 9, false));
        assertFalse(RingChunkFilter.isWithinRingDistance(
                100, 0, 0, 100, -10, 9, 0, -11, false));
        assertFalse(RingChunkFilter.isWithinRingDistance(
                100, 0, 0, 100, -10, 9, 0, 10, false));
    }
}
