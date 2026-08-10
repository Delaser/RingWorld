package dev.ringworld.world;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Shared immutable cylindrical coordinate lookup used by every chunk sampler. */
public final class RingNoiseCoordinates {
    private static final int MAX_PRECOMPUTED_CIRCUMFERENCE = 1_048_576;
    private static final ConcurrentMap<CacheKey, RingNoiseCoordinates> CACHE = new ConcurrentHashMap<>();

    private final int circumference;
    private final double radius;
    private final int mappingVersion;
    private final int[] legacyRingX;
    private final int[] legacyRingZOffset;
    private final double[] sine;
    private final double[] cosine;

    public static RingNoiseCoordinates forGeometry(RingGeometry geometry) {
        return forGeometry(geometry, RingTerrainNoiseMapping.CURRENT);
    }

    public static RingNoiseCoordinates forGeometry(RingGeometry geometry, int mappingVersion) {
        int supported = RingTerrainNoiseMapping.requireSupported(mappingVersion);
        return CACHE.computeIfAbsent(new CacheKey(geometry, supported),
                key -> new RingNoiseCoordinates(key.geometry(), key.mappingVersion()));
    }

    /** Drops per-layout lookup arrays when the owning Overworld unloads. */
    public static void clearCache() {
        CACHE.clear();
    }

    private RingNoiseCoordinates(RingGeometry geometry, int mappingVersion) {
        circumference = geometry.circumferenceBlocks();
        radius = geometry.radius();
        this.mappingVersion = mappingVersion;
        if (circumference <= MAX_PRECOMPUTED_CIRCUMFERENCE) {
            legacyRingX = mappingVersion == RingTerrainNoiseMapping.LEGACY_AXIAL
                    ? new int[circumference] : null;
            legacyRingZOffset = mappingVersion == RingTerrainNoiseMapping.LEGACY_AXIAL
                    ? new int[circumference] : null;
            sine = mappingVersion == RingTerrainNoiseMapping.ANNULAR
                    ? new double[circumference] : null;
            cosine = mappingVersion == RingTerrainNoiseMapping.ANNULAR
                    ? new double[circumference] : null;
            for (int x = 0; x < circumference; x++) {
                double angle = Math.PI * 2.0 * x / circumference;
                if (mappingVersion == RingTerrainNoiseMapping.LEGACY_AXIAL) {
                    legacyRingX[x] = rounded(radius * Math.sin(angle));
                    legacyRingZOffset[x] = rounded(radius * Math.cos(angle));
                } else {
                    sine[x] = Math.sin(angle);
                    cosine[x] = Math.cos(angle);
                }
            }
        } else {
            legacyRingX = null;
            legacyRingZOffset = null;
            sine = null;
            cosine = null;
        }
    }

    public int mappingVersion() {
        return mappingVersion;
    }

    /** Horizontal noise X. The annular mapping deliberately depends on intrinsic Z. */
    public int noiseX(int sourceX, int sourceZ) {
        int canonicalX = Math.floorMod(sourceX, circumference);
        if (mappingVersion == RingTerrainNoiseMapping.LEGACY_AXIAL) {
            if (legacyRingX != null) return legacyRingX[canonicalX];
            return rounded(radius * Math.sin(angle(canonicalX)));
        }
        double sin = sine != null ? sine[canonicalX] : Math.sin(angle(canonicalX));
        return rounded((radius + sourceZ) * sin);
    }

    /** Horizontal noise Z. */
    public int noiseZ(int sourceX, int sourceZ) {
        int canonicalX = Math.floorMod(sourceX, circumference);
        if (mappingVersion == RingTerrainNoiseMapping.LEGACY_AXIAL) {
            int offset = legacyRingZOffset != null
                    ? legacyRingZOffset[canonicalX]
                    : rounded(radius * Math.cos(angle(canonicalX)));
            // Preserve the public-alpha mapping bit-for-bit for existing worlds.
            return sourceZ + offset;
        }
        double cos = cosine != null ? cosine[canonicalX] : Math.cos(angle(canonicalX));
        return rounded((radius + sourceZ) * cos);
    }

    /** Legacy source-compatible centreline helper retained for focused tests. */
    public int ringX(int sourceX) {
        return noiseX(sourceX, 0);
    }

    /** Legacy source-compatible helper retained while callers move to the joint transform. */
    public int ringZ(int sourceX, int sourceZ) {
        return noiseZ(sourceX, sourceZ);
    }

    private double angle(int canonicalX) {
        return Math.PI * 2.0 * canonicalX / circumference;
    }

    private static int rounded(double value) {
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("Ring dimensions exceed vanilla noise coordinate range");
        }
        return (int) Math.round(value);
    }

    private record CacheKey(RingGeometry geometry, int mappingVersion) { }
}
