package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RingGenerationBoundaryTest {
    @Test
    void defaultWallHeightUsesTheVanillaOverworldBottom() {
        assertEquals(96, RingGenerationBoundary.wallTopExclusive(-64, 384, 160));
    }

    @Test
    void customWallHeightUsesTheSameSharedGenerationBound() {
        assertEquals(128, RingGenerationBoundary.wallTopExclusive(-64, 384, 192));
    }

    @Test
    void wallHeightClampsAtTheWorldTop() {
        assertEquals(320, RingGenerationBoundary.wallTopExclusive(-64, 384, 512));
    }

    @Test
    void invalidCoordinateDomainInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RingGenerationBoundary.wallTopExclusive(-64, 0, 160));
        assertThrows(IllegalArgumentException.class,
                () -> RingGenerationBoundary.wallTopExclusive(-64, -1, 160));
        assertThrows(IllegalArgumentException.class,
                () -> RingGenerationBoundary.wallTopExclusive(-64, 384, -1));
    }
}
