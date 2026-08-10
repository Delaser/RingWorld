package dev.ringworld.world;

import java.util.Objects;

/** Coordinate normalization for client interaction packets at the X seam. */
public final class RingInteractionCoordinates {
    private RingInteractionCoordinates() { }

    /**
     * Canonicalizes a clicked block and translates the hit coordinate by the
     * same whole-chart offset. The hit must remain on the clicked face: wrapping
     * it independently can move an east-face hit at {@code X=C} to {@code X=0}
     * while its clicked block remains at {@code X=C-1}.
     */
    public static CanonicalBlockHit canonicalizeBlockHit(RingGeometry geometry,
                                                          int clickedBlockX,
                                                          double hitX) {
        Objects.requireNonNull(geometry, "geometry");
        int canonicalBlockX = geometry.wrapBlockX(clickedBlockX);
        double chartOffset = (double) canonicalBlockX - clickedBlockX;
        return new CanonicalBlockHit(canonicalBlockX, hitX + chartOffset);
    }

    public record CanonicalBlockHit(int blockX, double hitX) { }
}
