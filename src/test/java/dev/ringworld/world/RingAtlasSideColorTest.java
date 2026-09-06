package dev.ringworld.world;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RingAtlasSideColorTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(128, 2048);

    @Test void sideMaterialSurvivesSnapshotWireAndDisk(@TempDir Path directory) throws Exception {
        var atlas = new RingTerrainAtlas(GEOMETRY, 12, 1);
        atlas.putCell(3, 4, 96, 0x22AA22, 5, 0x888888);
        var snapshot = atlas.snapshot();
        atlas.putCell(3, 4, 96, 0x22AA22, 5, 0xAA6633);
        assertEquals(0x888888, snapshot.cellSideColor(3, 4));
        var client = new RingTerrainAtlas(GEOMETRY, 12, 1);
        client.applyTile(0, 0, snapshot.encodeTile(0, 0));
        assertEquals(0x888888, client.cellSideColor(3, 4));
        Path path = directory.resolve("atlas.gz"); client.save(path);
        assertEquals(0x888888, RingTerrainAtlas.load(path, GEOMETRY, 12).cellSideColor(3, 4));
        assertEquals(0x22AA22, client.cellColor(3, 4));
        assertEquals(5, client.cellBlockLight(3, 4));
    }

    @Test void sideChangesInvalidateMeshButOrdinaryTopColourChangesDoNot() {
        var atlas = new RingTerrainAtlas(GEOMETRY, 12, 1);
        atlas.putCell(0, 0, 90, 0x228822);
        long original = atlas.surfaceHeightFingerprint();
        atlas.putCell(0, 0, 90, 0x229922);
        assertEquals(original, atlas.surfaceHeightFingerprint());
        atlas.putCell(0, 0, 90, 0x229922, 0, 0x888888);
        assertNotEquals(original, atlas.surfaceHeightFingerprint());
    }

    @Test void steepCliffEmitsSideMaterialWithoutChangingVertexCount() {
        var atlas = new RingTerrainAtlas(GEOMETRY, 12, 1);
        for (int row=0; row<atlas.rows(); row++) for (int col=0; col<atlas.columns(); col++)
            atlas.putCell(col,row,col<1024?64:96,0x228822,0,0x888888);
        var mesh = RingSurfaceMesh.build(GEOMETRY,atlas,true,64,64,1,
                RingLodQuality.HIGH.profile(GEOMETRY,96));
        int[] counts = new int[2];
        mesh.emitTriangles(new RingSurfaceMesh.VertexConsumer() {
            int material = -1;
            @Override public void sideColor(int rgb) { material=rgb; }
            @Override public void vertex(float x,float y,float z,float u,float v) {
                counts[0]++;
                if(u>=2) { assertEquals(0x888888,material); counts[1]++; }
                else assertEquals(-1,material);
            }
        });
        assertEquals(mesh.vertexCount(),counts[0]);
        assertTrue(counts[1]>0);
    }
}
