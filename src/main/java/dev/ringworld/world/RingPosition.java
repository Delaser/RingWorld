package dev.ringworld.world;

/**
 * A client presentation position on the periodic circumference. The server
 * stores only {@code canonicalX}; {@code chartIndex} is transient client-side
 * rendering state used to avoid a camera discontinuity at the seam.
 */
public record RingPosition(double canonicalX, long chartIndex) {
    public RingPosition {
        if (!Double.isFinite(canonicalX)) throw new IllegalArgumentException("canonicalX must be finite");
    }

    public static RingPosition fromPresentationX(double presentationX, RingGeometry geometry) {
        if (!Double.isFinite(presentationX)) throw new IllegalArgumentException("presentationX must be finite");
        long chartIndex = (long) Math.floor(presentationX / geometry.circumferenceBlocks());
        return new RingPosition(geometry.wrapX(presentationX), chartIndex);
    }

    public double presentationX(RingGeometry geometry) {
        return canonicalX + chartIndex * (double) geometry.circumferenceBlocks();
    }
}
