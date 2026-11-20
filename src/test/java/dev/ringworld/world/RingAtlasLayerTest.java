package dev.ringworld.world;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RingAtlasLayerTest {
    private final RingGeometry geometry = new RingGeometry(128, 2048);

    @Test void editsSurviveDiskWireAndMediumDownsampling(@TempDir Path dir) throws Exception {
        var source = new RingTerrainAtlas(geometry, 92, 1);
        for (int z = 0; z < source.rows(); z++) for (int x = 0; x < source.columns(); x++)
            source.putCell(x, z, 64, 0x85714a);
        int span = 0x820a;
        long fingerprint = source.contentFingerprint();
        var old = source.snapshot();
        source.putCell(1, 1, 64, 0x85714a, 4, 0x64523a, 3, span);
        assertNotEquals(fingerprint, source.contentFingerprint());
        assertEquals(0, old.cellLayer(1, 1));
        assertEquals(span, RingLodQuality.MEDIUM.displaySnapshot(source).cellLayer(0, 0));
        var client = new RingTerrainAtlas(geometry, 92, 1);
        assertTrue(client.applyTile(0, 0, source.encodeTile(0, 0)));
        assertFalse(client.applyTile(0, 0, source.encodeTile(0, 0)));
        var file = dir.resolve("atlas.gz");
        client.save(file);
        var restored = RingTerrainAtlas.load(file, geometry, 92);
        assertEquals(span, restored.cellLayer(1, 1));
        assertEquals(client.contentFingerprint(), restored.contentFingerprint());
        assertEquals(0x85714a, restored.cellColor(1, 1));
        assertEquals(0x64523a, restored.cellSideColor(1, 1));
        assertEquals(3, restored.cellWaterCoverage(1, 1));
        long mesh = restored.surfaceHeightFingerprint();
        restored.putCell(1, 1, 64, 0x85714a, 4, 0x64523a, 3);
        assertNotEquals(mesh, restored.surfaceHeightFingerprint());
        assertEquals(0, restored.cellLayer(1, 1));
    }

    @Test void layerDrawsCloseAcrossWrapAndStayInsideTheAllocatedBuffer() {
        var atlas = new RingTerrainAtlas(geometry, 92, 2);
        for (int z = 0; z < atlas.rows(); z++) for (int x = 0; x < atlas.columns(); x++) {
            boolean cap = z > 18 && z < 30 && (x < 6 || x > atlas.columns() - 6);
            atlas.putCell(x, z, 70, 0x67605b, 0, 0x67605b, 0, cap ? 0x820a : 0);
        }
        var profile = RingLodQuality.MEDIUM.profile(geometry, 192);
        var mesh = RingSurfaceMesh.build(geometry, atlas, true, 64, 80, 16, RingWallStyle.DEFAULT, 0L, profile, true);
        int[] counts = {0, 0};
        var levels = new java.util.HashSet<Integer>();
        mesh.emitTriangles((x, y, z, u, v) -> {
            counts[0]++;
            if (u >= RingLayerBand.REPLACEMENT) {
                counts[1]++;
                int level = (int)Math.round(geometry.physicalCenterY() - Math.hypot(x, y));
                levels.add(level);
            }
        });
        // The gap is ten blocks. Its only faces are the ground, soffit, and deck.
        assertEquals(java.util.Set.of(70, 80, 81), levels);
        int[] offsets = mesh.layerOffsets();
        assertNotNull(offsets);
        assertEquals(mesh.vertexCount(), counts[0]);
        assertEquals(counts[1], mesh.vertexCount() - offsets[0]);
        assertTrue(offsets[1] > offsets[0]);
        assertTrue(offsets[mesh.segments()] > offsets[mesh.segments()-1]);
        for (int i = 1; i < offsets.length; i++) assertTrue(offsets[i] >= offsets[i-1]);
        assertEquals(mesh.vertexCount(), offsets[mesh.segments()]);
    }
}
