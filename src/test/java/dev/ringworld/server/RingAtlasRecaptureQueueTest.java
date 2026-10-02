package dev.ringworld.server;

import dev.ringworld.world.RingAtlasSurfaceInvalidation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingAtlasRecaptureQueueTest {
    @Test
    void overflowAdvancesEvenWhenEveryExactCellIsRequeued() {
        RingAtlasRecaptureQueue queue = new RingAtlasRecaptureQueue();
        for (int cell = 0; cell < RingAtlasRecaptureQueue.MAX_EXACT_CELLS; cell++) {
            queue.enqueue(new RingAtlasSurfaceInvalidation.Cell(cell, 0));
        }
        var overflow = new RingAtlasSurfaceInvalidation.Cell(4_096, 0);
        queue.enqueue(overflow);
        var first = queue.drain(1, 8_192, 16);
        first.forEach(queue::enqueue);

        assertEquals(List.of(overflow), queue.drain(1, 8_192, 16));
        assertEquals(4_096, queue.exactCount());
        assertEquals(64, queue.drain(64, 8_192, 16).size());
    }

    @Test
    void deduplicatesSmallEditsAndDrainsThemInBoundedBatches() {
        RingAtlasRecaptureQueue queue = new RingAtlasRecaptureQueue();
        var first = new RingAtlasSurfaceInvalidation.Cell(2, 3);
        var second = new RingAtlasSurfaceInvalidation.Cell(4, 5);
        queue.enqueue(first);
        queue.enqueue(first);
        queue.enqueue(second);

        assertEquals(List.of(first), queue.drain(1, 32, 32));
        assertEquals(List.of(second), queue.drain(1, 32, 32));
        assertTrue(!queue.hasPending());
    }

    @Test
    void bulkOverflowCollapsesIntoTilesInsteadOfGrowingExactQueue() {
        RingAtlasRecaptureQueue queue = new RingAtlasRecaptureQueue();
        for (int cell = 0; cell < RingAtlasRecaptureQueue.MAX_EXACT_CELLS + 2_000; cell++) {
            queue.enqueue(new RingAtlasSurfaceInvalidation.Cell(cell, 0));
        }

        assertEquals(RingAtlasRecaptureQueue.MAX_EXACT_CELLS, queue.exactCount());
        assertTrue(queue.overflowTileCount() < 2_000);
        assertEquals(64, queue.drain(64, 8_192, 16).size());
    }
}
