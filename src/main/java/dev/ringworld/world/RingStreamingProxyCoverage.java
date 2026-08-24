package dev.ringworld.world;

/**
 * Backport-only coverage floor for the complete-ring proxy while real chunks
 * or their compiled sections are still arriving. It leaves the fixed render
 * profile untouched: an incomplete drawable window gets an opaque Atlas
 * underlay, while a complete window publishes a mathematical no-op beyond the
 * requested live radius.
 */
public final class RingStreamingProxyCoverage {
    private RingStreamingProxyCoverage() { }

    public static Span span(int effectiveChunks, boolean drawableWindowComplete) {
        if (effectiveChunks <= 0) {
            throw new IllegalArgumentException("effectiveChunks must be positive");
        }
        double requestedBlocks = effectiveChunks * 16.0;
        return drawableWindowComplete
                ? new Span(requestedBlocks, requestedBlocks)
                : new Span(0.0, 0.0);
    }

    /**
     * An adjacent camera-chart change introduces chunks only at the outer
     * fringe. It may inherit a proven overlap without exposing sky near the
     * player only when the fixed Experiment 19 proxy is already opaque before
     * the closest possible block in that fringe. For a diagonal chart step,
     * {@code (a,b)} is the current AABB distance in chunk widths. The chunk is
     * new exactly when {@code a^2+b^2 < V^2} but the old chart's
     * {@code (a+1,b+1)} is outside. Diagonal steps are the limiting superset
     * of all eight adjacent directions, so enumerating that integer frontier
     * gives the exact conservative lower bound for 1.21.1
     * {@code ChunkTrackingView}. Vanilla V is at most 32, and this proof runs
     * only on a camera chunk change.
     */
    public static boolean coversAdjacentNewFringe(
            int effectiveChunks, double baseProxyOpaqueFromBlocks) {
        if (effectiveChunks <= 0) {
            throw new IllegalArgumentException("effectiveChunks must be positive");
        }
        if (!Double.isFinite(baseProxyOpaqueFromBlocks)
                || baseProxyOpaqueFromBlocks < 0.0) {
            throw new IllegalArgumentException(
                    "base proxy opaque distance must be finite and non-negative");
        }
        long radiusSquared = (long)effectiveChunks * effectiveChunks;
        long closestSquaredChunkWidths = Long.MAX_VALUE;
        for (int a = 0; a < effectiveChunks; a++) {
            for (int b = 0; b < effectiveChunks; b++) {
                long currentSquared = (long)a * a + (long)b * b;
                if (currentSquared >= radiusSquared) continue;
                long oldA = (long)a + 1L;
                long oldB = (long)b + 1L;
                if (oldA * oldA + oldB * oldB >= radiusSquared) {
                    closestSquaredChunkWidths = Math.min(
                            closestSquaredChunkWidths, currentSquared);
                }
            }
        }
        if (closestSquaredChunkWidths == Long.MAX_VALUE) return false;
        double closestNewFringeBlocks =
                Math.sqrt(closestSquaredChunkWidths) * 16.0;
        return closestNewFringeBlocks >= baseProxyOpaqueFromBlocks;
    }

    /**
     * Returns whether an intrinsic horizontal section AABB reaches the region
     * where Experiment 19's ordinary Atlas proxy is not yet opaque. X uses the
     * nearest periodic image, matching the terrain shader's intrinsic
     * distance; an AABB touching the exact opaque boundary is already safe.
     */
    public static boolean intersectsNonOpaqueProxyRegion(
            RingGeometry geometry, double cameraX, double cameraZ,
            double minX, double maxX, double minZ, double maxZ,
            double baseProxyOpaqueFromBlocks) {
        if (geometry == null) throw new IllegalArgumentException("geometry is required");
        if (!Double.isFinite(cameraX) || !Double.isFinite(cameraZ)
                || !Double.isFinite(minX) || !Double.isFinite(maxX)
                || !Double.isFinite(minZ) || !Double.isFinite(maxZ)
                || minX > maxX || minZ > maxZ
                || !Double.isFinite(baseProxyOpaqueFromBlocks)
                || baseProxyOpaqueFromBlocks < 0.0) {
            throw new IllegalArgumentException(
                    "proxy-region coordinates must be finite and ordered");
        }
        double centerX = (minX + maxX) * 0.5;
        double halfWidthX = (maxX - minX) * 0.5;
        double distanceX = Math.max(0.0,
                Math.abs(geometry.shortestCircumferenceDelta(cameraX, centerX))
                        - halfWidthX);
        double distanceZ = axisDistance(cameraZ, minZ, maxZ);
        double opaqueSquared = baseProxyOpaqueFromBlocks
                * baseProxyOpaqueFromBlocks;
        return distanceX * distanceX + distanceZ * distanceZ < opaqueSquared;
    }

    private static double axisDistance(double point, double minimum, double maximum) {
        if (point < minimum) return minimum - point;
        if (point > maximum) return point - maximum;
        return 0.0;
    }

    public record Span(double fadeStartBlocks, double opaqueFromBlocks) {
        public Span {
            if (!Double.isFinite(fadeStartBlocks) || !Double.isFinite(opaqueFromBlocks)
                    || fadeStartBlocks < 0.0 || opaqueFromBlocks < fadeStartBlocks) {
                throw new IllegalArgumentException("streaming proxy span must be finite and ordered");
            }
        }
    }
}
