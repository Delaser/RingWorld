package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingAtlasPregenerationCursorTest {
    private static final long HASH = 0xBADC0FFEE0DDF00DL;

    @Test
    void enumeratesEveryCanonicalChunkExactlyOnceInXMajorOrder() {
        RingGeometry geometry = new RingGeometry(256, 1_024);
        RingAtlasPregenerationCursor cursor = cursor(geometry);

        List<RingAtlasPregenerationCursor.Chunk> chunks = drain(cursor);

        assertEquals(geometry.circumferenceChunks() * geometry.widthChunks(), chunks.size());
        assertEquals(new RingAtlasPregenerationCursor.Chunk(0, 0, 0, geometry.minChunkZ()),
                chunks.getFirst());
        assertEquals(new RingAtlasPregenerationCursor.Chunk(1, 0, 1, geometry.minChunkZ() + 1),
                chunks.get(1));
        assertEquals(new RingAtlasPregenerationCursor.Chunk(16, 1, 0, geometry.minChunkZ()),
                chunks.get(16));
        Set<String> coordinates = new HashSet<>();
        for (RingAtlasPregenerationCursor.Chunk chunk : chunks) {
            assertTrue(chunk.chunkX() >= 0 && chunk.chunkX() < geometry.circumferenceChunks());
            assertTrue(chunk.chunkZ() >= geometry.minChunkZ() && chunk.chunkZ() <= geometry.maxChunkZ());
            assertTrue(coordinates.add(chunk.chunkX() + ":" + chunk.chunkZ()));
        }
    }

    @Test
    void preservesCanonicalCoordinatesForNonPowerOfTwoCircumference() {
        RingGeometry geometry = new RingGeometry(256, 1_040);
        RingAtlasPregenerationCursor cursor = cursor(geometry);

        assertEquals(1_040, cursor.totalChunks());
        assertEquals(new RingAtlasPregenerationCursor.Chunk(1_039, 64, 15, geometry.minChunkZ() + 15),
                cursor.coordinateAt(1_039));
    }

    @Test
    void resumesAtFirstMissingChunkAndSkipsChunksCompletedDuringTraversal() {
        RingGeometry geometry = new RingGeometry(256, 1_024);
        RingTerrainAtlas atlas = new RingTerrainAtlas(geometry, HASH);
        completeChunk(atlas, 0, 0);
        completeChunk(atlas, 0, 1);
        completeChunk(atlas, 0, 3);
        RingAtlasPregenerationCursor cursor = new RingAtlasPregenerationCursor(geometry, atlas);

        assertEquals(2, cursor.nextIndex());
        assertEquals(2, cursor.nextChunk().orElseThrow().index());
        completeChunk(atlas, 0, 4);
        assertEquals(5, cursor.nextChunk().orElseThrow().index());
    }

    @Test
    void checkedTotalsRejectOverflowAndInvalidDimensionsBeforeTraversal() {
        assertThrows(ArithmeticException.class,
                () -> RingAtlasPregenerationCursor.checkedTotalChunks(Long.MAX_VALUE, 2));
        assertThrows(IllegalArgumentException.class,
                () -> RingAtlasPregenerationCursor.checkedTotalChunks(0, 1));
        assertThrows(IndexOutOfBoundsException.class,
                () -> cursor(new RingGeometry(256, 1_024)).coordinateAt(-1));
    }

    @Test
    void optionsHaveConservativeDefaultsAndRejectInvalidPolicy() {
        for (AtlasPregenerationMode mode : AtlasPregenerationMode.values()) {
            AtlasPregenerationOptions options = AtlasPregenerationOptions.defaults(mode);
            assertEquals(1, options.maxInFlightChunks());
            assertEquals(64, options.pendingTaskSoftLimit());
            assertEquals(mode == AtlasPregenerationMode.HEADLESS_PREWARM,
                    options.stopServerWhenComplete());
        }
        assertThrows(IllegalArgumentException.class,
                () -> new AtlasPregenerationOptions(AtlasPregenerationMode.BACKGROUND,
                        0, 64, 200, 20, false));
        assertThrows(IllegalArgumentException.class,
                () -> new AtlasPregenerationOptions(AtlasPregenerationMode.INTERACTIVE,
                        1, 64, 200, 20, true));
    }

    @Test
    void stateTransitionsAndZeroWorkProgressAreExplicit() {
        assertTrue(AtlasPregenerationState.IDLE.canTransitionTo(AtlasPregenerationState.RUNNING));
        assertTrue(AtlasPregenerationState.RUNNING.canTransitionTo(AtlasPregenerationState.PAUSED));
        assertTrue(AtlasPregenerationState.PAUSED.canTransitionTo(AtlasPregenerationState.RUNNING));
        assertTrue(AtlasPregenerationState.RUNNING.canTransitionTo(AtlasPregenerationState.CANCELLED));
        assertTrue(AtlasPregenerationState.SAVING.canTransitionTo(AtlasPregenerationState.COMPLETE));
        assertTrue(AtlasPregenerationState.FAILED.isTerminal());
        assertFalse(AtlasPregenerationState.COMPLETE.canTransitionTo(AtlasPregenerationState.RUNNING));

        AtlasPregenerationProgress progress = AtlasPregenerationProgress.snapshot(
                AtlasPregenerationState.RUNNING, 0, 32, 0, 128,
                Duration.ZERO, Optional.empty());
        assertEquals(0.0, progress.cellsPerSecond());
        assertTrue(progress.eta().isEmpty());
        assertEquals(Optional.of(Duration.ZERO),
                AtlasPregenerationProgress.estimateEta(128, 128, 0.0));
    }

    private static RingAtlasPregenerationCursor cursor(RingGeometry geometry) {
        return new RingAtlasPregenerationCursor(geometry, new RingTerrainAtlas(geometry, HASH));
    }

    private static List<RingAtlasPregenerationCursor.Chunk> drain(RingAtlasPregenerationCursor cursor) {
        List<RingAtlasPregenerationCursor.Chunk> chunks = new ArrayList<>();
        Optional<RingAtlasPregenerationCursor.Chunk> chunk;
        while ((chunk = cursor.nextChunk()).isPresent()) chunks.add(chunk.orElseThrow());
        return chunks;
    }

    private static void completeChunk(RingTerrainAtlas atlas, int chunkX, int chunkRow) {
        RingGeometry geometry = atlas.geometry();
        int firstX = chunkX * 16;
        int firstZ = geometry.minWidthZ() + chunkRow * 16;
        for (int z = 4; z < 16; z += RingTerrainAtlas.SAMPLE_STEP_BLOCKS) {
            for (int x = 4; x < 16; x += RingTerrainAtlas.SAMPLE_STEP_BLOCKS) {
                atlas.putBlockSample(firstX + x, firstZ + z, 70, 0x445566);
            }
        }
    }
}
