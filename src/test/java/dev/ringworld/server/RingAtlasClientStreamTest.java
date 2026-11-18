package dev.ringworld.server;

import dev.ringworld.server.RingAtlasPregenerationService.TileCoordinate;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTerrainAtlas;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingAtlasClientStreamTest {
    @Test
    void initialTransferIncludesUnsentChangesAndReplaysSentChangesOnce() {
        var atlas = new RingTerrainAtlas(new RingGeometry(128, 2048), 17);
        for (int x = 0; x < 4; x++) atlas.putCell(x * 16, 0, 70, 1);
        var stream = new RingTerrainAtlasServer.ClientStream(null, atlas, -1);
        var first = new TileCoordinate(0, 0);
        var pending = new TileCoordinate(3, 0);
        assertEquals(first, stream.nextTile());
        stream.queueChange(pending);
        stream.queueChange(first);
        stream.queueChange(first);
        var newlyCaptured = new TileCoordinate(8, 1);
        stream.queueChange(newlyCaptured);
        assertEquals(first, stream.nextTile());
        assertEquals(new TileCoordinate(1, 0), stream.nextTile());
        assertEquals(newlyCaptured, stream.nextTile());
        assertEquals(new TileCoordinate(2, 0), stream.nextTile());
        assertEquals(pending, stream.nextTile());
        assertFalse(stream.hasTiles());
    }

    @Test
    void completeCacheStreamsOnlyChangedTiles() {
        var stream = new RingTerrainAtlasServer.ClientStream(null, new RingTerrainAtlas(new RingGeometry(512, 32768), 17), 0);
        assertFalse(stream.hasTiles());
        var last = new TileCoordinate(2047, 31);
        stream.queueChange(last);
        assertTrue(stream.hasTiles());
        assertEquals(last, stream.nextTile());
        assertFalse(stream.hasTiles());
    }
}
