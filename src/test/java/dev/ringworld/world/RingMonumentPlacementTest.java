package dev.ringworld.world;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingMonumentPlacementTest {
    @Test
    void candidateOrderIsDeterministicCanonicalAndFinite() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        RingMonumentPlacement.SearchResult first = RingMonumentPlacement.findCandidate(
                1234L, geometry, candidate -> true);
        RingMonumentPlacement.SearchResult again = RingMonumentPlacement.findCandidate(
                1234L, geometry, candidate -> true);

        assertEquals(first, again);
        assertTrue(RingMonumentPlacement.isConservativelyInBounds(first.candidate(), geometry));
        assertTrue(first.candidate().chunkX() >= 0);
        assertTrue(first.candidate().chunkX() < geometry.circumferenceChunks());
        assertTrue(first.candidate().chunkZ() >= geometry.minChunkZ());
        assertTrue(first.candidate().chunkZ() <= geometry.maxChunkZ());
    }

    @Test
    void boundedSearchDoesNotVisitAliasesOrDuplicateCandidates() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        Set<RingMonumentPlacement.Candidate> seen = new HashSet<>();
        RingMonumentPlacement.SearchResult result = RingMonumentPlacement.findCandidate(
                99L, geometry, candidate -> !seen.add(candidate));

        assertNull(result.candidate());
        assertEquals(result.checkedCandidates(), seen.size());
        assertEquals(RingMonumentPlacement.MAX_CANDIDATES, seen.size());
        assertTrue(result.searchBoundReached());
        assertTrue(seen.stream().allMatch(candidate ->
                RingMonumentPlacement.isConservativelyInBounds(candidate, geometry)));
    }

    @Test
    void supportsTheMinimumGeometryWithAConservativeMonumentEnvelope() {
        RingGeometry geometry = new RingGeometry(256, 1_024);
        RingMonumentPlacement.SearchResult result = RingMonumentPlacement.findCandidate(
                1L, geometry, candidate -> true);

        assertTrue(result.candidate() != null);
    }

    @Test
    void smallPresetHasNoMonumentCandidateSpaceButAOneChunkWiderBandDoes() {
        assertTrue(!RingMonumentPlacement.hasCandidateSpace(new RingGeometry(128, 2_048)));
        assertTrue(RingMonumentPlacement.hasCandidateSpace(new RingGeometry(160, 2_048)));
    }

    @Test
    void seedChangesCandidateOrder() {
        RingGeometry geometry = new RingGeometry(416, 2_048);
        Set<RingMonumentPlacement.Candidate> starts = new HashSet<>();
        for (long seed = 0; seed < 16; seed++) {
            starts.add(RingMonumentPlacement.findCandidate(seed, geometry, candidate -> true).candidate());
        }
        assertNotEquals(1, starts.size());
    }
}
