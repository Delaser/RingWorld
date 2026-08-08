package dev.ringworld.world;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Immutable atlas content selected for one asynchronous complete-ring build.
 *
 * <p>The background texture job and the render-thread terrain-height mesh
 * must consume this same point-in-time copy. A live client atlas can advance
 * while pixels are being prepared, so using it again for the mesh would pair
 * colour from one surface revision with relief from another. Capture uses an
 * explicit no-detail sentinel; the texture worker resolves the O(cells)
 * fingerprint only when this snapshot is complete.</p>
 */
public record RingSurfaceBuildSnapshot(
        RingTerrainAtlas atlas,
        int renderRevision,
        long heightFingerprint) {
    /** Partial builds never own or compute a detailed terrain-mesh hash. */
    public static final long NO_DETAILED_HEIGHT_FINGERPRINT = Long.MIN_VALUE;

    /** Captures identity/content without scanning atlas heights on the caller. */
    public RingSurfaceBuildSnapshot(RingTerrainAtlas atlas, int renderRevision) {
        this(atlas, renderRevision, NO_DETAILED_HEIGHT_FINGERPRINT);
    }

    public RingSurfaceBuildSnapshot {
        Objects.requireNonNull(atlas, "atlas");
        if (renderRevision < 0) {
            throw new IllegalArgumentException("render revision must be non-negative");
        }
        if (!atlas.isComplete() && heightFingerprint != NO_DETAILED_HEIGHT_FINGERPRINT) {
            throw new IllegalArgumentException(
                    "partial atlas snapshots cannot own a detailed height fingerprint");
        }
    }

    /**
     * Resolves the detailed-mesh fingerprint for complete content only.
     * Call this from the asynchronous texture worker, never the render thread.
     */
    public RingSurfaceBuildSnapshot resolveDetailedHeightFingerprint() {
        return resolveDetailedHeightFingerprint(atlas::surfaceHeightFingerprint);
    }

    /** Pure seam proving that partial builds never invoke the O(cells) scan. */
    RingSurfaceBuildSnapshot resolveDetailedHeightFingerprint(LongSupplier fingerprintSupplier) {
        Objects.requireNonNull(fingerprintSupplier, "fingerprintSupplier");
        if (!atlas.isComplete() || heightFingerprint != NO_DETAILED_HEIGHT_FINGERPRINT) return this;
        return new RingSurfaceBuildSnapshot(
                atlas, renderRevision, fingerprintSupplier.getAsLong());
    }

    /** True only while this immutable content still represents the live client state. */
    public boolean matches(RingGeometry geometry, long worldHash, int revision) {
        return renderRevision == revision
                && atlas.worldHash() == worldHash
                && atlas.geometry().equals(geometry);
    }
}
