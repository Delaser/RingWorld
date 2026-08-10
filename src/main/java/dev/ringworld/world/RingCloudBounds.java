package dev.ringworld.world;

/** Intrinsic-Z clipping planes for the finite RingWorld cloud deck. */
public record RingCloudBounds(double minimumZ, double maximumZ) {
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
