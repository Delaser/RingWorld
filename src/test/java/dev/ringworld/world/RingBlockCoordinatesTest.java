package dev.ringworld.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class RingBlockCoordinatesTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(256, 2_048);

    @Test
    void canonicalizesSeamAliasesAndMultipleLaps() {
        assertEquals(0, RingBlockCoordinates.canonicalBlockX(2_048, GEOMETRY));
        assertEquals(2_047, RingBlockCoordinates.canonicalBlockX(-1, GEOMETRY));
        assertEquals(17, RingBlockCoordinates.canonicalBlockX(17 + 2_048 * 100, GEOMETRY));
        assertEquals(17, RingBlockCoordinates.canonicalBlockX(17 - 2_048 * 100, GEOMETRY));
    }

    @Test
    void selectsNearestImagesAcrossTheSeamInBothDirections() {
        assertEquals(2_048, RingBlockCoordinates.nearestImageBlockX(0, 2_047.0, GEOMETRY));
        assertEquals(-1, RingBlockCoordinates.nearestImageBlockX(2_047, 0.0, GEOMETRY));
        assertEquals(4_096, RingBlockCoordinates.nearestImageBlockX(0, 4_095.0, GEOMETRY));
        assertEquals(-2_049, RingBlockCoordinates.nearestImageBlockX(2_047, -2_048.0, GEOMETRY));
    }

    @Test
    void canonicalizesAliasesBeforeSelectingAnImage() {
        assertEquals(2_048,
                RingBlockCoordinates.nearestImageBlockX(2_048 * 20, 2_047.0, GEOMETRY));
        assertEquals(-1,
                RingBlockCoordinates.nearestImageBlockX(-1 - 2_048 * 20, 0.0, GEOMETRY));
    }

    @Test
    void exactHalfCircumferenceTiesSelectTheEvenLap() {
        assertEquals(0, RingBlockCoordinates.nearestImageBlockX(0, 1_024.0, GEOMETRY));
        assertEquals(4_096, RingBlockCoordinates.nearestImageBlockX(0, 3_072.0, GEOMETRY));
        assertEquals(0, RingBlockCoordinates.nearestImageBlockX(0, -1_024.0, GEOMETRY));
        assertEquals(-4_096, RingBlockCoordinates.nearestImageBlockX(0, -3_072.0, GEOMETRY));
    }

    @Test
    void blockPositionOperationsPreserveYAndZ() {
        BlockPos canonical = RingBlockCoordinates.canonicalBlockPos(
                new BlockPos(-1, 73, -29), GEOMETRY);
        BlockPos image = RingBlockCoordinates.nearestImageBlockPos(
                new BlockPos(2_047, 73, -29), 0.0, GEOMETRY);

        assertEquals(new BlockPos(2_047, 73, -29), canonical);
        assertEquals(new BlockPos(-1, 73, -29), image);
    }

    @Test
    void unchangedPositionsRetainIdentity() {
        BlockPos position = new BlockPos(24, 80, 7);

        assertSame(position, RingBlockCoordinates.canonicalBlockPos(position, GEOMETRY));
        assertSame(position, RingBlockCoordinates.nearestImageBlockPos(position, 30.0, GEOMETRY));
    }

    @Test
    void changedPositionsDoNotMutateTheirInput() {
        BlockPos input = new BlockPos(-1, 91, 12);
        BlockPos result = RingBlockCoordinates.canonicalBlockPos(input, GEOMETRY);

        assertNotSame(input, result);
        assertEquals(new BlockPos(-1, 91, 12), input);
        assertEquals(new BlockPos(2_047, 91, 12), result);
    }

    @Test
    void rejectsNonFiniteAndOutOfRangePresentationImages() {
        assertThrows(IllegalArgumentException.class,
                () -> RingBlockCoordinates.nearestImageBlockX(0, Double.NaN, GEOMETRY));
        assertThrows(IllegalArgumentException.class,
                () -> RingBlockCoordinates.nearestImageBlockX(0, Double.POSITIVE_INFINITY, GEOMETRY));
        assertThrows(IllegalArgumentException.class,
                () -> RingBlockCoordinates.nearestImageBlockX(0, Integer.MAX_VALUE, GEOMETRY));
        assertThrows(IllegalArgumentException.class,
                () -> RingBlockCoordinates.nearestImageBlockX(2_047, Integer.MIN_VALUE, GEOMETRY));
    }
}
