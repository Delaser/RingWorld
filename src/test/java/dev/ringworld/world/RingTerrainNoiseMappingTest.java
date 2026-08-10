package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingTerrainNoiseMappingTest {
    @Test
    void legacyMappingRetainsTheAlphaCoordinateFormula() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        RingNoiseCoordinates coordinates = RingNoiseCoordinates.forGeometry(
                geometry, RingTerrainNoiseMapping.LEGACY_AXIAL);
        int[] xs = {0, 1, 2_048, 4_096, 8_192, 12_288, 16_383};
        int[] zs = {-128, -1, 0, 37, 127};

        for (int x : xs) {
            double angle = Math.PI * 2.0 * x / geometry.circumferenceBlocks();
            int expectedX = (int)Math.round(geometry.radius() * Math.sin(angle));
            int expectedOffsetZ = (int)Math.round(geometry.radius() * Math.cos(angle));
            for (int z : zs) {
                assertEquals(expectedX, coordinates.noiseX(x, z));
                assertEquals(z + expectedOffsetZ, coordinates.noiseZ(x, z));
            }
        }
    }

    @Test
    void annularMappingIsExactAtSeamAliasesAndCardinalLongitudes() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        RingNoiseCoordinates coordinates = RingNoiseCoordinates.forGeometry(
                geometry, RingTerrainNoiseMapping.ANNULAR);
        int radius = (int)Math.round(geometry.radius());

        assertEquals(0, coordinates.noiseX(0, 0));
        assertEquals(radius, coordinates.noiseZ(0, 0));
        assertEquals(radius, coordinates.noiseX(4_096, 0));
        assertEquals(0, coordinates.noiseZ(4_096, 0));
        assertEquals(0, coordinates.noiseX(8_192, 0));
        assertEquals(-radius, coordinates.noiseZ(8_192, 0));
        assertEquals(-radius, coordinates.noiseX(12_288, 0));
        assertEquals(0, coordinates.noiseZ(12_288, 0));

        for (int z : new int[] {-128, -1, 0, 37, 127}) {
            assertEquals(coordinates.noiseX(0, z), coordinates.noiseX(16_384, z));
            assertEquals(coordinates.noiseZ(0, z), coordinates.noiseZ(16_384, z));
            assertEquals(coordinates.noiseX(7, z), coordinates.noiseX(7 - 16_384, z));
            assertEquals(coordinates.noiseZ(7, z), coordinates.noiseZ(7 + 32_768, z));
        }
    }

    @Test
    void annularWidthDirectionNeverCollapsesIntoTheCircumferenceDirection() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        double epsilon = 1.0e-3;
        double[] zs = {
                geometry.minWidthZ() - RingTerrainNoiseMapping.QUERY_MARGIN_BLOCKS,
                0.0,
                geometry.maxWidthZ() + RingTerrainNoiseMapping.QUERY_MARGIN_BLOCKS
        };
        for (int x : new int[] {0, 2_048, 4_096, 8_192, 12_288, 16_383}) {
            for (double z : zs) {
                RingTerrainNoiseMapping.ContinuousCoordinate center =
                        RingTerrainNoiseMapping.continuousAnnular(geometry, x, z);
                RingTerrainNoiseMapping.ContinuousCoordinate along =
                        RingTerrainNoiseMapping.continuousAnnular(geometry, x + epsilon, z);
                RingTerrainNoiseMapping.ContinuousCoordinate across =
                        RingTerrainNoiseMapping.continuousAnnular(geometry, x, z + epsilon);
                double alongX = (along.x() - center.x()) / epsilon;
                double alongZ = (along.z() - center.z()) / epsilon;
                double acrossX = (across.x() - center.x()) / epsilon;
                double acrossZ = (across.z() - center.z()) / epsilon;
                double dot = alongX * acrossX + alongZ * acrossZ;
                double determinant = alongX * acrossZ - alongZ * acrossX;

                assertEquals(0.0, dot, 2.0e-5);
                assertTrue(determinant > 0.0,
                        "annular basis collapsed at x=" + x + ", z=" + z
                                + ", determinant=" + determinant);
            }
        }
    }

    @Test
    void mappingCacheKeepsLegacyAndAnnularWorldsIsolated() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        RingNoiseCoordinates legacy = RingNoiseCoordinates.forGeometry(
                geometry, RingTerrainNoiseMapping.LEGACY_AXIAL);
        RingNoiseCoordinates annular = RingNoiseCoordinates.forGeometry(
                geometry, RingTerrainNoiseMapping.ANNULAR);

        assertTrue(legacy != annular);
        assertEquals(RingTerrainNoiseMapping.LEGACY_AXIAL, legacy.mappingVersion());
        assertEquals(RingTerrainNoiseMapping.ANNULAR, annular.mappingVersion());
        assertNotEquals(legacy.noiseX(2_048, 127), annular.noiseX(2_048, 127));
    }

    @Test
    void discreteCardinalSamplesRetainRadialWidthVariation() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        RingNoiseCoordinates coordinates = RingNoiseCoordinates.forGeometry(
                geometry, RingTerrainNoiseMapping.ANNULAR);
        for (int z : new int[] {-128, 0, 127}) {
            int radial = (int)Math.round(geometry.radius() + z);
            assertEquals(0, coordinates.noiseX(0, z));
            assertEquals(radial, coordinates.noiseZ(0, z));
            assertEquals(radial, coordinates.noiseX(4_096, z));
            assertEquals(0, coordinates.noiseZ(4_096, z));
            assertEquals(0, coordinates.noiseX(8_192, z));
            assertEquals(-radial, coordinates.noiseZ(8_192, z));
            assertEquals(-radial, coordinates.noiseX(12_288, z));
            assertEquals(0, coordinates.noiseZ(12_288, z));
        }
    }

    @Test
    void discreteSeamNeighbourRemainsLocalAcrossTheFullSampledWidth() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        RingNoiseCoordinates coordinates = RingNoiseCoordinates.forGeometry(
                geometry, RingTerrainNoiseMapping.ANNULAR);
        for (int z : new int[] {
                geometry.minWidthZ() - RingTerrainNoiseMapping.QUERY_MARGIN_BLOCKS,
                0,
                geometry.maxWidthZ() + RingTerrainNoiseMapping.QUERY_MARGIN_BLOCKS}) {
            int dx = coordinates.noiseX(0, z) - coordinates.noiseX(16_383, z);
            int dz = coordinates.noiseZ(0, z) - coordinates.noiseZ(16_383, z);
            assertTrue(Math.hypot(dx, dz) <= 2.0,
                    "seam neighbour exceeded rounded local step at z=" + z);
        }
    }

    @Test
    void widthAffectsBothNoiseAxesAwayFromCardinalLongitudes() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        RingNoiseCoordinates coordinates = RingNoiseCoordinates.forGeometry(
                geometry, RingTerrainNoiseMapping.ANNULAR);

        assertNotEquals(coordinates.noiseX(2_048, -64), coordinates.noiseX(2_048, 64));
        assertNotEquals(coordinates.noiseZ(2_048, -64), coordinates.noiseZ(2_048, 64));
    }

    @Test
    void supportedPresetsKeepAComfortablePositiveNoiseRadius() {
        RingGeometry[] presets = {
                new RingGeometry(128, 2_048),
                new RingGeometry(256, 16_384),
                new RingGeometry(512, 32_768)
        };
        for (RingGeometry geometry : presets) {
            RingTerrainNoiseMapping.requireSafeNewWorldGeometry(geometry);
            double minimumRadius = geometry.radius() + geometry.minWidthZ()
                    - RingTerrainNoiseMapping.QUERY_MARGIN_BLOCKS;
            assertTrue(minimumRadius >= RingTerrainNoiseMapping.MINIMUM_NOISE_RADIUS_BLOCKS);
        }
    }

    @Test
    void unsafeAnnularBandIsRejectedForNewWorldsOnly() {
        RingGeometry unsafe = new RingGeometry(640, 2_048);

        assertThrows(IllegalArgumentException.class,
                () -> RingTerrainNoiseMapping.requireSafeNewWorldGeometry(unsafe));
        assertEquals(RingTerrainNoiseMapping.LEGACY_AXIAL,
                RingTerrainNoiseMapping.forSettingsFormat(2));
        assertEquals(RingTerrainNoiseMapping.ANNULAR_COMPLETE,
                RingTerrainNoiseMapping.forSettingsFormat(3));
    }

    @Test
    void unknownMappingIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RingTerrainNoiseMapping.requireSupported(0));
        assertEquals(RingTerrainNoiseMapping.ANNULAR_COMPLETE,
                RingTerrainNoiseMapping.requireSupported(3));
        assertThrows(IllegalArgumentException.class,
                () -> RingTerrainNoiseMapping.requireSupported(4));
    }

    @Test
    void annularCoordinatesRejectVanillaIntegerOverflow() {
        RingNoiseCoordinates coordinates = RingNoiseCoordinates.forGeometry(
                new RingGeometry(256, 16_384), RingTerrainNoiseMapping.ANNULAR);

        assertThrows(IllegalArgumentException.class,
                () -> coordinates.noiseZ(0, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> coordinates.noiseZ(8_192, Integer.MAX_VALUE));
    }

    @Test
    void completeMappingGivesCarversOneCanonicalSeedIdentity() {
        RingGeometry geometry = new RingGeometry(256, 16_384);
        assertEquals(1_023, RingTerrainNoiseMapping.carverSeedChunkX(
                geometry, RingTerrainNoiseMapping.ANNULAR_COMPLETE, -1));
        assertEquals(0, RingTerrainNoiseMapping.carverSeedChunkX(
                geometry, RingTerrainNoiseMapping.ANNULAR_COMPLETE, 1_024));
        assertEquals(-1, RingTerrainNoiseMapping.carverSeedChunkX(
                geometry, RingTerrainNoiseMapping.ANNULAR, -1));
    }
}
