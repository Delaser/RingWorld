package dev.ringworld.world;

/** Pure policy for fitting the visual handoff inside currently available chunks. */
public final class RingHandoffAvailability {
    public static final double MINIMUM_LOCAL_RADIUS_BLOCKS = 6.0 * 16.0;
    public static final double GROWTH_PER_TICK_BLOCKS = 4.0;

    private RingHandoffAvailability() { }

    public static double targetProfileBlocks(int effectiveChunks, double cameraX,
                                             int cameraChunkX,
                                             int positiveChunks, int negativeChunks) {
        if (effectiveChunks <= 0) {
            throw new IllegalArgumentException("effectiveChunks must be positive");
        }
        if (!Double.isFinite(cameraX)) {
            throw new IllegalArgumentException("cameraX must be finite");
        }
        if (positiveChunks < 0 || positiveChunks > effectiveChunks
                || negativeChunks < 0 || negativeChunks > effectiveChunks) {
            throw new IllegalArgumentException(
                    "contiguous chunk counts must be within the effective radius");
        }

        double requestedBlocks = effectiveChunks * 16.0;
        double positiveEdge = (cameraChunkX + positiveChunks + 1) * 16.0 - cameraX;
        double negativeEdge = cameraX - (cameraChunkX - negativeChunks) * 16.0;
        double loadedRadius = Math.min(positiveEdge, negativeEdge);
        double protectedRadius = Math.min(requestedBlocks,
                Math.max(MINIMUM_LOCAL_RADIUS_BLOCKS, loadedRadius));
        return Math.min(requestedBlocks,
                protectedRadius / RingRenderProfile.LIVE_FADE_END_FACTOR);
    }

    public static double smooth(double previousBlocks, double targetBlocks) {
        if (!Double.isFinite(previousBlocks) || previousBlocks < 0.0
                || !Double.isFinite(targetBlocks) || targetBlocks <= 0.0) {
            throw new IllegalArgumentException("handoff distances must be finite and positive");
        }
        if (previousBlocks == 0.0 || targetBlocks < previousBlocks) return targetBlocks;
        return Math.min(targetBlocks, previousBlocks + GROWTH_PER_TICK_BLOCKS);
    }
}
