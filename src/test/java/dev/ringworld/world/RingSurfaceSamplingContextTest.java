package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingSurfaceSamplingContextTest {
    @Test
    void completeMappingMakesSurfaceNoiseAdjacentAcrossTheSeam() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        RingSurfaceSamplingContext.run(geometry, RingTerrainNoiseMapping.ANNULAR_COMPLETE_V2, () -> {
            RingSurfaceSamplingContext.Coordinates high =
                    RingSurfaceSamplingContext.mapScaled(16_383.0, 31.0, 1.0);
            RingSurfaceSamplingContext.Coordinates low =
                    RingSurfaceSamplingContext.mapScaled(0.0, 31.0, 1.0);
            assertEquals(-1.0, high.x(), 1.0);
            assertEquals(0.0, low.x(), 0.0);
            assertEquals(low.z(), high.z(), 1.0);

            RingSurfaceSamplingContext.Coordinates scaled =
                    RingSurfaceSamplingContext.mapScaled(16_383 * 0.2, 31 * 0.2, 0.2);
            assertEquals(high.x() * 0.2, scaled.x(), 1.0e-9);
            assertEquals(high.z() * 0.2, scaled.z(), 1.0e-9);
        });
    }

    @Test
    void priorMappingsRemainBitCompatibleAndContextDoesNotLeak() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        RingSurfaceSamplingContext.run(geometry, RingTerrainNoiseMapping.ANNULAR, () -> {
            assertEquals(new RingSurfaceSamplingContext.Coordinates(16_383.0, 31.0),
                    RingSurfaceSamplingContext.mapScaled(16_383.0, 31.0, 1.0));
        });
        assertEquals(new RingSurfaceSamplingContext.BlockCoordinates(16_383, 31),
                RingSurfaceSamplingContext.mapBlock(16_383, 31));
    }
}
