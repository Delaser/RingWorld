package dev.ringworld.world;

/** Intrinsic-Z clipping planes for the finite RingWorld cloud deck. */
public record RingCloudBounds(double minimumZ, double maximumZ) {
    /** One deck altitude for CPU face selection and GPU projection. */
    public static int baseHeight(int worldBottomY, int wallHeightBlocks) {
        return Math.addExact(Math.addExact(worldBottomY, wallHeightBlocks),
                RingDimensionReport.CLOUD_CLEARANCE_BLOCKS);
    }

    public RingCloudBounds {
        if (!Double.isFinite(minimumZ) || !Double.isFinite(maximumZ)
                || maximumZ <= minimumZ) {
            throw new IllegalArgumentException("cloud bounds must form a finite interval");
        }
    }

    /**
     * Returns the inner wall-face planes. The maximum is a geometric face,
     * not an included block coordinate.
     */
    public static RingCloudBounds betweenInnerRimFaces(
            RingGeometry geometry, int rimThicknessBlocks) {
        if (rimThicknessBlocks <= 0
                || rimThicknessBlocks * 2 >= geometry.widthBlocks()) {
            throw new IllegalArgumentException("rim thickness leaves no cloud interior");
        }
        return new RingCloudBounds(
                geometry.minWidthZ() + rimThicknessBlocks,
                geometry.maxWidthZ() + 1.0 - rimThicknessBlocks);
    }

    public boolean contains(double intrinsicZ) {
        return intrinsicZ >= minimumZ && intrinsicZ <= maximumZ;
    }
}
