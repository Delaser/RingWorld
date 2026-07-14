package dev.ringworld.world;

/** Pure chunk-coordinate operations for the circumference's periodic graph. */
public record RingChunkTopology(int circumferenceChunks) {
    public RingChunkTopology {
        if (circumferenceChunks <= 0) throw new IllegalArgumentException("circumferenceChunks must be positive");
    }

    public int canonicalX(int chunkX) {
        return Math.floorMod(chunkX, circumferenceChunks);
    }

    public int distanceX(int firstX, int secondX) {
        int raw = Math.abs(canonicalX(firstX) - canonicalX(secondX));
        return Math.min(raw, circumferenceChunks - raw);
    }

    public boolean isWithinVanillaDistance(int centerX, int centerZ, int viewDistance,
                                           int x, int z, boolean includeEdge) {
        return isWithinVanillaDistance(circumferenceChunks, centerX, centerZ, viewDistance,
                x, z, includeEdge);
    }

    public static boolean isWithinVanillaDistance(int circumferenceChunks, int centerX, int centerZ,
                                                  int viewDistance, int x, int z, boolean includeEdge) {
        int edgeAllowance = includeEdge ? 2 : 1;
        int first = Math.floorMod(centerX, circumferenceChunks);
        int second = Math.floorMod(x, circumferenceChunks);
        int raw = Math.abs(first - second);
        int periodicDistance = Math.min(raw, circumferenceChunks - raw);
        long dx = Math.max(0, periodicDistance - edgeAllowance);
        long dz = Math.max(0, Math.abs(z - centerZ) - edgeAllowance);
        return dx * dx + dz * dz < (long) viewDistance * viewDistance;
    }
}
