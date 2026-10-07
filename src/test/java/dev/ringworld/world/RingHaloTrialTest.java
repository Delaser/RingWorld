package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingHaloTrialTest {
    private final RingGeometry geometry = new RingGeometry(RingHaloTrial.WIDTH, RingHaloTrial.CIRCUMFERENCE);

    @Test void literalDimensionsAreChunkAlignedAndWithinDiameterRoundingTolerance() {
        assertEquals(0, geometry.widthBlocks() % 16);
        assertEquals(0, geometry.circumferenceBlocks() % 16);
        assertEquals(10_000_000.0, geometry.diameter(), 3.0);
        assertEquals(318_000, geometry.widthBlocks());
        assertFalse(RingHaloTrial.active(geometry));
    }

    @Test void normalAtlasStillRejectsTheHugeAllocationAndInvalidSampling() {
        assertThrows(IllegalArgumentException.class, () -> new RingTerrainAtlas(geometry, 123));
        assertThrows(IllegalArgumentException.class, () -> new RingTerrainAtlas(geometry, 123, 16384));
        assertFalse(RingDimensionReport.forVanillaOverworld(geometry, 160).isValid());
    }

    @Test void placeholderAndEveryLodSnapshotStayEmptyAndBounded() {
        var atlas = RingTerrainAtlas.haloPlaceholder(geometry, 123);
        assertEquals(1918, atlas.columns());
        assertEquals(20, atlas.rows());
        assertTrue(atlas.estimatedMemoryBytes() < 500_000);
        assertFalse(atlas.isComplete());
        for (var quality : RingLodQuality.values()) {
            var snapshot = quality.displaySnapshot(atlas);
            assertEquals(geometry, snapshot.geometry());
            assertEquals(0, snapshot.presentCount());
            assertEquals(atlas.cellCount(), snapshot.cellCount());
            var mesh = RingSurfaceMesh.build(geometry, snapshot, false, 64, 96, 7,
                    quality.profile(geometry, 192));
            assertTrue(mesh.vertexCount() < 350_000);
        }
        assertThrows(IllegalArgumentException.class, () -> RingTerrainAtlas.haloPlaceholder(
                new RingGeometry(128, 2048), 123));
    }
}
