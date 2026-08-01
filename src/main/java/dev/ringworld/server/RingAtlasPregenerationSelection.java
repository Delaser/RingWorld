package dev.ringworld.server;

import dev.ringworld.world.RingAtlasPregenerationCursor;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTerrainAtlas;

import java.util.Optional;

/**
 * Server-service scheduling seam kept free of Minecraft runtime types.
 *
 * <p>The cursor advances when a chunk is selected. This holder retains that
 * selection until the service confirms a full chunk was captured, preventing
 * a failed future from silently skipping canonical terrain.</p>
 */
final class RingAtlasPregenerationSelection {
    private final RingAtlasPregenerationCursor cursor;
    private RingAtlasPregenerationCursor.Chunk selected;
    private long retryAfterTick;
    private int retryAttempt;

    RingAtlasPregenerationSelection(RingGeometry geometry, RingTerrainAtlas atlas) {
        cursor = new RingAtlasPregenerationCursor(geometry, atlas);
    }

    long totalChunks() { return cursor.totalChunks(); }
    RingAtlasPregenerationCursor.Chunk selected() { return selected; }
    boolean accepts(int chunkX, int chunkZ) {
        return selected != null && selected.chunkX() == chunkX && selected.chunkZ() == chunkZ;
    }
    boolean mayRetryAt(long tick) { return tick >= retryAfterTick; }

    Optional<RingAtlasPregenerationCursor.Chunk> select() {
        if (selected == null) selected = cursor.nextChunk().orElse(null);
        return Optional.ofNullable(selected);
    }

    void captured() {
        if (selected == null) throw new IllegalStateException("no selected chunk was captured");
        selected = null;
        retryAttempt = 0;
        retryAfterTick = 0L;
    }

    /** @return false after the bounded retry policy is exhausted. */
    boolean failed(long tick, int maxRetries) {
        if (selected == null) throw new IllegalStateException("no selected chunk failed");
        retryAttempt++;
        if (retryAttempt > maxRetries) return false;
        retryAfterTick = tick + 20L * retryAttempt;
        return true;
    }

    int retryAttempt() { return retryAttempt; }
}
