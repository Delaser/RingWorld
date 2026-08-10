package dev.ringworld.world;

/** Versioned horizontal coordinate embedding used by Overworld terrain noise. */
public final class RingTerrainNoiseMapping {
    /** Historical mapping shipped by the public alphas; retained for existing worlds only. */
    public static final int LEGACY_AXIAL = 1;
    /** Orthogonal annular embedding used by newly created worlds. */
    public static final int ANNULAR = 2;
    public static final int CURRENT = ANNULAR;

    /** Conservative allowance for structure and column queries beside the finite band. */
    public static final int QUERY_MARGIN_BLOCKS = 64;
    /** Avoid the severe compression that occurs as an annular radius approaches zero. */
    public static final double MINIMUM_NOISE_RADIUS_BLOCKS = 32.0;

    private RingTerrainNoiseMapping() { }

    public static int forSettingsFormat(int settingsFormat) {
        return settingsFormat >= 3 ? CURRENT : LEGACY_AXIAL;
    }

    public static int requireSupported(int mapping) {
        if (mapping != LEGACY_AXIAL && mapping != ANNULAR) {
            throw new IllegalArgumentException("unsupported RingWorld terrain-noise mapping " + mapping);
        }
        return mapping;
    }

    /** New-world-only safety rule. Persisted legacy worlds are not revalidated through it. */
    public static void requireSafeNewWorldGeometry(RingGeometry geometry) {
        double minimumRadius = minimumSampledRadius(geometry);
        if (minimumRadius < MINIMUM_NOISE_RADIUS_BLOCKS) {
            throw new IllegalArgumentException(
                    "circumference is too small for the selected width's periodic terrain-noise band; "
                            + "minimum sampled radius="
                            + String.format(java.util.Locale.ROOT, "%.2f", minimumRadius)
                            + ", required="
                            + String.format(java.util.Locale.ROOT, "%.2f", MINIMUM_NOISE_RADIUS_BLOCKS));
        }
    }

    public static double minimumSampledRadius(RingGeometry geometry) {
        return geometry.radius() + geometry.minWidthZ() - QUERY_MARGIN_BLOCKS;
    }

    static ContinuousCoordinate continuousAnnular(
            RingGeometry geometry, double sourceX, double sourceZ) {
        double canonicalX = geometry.wrapX(sourceX);
        double angle = Math.PI * 2.0 * canonicalX / geometry.circumferenceBlocks();
        double radius = geometry.radius() + sourceZ;
        return new ContinuousCoordinate(radius * Math.sin(angle), radius * Math.cos(angle));
    }

    record ContinuousCoordinate(double x, double z) { }
}
