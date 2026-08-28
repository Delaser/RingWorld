package dev.ringworld.world;

import net.minecraft.core.BlockPos;

/** Canonical and transient presentation operations for block positions. */
public final class RingBlockCoordinates {
    private RingBlockCoordinates() { }

    public static int canonicalBlockX(int blockX, RingGeometry geometry) {
        return geometry.wrapBlockX(blockX);
    }

    /**
     * Returns the periodic copy of a block X nearest a presentation-chart
     * reference. At an exact half-circumference tie, the even lap is selected
     * to preserve {@link RingGeometry#nearestImageX(double, double)} behavior.
     */
    public static int nearestImageBlockX(int canonicalOrChartBlockX,
                                         double referenceChartX,
                                         RingGeometry geometry) {
        if (!Double.isFinite(referenceChartX)) {
            throw new IllegalArgumentException("reference chart X must be finite");
        }
        int circumference = geometry.circumferenceBlocks();
        int wrapped = canonicalBlockX(canonicalOrChartBlockX, geometry);
        double imageIndex = Math.rint((referenceChartX - wrapped) / circumference);
        double result = wrapped + imageIndex * circumference;
        if (!Double.isFinite(result) || result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("ring block image exceeds vanilla coordinate range");
        }
        return (int) result;
    }

    /** Returns the canonical storage position while preserving Y and Z. */
    public static BlockPos canonicalBlockPos(BlockPos position, RingGeometry geometry) {
        int canonicalX = canonicalBlockX(position.getX(), geometry);
        return canonicalX == position.getX()
                ? position
                : new BlockPos(canonicalX, position.getY(), position.getZ());
    }

    /**
     * Returns a transient presentation position nearest {@code referenceChartX}.
     * The result must not be used as a server storage, cache, or persistence key.
     */
    public static BlockPos nearestImageBlockPos(BlockPos position,
                                                double referenceChartX,
                                                RingGeometry geometry) {
        int imageX = nearestImageBlockX(position.getX(), referenceChartX, geometry);
        return imageX == position.getX()
                ? position
                : new BlockPos(imageX, position.getY(), position.getZ());
    }
}
