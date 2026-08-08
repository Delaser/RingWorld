package dev.ringworld.server;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTerrainAtlas;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingAtlasPregenerationSelectionTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(256, 1_024);

    @Test
    void failedSelectedChunkIsRetriedBeforeCursorCanAdvance() {
        RingAtlasPregenerationSelection selection = new RingAtlasPregenerationSelection(
                GEOMETRY, new RingTerrainAtlas(GEOMETRY, 7L));

        var first = selection.select().orElseThrow();
        assertTrue(selection.accepts(first.chunkX(), first.chunkZ()));
        assertFalse(selection.accepts(first.chunkX() + 1, first.chunkZ()));
        assertTrue(selection.failed(10, 3));
        assertFalse(selection.mayRetryAt(29));
        assertEquals(first, selection.select().orElseThrow());
        assertTrue(selection.mayRetryAt(30));

        selection.captured();
        assertEquals(first.index() + 1, selection.select().orElseThrow().index());
    }

    @Test
    void partialAtlasStartsAtItsFirstMissingChunkAndExhaustsBoundedRetries() {
        RingTerrainAtlas atlas = new RingTerrainAtlas(GEOMETRY, 7L);
        completeChunk(atlas, 0, 0);
        RingAtlasPregenerationSelection selection = new RingAtlasPregenerationSelection(GEOMETRY, atlas);

        assertEquals(1, selection.select().orElseThrow().index());
        assertTrue(selection.failed(0, 1));
        assertFalse(selection.failed(20, 1));
        assertEquals(2, selection.retryAttempt());
    }

    @Test
    void shutdownDiscardOfCompletedRequestLeavesSelectedCursorForResume() {
        RingAtlasPregenerationSelection selection = new RingAtlasPregenerationSelection(
                GEOMETRY, new RingTerrainAtlas(GEOMETRY, 7L));
        var selected = selection.select().orElseThrow();
        AtomicInteger resultReads = new AtomicInteger();
        RingAtlasChunkRequest<String> request = RingAtlasChunkRequest.start(
                () -> CompletableFuture.completedFuture(null), () -> {
                    resultReads.incrementAndGet();
                    return "unloaded chunk";
                }, () -> { });

        request.cancel();

        assertEquals(0, resultReads.get());
        assertEquals(selected, selection.select().orElseThrow());
        assertEquals(0, selection.retryAttempt());
    }

    private static void completeChunk(RingTerrainAtlas atlas, int chunkX, int chunkRow) {
        int firstX = chunkX * 16;
        int firstZ = atlas.geometry().minWidthZ() + chunkRow * 16;
        for (int z = 4; z < 16; z += RingTerrainAtlas.SAMPLE_STEP_BLOCKS) {
            for (int x = 4; x < 16; x += RingTerrainAtlas.SAMPLE_STEP_BLOCKS) {
                atlas.putBlockSample(firstX + x, firstZ + z, 70, 0x445566);
            }
        }
    }
}
