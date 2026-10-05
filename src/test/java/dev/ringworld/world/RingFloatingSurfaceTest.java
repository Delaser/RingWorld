package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class RingFloatingSurfaceTest {
    @Test void floatingPlatformKeepsGroundAndOneBlockThicknessWithoutConnectingCurtains() {
        var geometry = new RingGeometry(128, 2048);
        var authoritative = new RingTerrainAtlas(geometry, 123, 16);
        for (int x=0; x<authoritative.columns(); x++) for (int z=0; z<authoritative.rows(); z++)
            authoritative.putCell(x,z,65,0x446633);
        authoritative.putBlockSample(512,0,320,0xEEEEEE);
        var snapshot = authoritative.snapshot();
        var layer = new RingFloatingSurface(512,0,319,320,65,0x446633,0xEEEEEE,0);
        snapshot.applyFloatingTrial(List.of(layer));
        assertEquals(320,authoritative.cellHeight(32,4));
        assertEquals(65,snapshot.cellHeight(32,4));
        assertTrue(authoritative.floatingTrial().isEmpty());
        assertEquals(List.of(layer),snapshot.snapshot().floatingTrial());
        var mesh = RingSurfaceMesh.build(geometry,snapshot,true,64);
        int[] count = {0,0};
        mesh.emitTriangles((x,y,z,u,v) -> {
            count[0]++;
            double height = geometry.radius()+geometry.surfaceReferenceY()-Math.hypot(x,y);
            if (u>=2) {
                count[1]++;
                assertTrue(Math.abs(height-319)<0.0001 || Math.abs(height-320)<0.0001,
                        "platform vertices must stay within their actual thickness");
            } else assertTrue(height<=65.0001,"terrain must remain below the detached platform");
        });
        assertEquals(mesh.vertexCount(),count[0]);
        assertEquals(36,count[1]);
        assertNotEquals(authoritative.surfaceHeightFingerprint(),snapshot.surfaceHeightFingerprint());
    }
    @Test void groundedColumnCannotBecomeDetachedLayer() {
        assertThrows(IllegalArgumentException.class, () ->
                new RingFloatingSurface(0,0,319,320,315,0,0,0));
    }
    @Test void detachedLayerSurvivesEveryDisplayQuality() {
        var source = new RingTerrainAtlas(new RingGeometry(128,2048),123);
        for (int x=0; x<source.columns(); x++) for (int z=0; z<source.rows(); z++)
            source.putCell(x,z,65,0x446633);
        var layer = new RingFloatingSurface(512,0,319,320,65,0x446633,0xEEEEEE,0);
        source.applyFloatingTrial(List.of(layer));
        for (var quality : RingLodQuality.values()) {
            var displayed = quality.displaySnapshot(source);
            assertEquals(List.of(layer),displayed.floatingTrial(),quality.label());
            assertEquals(65,displayed.cellHeight(512/displayed.sampleStep(),64/displayed.sampleStep()));
        }
    }
}
