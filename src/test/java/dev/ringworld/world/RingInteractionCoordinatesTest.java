package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingInteractionCoordinatesTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(256, 16_384);

    @Test
    void canonicalEastSeamFaceKeepsItsLocalHitCoordinate() {
        assertHit(16_383, 16_384.0, 16_383, 16_384.0);
    }

    @Test
    void canonicalWestSeamFaceRemainsUnchanged() {
        assertHit(0, 0.0, 0, 0.0);
    }

    @Test
    void positivePresentationChartMovesBlockAndHitTogether() {
        assertHit(16_384, 16_384.0, 0, 0.0);
        assertHit(16_384, 16_385.0, 0, 1.0);
        assertHit(32_771, 32_772.0, 3, 4.0);
    }

    @Test
    void negativePresentationChartMovesBlockAndHitTogether() {
        assertHit(-1, -1.0, 16_383, 16_383.0);
        assertHit(-1, 0.0, 16_383, 16_384.0);
    }

    private static void assertHit(int sourceBlockX, double sourceHitX,
                                  int expectedBlockX, double expectedHitX) {
        RingInteractionCoordinates.CanonicalBlockHit actual =
                RingInteractionCoordinates.canonicalizeBlockHit(
                        GEOMETRY, sourceBlockX, sourceHitX);
        assertEquals(expectedBlockX, actual.blockX());
        assertEquals(expectedHitX, actual.hitX());
        assertEquals(sourceHitX - sourceBlockX,
                actual.hitX() - actual.blockX());
    }
}
