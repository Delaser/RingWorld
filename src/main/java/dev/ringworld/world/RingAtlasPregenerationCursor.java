package dev.ringworld.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic X-major traversal of canonical atlas chunks.
 *
 * <p>Present chunks are the durable resume journal: construction begins at
 * the atlas's first missing chunk, then skips chunks completed while a job is
 * paused or while ordinary play captures terrain. No separate cursor file or
 * power-of-two X arithmetic is needed.</p>
 */
public final class RingAtlasPregenerationCursor {
    private final RingGeometry geometry;
    private final RingTerrainAtlas atlas;
    private final int chunksAcross;
    private final long totalChunks;
    private long nextIndex;

    public RingAtlasPregenerationCursor(RingGeometry geometry, RingTerrainAtlas atlas) {
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.atlas = Objects.requireNonNull(atlas, "atlas");
        if (!geometry.equals(atlas.geometry())) {
            throw new IllegalArgumentException("atlas geometry must match cursor geometry");
        }
        this.chunksAcross = geometry.widthChunks();
        this.totalChunks = checkedTotalChunks(geometry.circumferenceChunks(), chunksAcross);
        this.nextIndex = atlas.firstMissingChunkIndex();
        if (nextIndex < 0 || nextIndex > totalChunks) {
            throw new IllegalStateException("atlas supplied an invalid first missing chunk index");
        }
    }

    public long totalChunks() {
        return totalChunks;
    }

    /** The next canonical traversal index, including chunks that may now be skipped. */
    public long nextIndex() {
        return nextIndex;
    }

    /** Returns the next incomplete canonical chunk, or empty after the complete atlas. */
    public Optional<Chunk> nextChunk() {
        while (nextIndex < totalChunks) {
            Chunk chunk = coordinateAt(nextIndex++);
            if (!atlas.isChunkPresent(chunk.chunkX(), chunk.chunkRow())) {
                return Optional.of(chunk);
            }
        }
        return Optional.empty();
    }

    /** Derives a deterministic canonical X and finite Z coordinate from an index. */
    public Chunk coordinateAt(long index) {
        if (index < 0 || index >= totalChunks) {
            throw new IndexOutOfBoundsException("chunk index outside canonical traversal: " + index);
        }
        int chunkX = Math.toIntExact(index / chunksAcross);
        int chunkRow = Math.toIntExact(index % chunksAcross);
        int chunkZ = Math.addExact(geometry.minChunkZ(), chunkRow);
        return new Chunk(index, chunkX, chunkRow, chunkZ);
    }

    /** Checked multiplication is intentionally exposed for dimension-limit validation tests. */
    public static long checkedTotalChunks(long circumferenceChunks, long widthChunks) {
        if (circumferenceChunks <= 0 || widthChunks <= 0) {
            throw new IllegalArgumentException("chunk dimensions must be positive");
        }
        return Math.multiplyExact(circumferenceChunks, widthChunks);
    }

    /** One canonical chunk in X-major finite-Z-row traversal order. */
    public record Chunk(long index, int chunkX, int chunkRow, int chunkZ) { }
}
