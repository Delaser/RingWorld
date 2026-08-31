package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingStructureDensityTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(256, 16_384);

    @Test
    void candidateGridIsPeriodicAndFiniteWidth() {
        int count = 0;
        for (int x = 0; x < GEOMETRY.circumferenceChunks(); x++) {
            for (int z = GEOMETRY.minChunkZ(); z <= GEOMETRY.maxChunkZ(); z++) {
                boolean candidate = RingStructureDensity.isAdditionalCandidate(
                        42L, GEOMETRY, 10387312, 32, 8, x, z);
                assertEquals(candidate, RingStructureDensity.isAdditionalCandidate(
                        42L, GEOMETRY, 10387312, 32, 8,
                        x + GEOMETRY.circumferenceChunks(), z));
                if (candidate) count++;
            }
        }
        assertTrue(count > 0);
        assertFalse(RingStructureDensity.isAdditionalCandidate(
                42L, GEOMETRY, 10387312, 32, 8, 0, GEOMETRY.maxChunkZ() + 4));
    }

    @Test
    void differentSeedsMoveCandidates() {
        boolean differs = false;
        for (int x = 0; x < GEOMETRY.circumferenceChunks() && !differs; x++) {
            for (int z = GEOMETRY.minChunkZ(); z <= GEOMETRY.maxChunkZ(); z++) {
                differs |= RingStructureDensity.isAdditionalCandidate(1, GEOMETRY, 2, 24, 6, x, z)
                        != RingStructureDensity.isAdditionalCandidate(3, GEOMETRY, 2, 24, 6, x, z);
            }
        }
        assertTrue(differs);
    }
}
