package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingLodQualityTest {
    @Test void exposesOnlyTheThreeAgreedBudgets() {
        assertArrayEquals(new String[]{"low", "medium", "high"},
                java.util.Arrays.stream(RingLodQuality.values()).map(RingLodQuality::command).toArray(String[]::new));
        assertEquals(8, RingLodQuality.LOW.sampleStep());
        assertEquals(2, RingLodQuality.MEDIUM.sampleStep());
        assertEquals(1, RingLodQuality.HIGH.sampleStep());
        assertEquals(8, RingLodQuality.LOW.meshStep());
        assertEquals(4, RingLodQuality.MEDIUM.meshStep());
        assertEquals(1, RingLodQuality.HIGH.meshStep());
    }

    @Test void displayDownsamplingPreservesAuthorityAndExactCaptureAnchors() {
        var geometry = new RingGeometry(128, 2048);
        var source = new RingTerrainAtlas(geometry, 123, 1);
        for (int y=0; y<source.rows(); y++) for (int x=0; x<source.columns(); x++)
            source.putCell(x,y,x % 200, x + y * 2048, (x+y)%16);
        var display = RingLodQuality.MEDIUM.displaySnapshot(source);
        assertEquals(2, display.sampleStep());
        assertEquals(65536, display.cellCount());
        assertTrue(display.isComplete());
        assertEquals(source.worldHash(), display.worldHash());
        assertEquals(source.cellColor(3,5), display.cellColor(1,2));
        assertEquals(source.cellHeight(3,5), display.cellHeight(1,2));
        assertEquals(source.cellBlockLight(3,5), display.cellBlockLight(1,2));
        display.putCell(0,0,0,0);
        assertEquals(262144, source.cellCount());
        assertEquals(1, source.cellHeight(1,1));
    }
    @Test void finerChoiceCannotInventServerSamplesAndHighRetainsFormerMaxBudget() {
        var geometry = new RingGeometry(128, 2048);
        var coarse = new RingTerrainAtlas(geometry, 123, 8);
        assertEquals(8, RingLodQuality.HIGH.displaySnapshot(coarse).sampleStep());
        var two=RingLodQuality.MEDIUM.profile(geometry,96);
        var one=RingLodQuality.HIGH.profile(geometry,96);
        assertEquals(two.circumferenceSegments()*4,one.circumferenceSegments());
        assertEquals(two.widthBands()*4,one.widthBands());
        assertEquals(two.textureColumns(),one.textureColumns());
        assertTrue(RingLodQuality.HIGH.profile(geometry,96,512).textureColumns()<=512);
    }
}
