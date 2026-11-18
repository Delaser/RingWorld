package dev.ringworld.server;

import dev.ringworld.server.RingAtlasPregenerationService.TileCoordinate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingAtlasClientStreamTest {
    @Test
    void initialTransferIncludesUnsentChangesAndReplaysSentChangesOnce() {
        var stream = new RingTerrainAtlasServer.ClientStream(null, 2, 2, -1);
        var first = new TileCoordinate(0, 0);
        var last = new TileCoordinate(1, 1);
        assertEquals(first, stream.nextTile());
        stream.queueChange(last);
        stream.queueChange(first);
        stream.queueChange(first);
        assertEquals(new TileCoordinate(1, 0), stream.nextTile());
        assertEquals(new TileCoordinate(0, 1), stream.nextTile());
        assertEquals(last, stream.nextTile());
        assertEquals(first, stream.nextTile());
        assertFalse(stream.hasTiles());
    }

    @Test
    void completeCacheStreamsOnlyChangedTiles() {
        var stream = new RingTerrainAtlasServer.ClientStream(null, 2048, 32, 0);
        assertFalse(stream.hasTiles());
        var last = new TileCoordinate(2047, 31);
        stream.queueChange(last);
        assertTrue(stream.hasTiles());
        assertEquals(last, stream.nextTile());
        assertFalse(stream.hasTiles());
    }
}
