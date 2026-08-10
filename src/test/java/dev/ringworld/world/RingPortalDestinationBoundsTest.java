package dev.ringworld.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class RingPortalDestinationBoundsTest {
    private static final RingGeometry MEDIUM = new RingGeometry(256, 16_384);
    private static final RingGeometry SMALL = new RingGeometry(128, 2_048);

    @Test
    void wrapsPositiveAndNegativeMultiLapTargetsAfterVanillaScaling() {
        assertEquals(new BlockPos(19, 70, 0),
                RingPortalDestinationBounds.normalizeSearchAnchor(
                        MEDIUM, new BlockPos(16_384 * 4 + 19, 70, 0)));
        assertEquals(new BlockPos(16_377, 70, 0),
                RingPortalDestinationBounds.normalizeSearchAnchor(
                        MEDIUM, new BlockPos(-16_384 * 3 - 7, 70, 0)));
    }

    @Test
    void clampsBothFiniteWidthDirectionsToCreationSafeAnchors() {
        assertEquals(-104, RingPortalDestinationBounds.safeAnchorMinZ(MEDIUM));
        assertEquals(103, RingPortalDestinationBounds.safeAnchorMaxZ(MEDIUM));
        assertEquals(-104, RingPortalDestinationBounds.normalizeSearchAnchor(
                MEDIUM, new BlockPos(0, 80, -50_000)).getZ());
        assertEquals(103, RingPortalDestinationBounds.normalizeSearchAnchor(
                MEDIUM, new BlockPos(0, 80, 50_000)).getZ());
    }

    @Test
    void preservesTargetsAlreadyInsideTheSafeCreationInterval() {
        BlockPos source = new BlockPos(8_192, -12, 37);
        assertEquals(source, RingPortalDestinationBounds.normalizeSearchAnchor(MEDIUM, source));
    }

    @Test
    void safeSmallStillContainsACompleteCreationEnvelope() {
        assertEquals(-40, RingPortalDestinationBounds.safeAnchorMinZ(SMALL));
        assertEquals(39, RingPortalDestinationBounds.safeAnchorMaxZ(SMALL));
        assertTrue(RingPortalDestinationBounds.isSafePortalBlock(SMALL, new BlockPos(0, 70, -56)));
        assertTrue(RingPortalDestinationBounds.isSafePortalBlock(SMALL, new BlockPos(0, 70, 55)));
        assertFalse(RingPortalDestinationBounds.isSafePortalBlock(SMALL, new BlockPos(0, 70, -57)));
        assertFalse(RingPortalDestinationBounds.isSafePortalBlock(SMALL, new BlockPos(0, 70, 56)));
    }

    @Test
    void periodicQueriesExposeBothCanonicalSidesOfTheSeam() {
        BlockPos anchor = new BlockPos(2, 70, 0);
        assertEquals(List.of(
                        new BlockPos(2, 70, 0),
                        new BlockPos(2 - 16_384, 70, 0),
                        new BlockPos(2 + 16_384, 70, 0)),
                RingPortalDestinationBounds.periodicQueryAnchors(MEDIUM, anchor));
        assertEquals(9.0, RingPortalDestinationBounds.periodicDistanceSquared(
                MEDIUM, anchor, new BlockPos(16_383, 70, 0)));
    }

}
