package dev.ringworld.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingAtlasLoadedChunkQueueTest {
    @Test void coalescesRepeatedLoadsWithoutDelayingTheOldestChunk() {
        var queue = new RingAtlasLoadedChunkQueue();
        queue.enqueue(0, -16);
        queue.enqueue(10, 15);
        queue.enqueue(0, -16);
        assertEquals(2, queue.size());
        assertEquals(new RingAtlasLoadedChunkQueue.Position(0, -16), queue.poll());
        assertEquals(new RingAtlasLoadedChunkQueue.Position(10, 15), queue.poll());
        assertNull(queue.poll());
    }

    @Test void explicitCaptureRemovesOnlyItsOwnRequestAndLaterReloadCanBeQueued() {
        var queue = new RingAtlasLoadedChunkQueue();
        queue.enqueue(2047, -1);
        queue.enqueue(2047, 0);
        queue.remove(2047, -1);
        assertFalse(queue.contains(2047, -1));
        assertTrue(queue.contains(2047, 0));
        queue.enqueue(2047, -1);
        assertEquals(new RingAtlasLoadedChunkQueue.Position(2047, 0), queue.poll());
        assertEquals(new RingAtlasLoadedChunkQueue.Position(2047, -1), queue.poll());
    }
}
