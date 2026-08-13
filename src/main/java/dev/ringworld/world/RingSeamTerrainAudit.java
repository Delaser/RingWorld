package dev.ringworld.world;

/** Detects a broad seam-aligned cliff without rejecting isolated natural height steps. */
public final class RingSeamTerrainAudit {
    public static final int CLIFF_HEIGHT_BLOCKS = 12;
    public static final int MAX_CONTIGUOUS_CLIFF_COLUMNS = 15;
    public static final double MAX_SMOOTH_JOIN_AVERAGE_DELTA = 2.0;

    private RingSeamTerrainAudit() { }

    public static Report inspect(int[] highSideHeights, int[] lowSideHeights) {
        if (highSideHeights.length == 0 || highSideHeights.length != lowSideHeights.length) {
            throw new IllegalArgumentException("seam height strips must have equal non-zero length");
        }
        int largestDelta = 0;
        int cliffColumns = 0;
        int longestRun = 0;
        int run = 0;
        long totalDelta = 0L;
        for (int index = 0; index < highSideHeights.length; index++) {
            int delta = Math.abs(highSideHeights[index] - lowSideHeights[index]);
            largestDelta = Math.max(largestDelta, delta);
            totalDelta += delta;
            if (delta >= CLIFF_HEIGHT_BLOCKS) {
                cliffColumns++;
                longestRun = Math.max(longestRun, ++run);
            } else {
                run = 0;
            }
        }
        return new Report(largestDelta, cliffColumns, longestRun,
                (double)totalDelta / highSideHeights.length,
                longestRun <= MAX_CONTIGUOUS_CLIFF_COLUMNS);
    }

    public record Report(int largestDelta, int cliffColumns, int longestCliffRun,
                         double averageAbsoluteDelta, boolean passes) {
        /** Rejects a broad lower wall while allowing isolated natural relief. */
        public boolean passesSmoothJoin() {
            return passes && averageAbsoluteDelta <= MAX_SMOOTH_JOIN_AVERAGE_DELTA;
        }
    }
}
