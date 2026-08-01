package dev.ringworld.server;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingAtlasDirtyTileQueueTest {
    @Test
    void finalDirtyTileRemainsPublishedUntilAdapterDrainsIt() {
        RingAtlasDirtyTileQueue queue = new RingAtlasDirtyTileQueue();
        RingAtlasPregenerationService.TileCoordinate finalTile =
                new RingAtlasPregenerationService.TileCoordinate(3, 1);

        queue.publish(finalTile);
        assertTrue(queue.hasPending());
        assertEquals(Set.of(finalTile), queue.drain());
        assertFalse(queue.hasPending());
    }
}
