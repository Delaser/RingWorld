package dev.ringworld.world;

/**
 * Measured-reference operational cost estimate for one immutable layout.
 *
 * <p>The generation and disk figures scale the production benchmark rather
 * than claiming to predict every machine or seed. Atlas transfer is an exact
 * lower bound from the current tile dimensions and eight-tile-per-tick
 * throttle.</p>
 */
public record RingDimensionCostEstimate(
        long estimatedPregenerationSeconds,
        long estimatedGeneratedWorldBytes,
        long estimatedAtlasWireBytes,
        long minimumAtlasTransferTicks) {
    public static final long REFERENCE_CANONICAL_CHUNKS = 16_384L;
    public static final long REFERENCE_PREGENERATION_SECONDS = 817L;
    public static final long REFERENCE_GENERATED_WORLD_BYTES = 177_523_917L;
    public static final int ATLAS_TILES_PER_TICK = 8;

    public static RingDimensionCostEstimate estimate(
            RingGeometry geometry, int atlasSampleStepBlocks) {
        if (atlasSampleStepBlocks <= 0 || 16 % atlasSampleStepBlocks != 0) {
            throw new IllegalArgumentException("atlas sample step must divide one chunk");
        }
        long chunks = Math.multiplyExact(
                (long)geometry.circumferenceChunks(), geometry.widthChunks());
        long columns = divideCeil(geometry.circumferenceBlocks(), atlasSampleStepBlocks);
        long rows = divideCeil(geometry.widthBlocks(), atlasSampleStepBlocks);
        long cells = Math.multiplyExact(columns, rows);
        long tileColumns = divideCeil(columns, RingTerrainAtlas.TILE_SIZE);
        long tileRows = divideCeil(rows, RingTerrainAtlas.TILE_SIZE);
        long tiles = Math.multiplyExact(tileColumns, tileRows);
        long wireBytes = Math.addExact(Math.multiplyExact(cells, 7L), Math.multiplyExact(tiles, 2L));
        return new RingDimensionCostEstimate(
                scaleCeil(chunks, REFERENCE_PREGENERATION_SECONDS),
                scaleCeil(chunks, REFERENCE_GENERATED_WORLD_BYTES),
                wireBytes,
                divideCeil(tiles, ATLAS_TILES_PER_TICK));
    }

    private static long scaleCeil(long chunks, long referenceValue) {
        return divideCeil(Math.multiplyExact(chunks, referenceValue), REFERENCE_CANONICAL_CHUNKS);
    }

    private static long divideCeil(long value, long divisor) {
        return Math.floorDiv(Math.addExact(value, divisor - 1L), divisor);
    }
}
