package dev.ringworld.server;

import dev.ringworld.world.RingAtlasSurfaceInvalidation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingAtlasRecaptureQueueTest {
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
    void unavailableExactCellsCannotStarveLoadedOverflowWithSingleCellBudget() {
        var queue = new RingAtlasRecaptureQueue();
        for (int i = 0; i < RingAtlasRecaptureQueue.MAX_EXACT_CELLS; i++)
            queue.enqueue(new RingAtlasSurfaceInvalidation.Cell(i, 0));
        var loaded = new RingAtlasSurfaceInvalidation.Cell(RingAtlasRecaptureQueue.MAX_EXACT_CELLS, 0);
        queue.enqueue(loaded);
        var unavailable = queue.drain(1, 8192, 16).getFirst();
        queue.enqueue(unavailable);
        assertEquals(loaded, queue.drain(1, 8192, 16).getFirst());
    }

    @Test
    void deferredEditsExitOnFullChunkCaptureOrReturnForShutdown() {
        var queue = new RingAtlasRecaptureQueue();
        var cleared = new RingAtlasSurfaceInvalidation.Cell(4, 1);
        var remaining = new RingAtlasSurfaceInvalidation.Cell(17, 1);
        queue.defer(cleared, 32);
        queue.defer(remaining, 32);
        assertTrue(queue.drain(64, 32, 16).isEmpty());
        queue.capturedRange(32, 16);
        queue.beginFinalDrain();
        assertEquals(List.of(remaining), queue.drain(64, 32, 16));
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
