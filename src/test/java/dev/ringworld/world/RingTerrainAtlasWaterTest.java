package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class RingTerrainAtlasWaterTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(128, 2048);

    @Test void waterAndLightSurviveSnapshotTileAndDiskIndependently(@TempDir Path directory) throws Exception {
        var source = new RingTerrainAtlas(GEOMETRY, 123, 8);
        for (int water = 0; water <= 15; water++) for (int light = 0; light <= 15; light++)
            source.putCell(water, light, 123, 0x224488, light, 0x887766, water);
        var tile = new RingTerrainAtlas(GEOMETRY, 123, 8);
        tile.applyTile(0, 0, source.encodeTile(0, 0));
        Path cache = directory.resolve("water.rwat.gz");
        source.save(cache);
        var loaded = RingTerrainAtlas.load(cache, GEOMETRY, 123);
        for (var atlas : new RingTerrainAtlas[]{source.snapshot(), tile, loaded}) {
            for (int water = 0; water <= 15; water++) for (int light = 0; light <= 15; light++) {
                assertEquals(water, atlas.cellWaterCoverage(water, light));
                assertEquals(light, atlas.cellBlockLight(water, light));
                assertEquals(0x224488, atlas.cellColor(water, light));
                assertEquals(0x887766, atlas.cellSideColor(water, light));
            }
        }
    }

    @Test void authoredWaterIgnoresColourAndAltitudeAndBlendsAtShore() {
        var atlas = new RingTerrainAtlas(GEOMETRY, 123, 8);
        atlas.putCell(0, 0, 180, 0x224488, 15, 0x224488, 0);
        atlas.putCell(1, 0, 180, 0x224488, 3, 0x224488, 15);
        var dry = atlas.sample(4, GEOMETRY.minWidthZ() + 4);
        var wet = atlas.sample(12, GEOMETRY.minWidthZ() + 4);
        var shore = atlas.sample(8, GEOMETRY.minWidthZ() + 4);
        assertEquals(0x224488, RingWaterColor.tint(dry.color(), dry.waterCoverage()));
        assertNotEquals(dry.color(), RingWaterColor.tint(wet.color(), wet.waterCoverage()));
        assertEquals(0.5, shore.waterCoverage(), 1e-9);
        assertEquals(9, shore.blockLight(), 1e-9);
        assertEquals(0.5, atlas.sample(0, GEOMETRY.minWidthZ() + 4).coverage(), 1e-9);
    }

    @Test void allDisplayLevelsRetainWaterCoverageAndSourceRemainsUnchanged() {
        var atlas = new RingTerrainAtlas(GEOMETRY, 123);
        for (int z = 0; z < atlas.rows(); z++) for (int x = 0; x < atlas.columns(); x++)
            atlas.putCell(x, z, 90, 0x334455, 12, 0x665544, x % 2 == 0 ? 15 : 0);
        for (var quality : RingLodQuality.values()) {
            var display = quality.displaySnapshot(atlas);
            assertEquals(quality == RingLodQuality.HIGH ? 15 : 8, display.cellWaterCoverage(0, 0));
            assertEquals(12, display.cellBlockLight(0, 0));
        }
        assertEquals(0, atlas.cellWaterCoverage(1, 0));
        assertThrows(IllegalArgumentException.class, () -> atlas.putCell(0,0,0,0,0,0,16));
    }

    @Test void acceptedTintAppliesToAnyBiomeColourWithBoundedChannels() {
        assertEquals(0x385798, RingWaterColor.tint(0x254484, 1));
        assertEquals(0xFFFFFF, RingWaterColor.tint(0xFFFFFF, 1));
        assertEquals(0, RingWaterColor.tint(0, 1));
        assertNotEquals(0x446633, RingWaterColor.tint(0x446633, 1));
    }
}
