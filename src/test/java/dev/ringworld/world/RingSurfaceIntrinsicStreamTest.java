package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingSurfaceIntrinsicStreamTest {
    @Test void metadataPreservesHighTerrainAndEverySharedSeamVertexWithoutAnInverse() {
        var geometry = new RingGeometry(128, 1024);
        var atlas = new RingTerrainAtlas(geometry, 23, 8);
        for (int row = 0; row < atlas.rows(); row++) for (int column = 0; column < atlas.columns(); column++)
            atlas.putCell(column, row, 319, 0x556677);
        var mesh = RingSurfaceMesh.build(geometry, atlas, true, 64, 96, 160,
                RingWallStyle.DEFAULT, 255, RingLodQuality.HIGH.profile(geometry, 96));
        assertTrue(geometry.physicalRadiusAt(319) < 0, "exercise the ambiguous physical inverse");
        int[] counts = new int[2];
        mesh.emitTriangles(new RingSurfaceMesh.VertexConsumer() {
            float canonicalX, canonicalY, canonicalZ;
            boolean ready;
            @Override public void intrinsic(float x, float y, float z) {
                assertFalse(ready);
                assertTrue(x >= 0 && x < geometry.circumferenceBlocks());
                canonicalX = x; canonicalY = y; canonicalZ = z; ready = true;
            }
            @Override public void vertex(float x, float y, float z, float u, float v) {
                assertTrue(ready, "every surface, cap, outer wall and closure carries metadata");
                ready = false;
                double angle = canonicalX * Math.PI * 2 / geometry.circumferenceBlocks();
                double radius = geometry.physicalRadiusAt(canonicalY);
                assertEquals(radius * Math.sin(angle), x, 0.0001);
                assertEquals(-radius * Math.cos(angle), y, 0.0001);
                assertEquals(canonicalZ, z);
                counts[0]++;
                if (canonicalY == 319) counts[1]++;
            }
        });
        assertEquals(mesh.vertexCount(), counts[0]);
        assertTrue(counts[1] > 0);
    }
}
