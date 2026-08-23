package dev.ringworld.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class RingHandoffAvailabilityTest {
    @Test
    void completesTheLiveFadeInsideTheContiguousChunkEdge() {
        assertEquals(208.0 / RingRenderProfile.LIVE_FADE_END_FACTOR,
                RingHandoffAvailability.targetProfileBlocks(
                        16, 4_096.0, 256, 13, 13),
                1.0e-9);
    }

    @Test
    void retainsAProtectedLocalInteractionRadiusDuringInitialStreaming() {
        assertEquals(RingHandoffAvailability.MINIMUM_LOCAL_RADIUS_BLOCKS
                        / RingRenderProfile.LIVE_FADE_END_FACTOR,
                RingHandoffAvailability.targetProfileBlocks(
                        16, 4_096.0, 256, 0, 0),
                1.0e-9);
    }

    @Test
    void growsGraduallyButContractsImmediately() {
        assertEquals(164.0, RingHandoffAvailability.smooth(160.0, 224.0));
        assertEquals(144.0, RingHandoffAvailability.smooth(160.0, 144.0));
        assertThrows(IllegalArgumentException.class,
                () -> RingHandoffAvailability.targetProfileBlocks(
                        16, 4_096.0, 256, 17, 13));
    }
}
