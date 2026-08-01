package dev.ringworld.world;

/** Deterministic placement policy for the ring's guaranteed stronghold. */
public final class RingStrongholdPlacement {
    /** Vanilla refuses to add a stronghold piece whose anchor is farther away. */
    public static final int MAX_PIECE_ANCHOR_DISTANCE_BLOCKS = 112;
    private static final int SEAM_CLEARANCE_CHUNKS =
            Math.floorDiv(MAX_PIECE_ANCHOR_DISTANCE_BLOCKS, 16) + 1;
    private static final long SEED_SALT = 0x52494E475354524FL;

    private RingStrongholdPlacement() { }

    /**
     * Selects one canonical start chunk, centred across the finite width and
     * far enough from the circumference seam for vanilla's complete piece graph.
     */
    public static StartChunk guaranteedStart(long worldSeed, RingGeometry geometry) {
        int circumferenceChunks = geometry.circumferenceChunks();
        int usableChunks = circumferenceChunks - SEAM_CLEARANCE_CHUNKS * 2;
        if (usableChunks <= 0) {
            throw new IllegalArgumentException("circumference is too small for a stronghold");
        }

        long mixedSeed = mix64(worldSeed ^ SEED_SALT);
        int chunkX = SEAM_CLEARANCE_CHUNKS + (int)Math.floorMod(mixedSeed, (long)usableChunks);
        return new StartChunk(chunkX, 0);
    }

    public static boolean hasSafeSeamClearance(StartChunk start, RingGeometry geometry) {
        return start.chunkX() >= SEAM_CLEARANCE_CHUNKS
                && start.chunkX() < geometry.circumferenceChunks() - SEAM_CLEARANCE_CHUNKS;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    /** Loader- and Minecraft-bootstrap-neutral result consumed by platform worldgen. */
    public record StartChunk(int chunkX, int chunkZ) { }
}
