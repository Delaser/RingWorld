package dev.ringworld.world;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingStrongholdPlacementTest {
    @Test
    void placementIsDeterministicCanonicalAndCentredAcrossWidth() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        RingStrongholdPlacement.StartChunk first =
                RingStrongholdPlacement.guaranteedStart(123456789L, geometry);

        assertEquals(first, RingStrongholdPlacement.guaranteedStart(123456789L, geometry));
        assertEquals(0, first.chunkZ());
        assertTrue(first.chunkX() >= 0 && first.chunkX() < geometry.circumferenceChunks());
        assertTrue(RingStrongholdPlacement.hasSafeSeamClearance(first, geometry));
    }

    @Test
    void placementSupportsMinimumAndNonPowerOfTwoCircumferences() {
        for (RingGeometry geometry : new RingGeometry[]{
                new RingGeometry(256, 1_024),
                new RingGeometry(416, 2_048),
                new RingGeometry(4_096, 15_552)}) {
            RingStrongholdPlacement.StartChunk start =
                    RingStrongholdPlacement.guaranteedStart(-42L, geometry);
            assertTrue(RingStrongholdPlacement.hasSafeSeamClearance(start, geometry));
            assertEquals(0, start.chunkZ());
        }
    }

    @Test
    void worldSeedActuallySelectsTheCircumferencePosition() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        Set<Integer> starts = new HashSet<>();
        for (long seed = 0; seed < 32; seed++) {
            starts.add(RingStrongholdPlacement.guaranteedStart(seed, geometry).chunkX());
        }

        assertNotEquals(1, starts.size());
    }

    @Test
    void completedPieceGraphIsShiftedInsideBothFiniteAxes() {
        RingGeometry geometry = new RingGeometry(256, 16_384);

        assertEquals(new RingStrongholdPlacement.BlockShift(0, 4),
                RingStrongholdPlacement.fitShift(6760, 6917, -132, 18, geometry));
        assertEquals(new RingStrongholdPlacement.BlockShift(-5, 0),
                RingStrongholdPlacement.fitShift(16_230, 16_388, -80, 80, geometry));
        assertEquals(new RingStrongholdPlacement.BlockShift(0, 0),
                RingStrongholdPlacement.fitShift(100, 250, -90, 90, geometry));
    }

    @Test
    void pieceGraphWiderThanBandFailsExplicitly() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        assertThrows(IllegalArgumentException.class,
                () -> RingStrongholdPlacement.fitShift(100, 250, -130, 130, geometry));
    }

    @Test
    void narrowBandKeepsPortalBoundsWhileAllowingOptionalBranchesToCrossRims() {
        RingGeometry geometry = new RingGeometry(128, 2_048);

        RingStrongholdPlacement.FitPlan plan = RingStrongholdPlacement.fitRequiredPiece(
                400, 560, -130, 42,
                470, 510, -78, -38,
                geometry);

        assertEquals(new RingStrongholdPlacement.BlockShift(0, 14), plan.shift());
        assertTrue(plan.graphExceedsBoundsZ());
        assertTrue(!plan.graphExceedsBoundsX());
    }
}
