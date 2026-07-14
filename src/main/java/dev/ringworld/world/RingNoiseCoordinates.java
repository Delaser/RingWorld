package dev.ringworld.world;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Shared immutable cylindrical coordinate lookup used by every chunk sampler. */
public final class RingNoiseCoordinates {
    private static final int MAX_PRECOMPUTED_CIRCUMFERENCE = 1_048_576;
    private static final ConcurrentMap<RingGeometry, RingNoiseCoordinates> CACHE = new ConcurrentHashMap<>();

    private final int circumference;
    private final double radius;
    private final int[] ringX;
    private final int[] ringZOffset;

    public static RingNoiseCoordinates forGeometry(RingGeometry geometry) {
        return CACHE.computeIfAbsent(geometry, RingNoiseCoordinates::new);
    }

    private RingNoiseCoordinates(RingGeometry geometry) {
        circumference = geometry.circumferenceBlocks();
        radius = geometry.radius();
        if (circumference <= MAX_PRECOMPUTED_CIRCUMFERENCE) {
            ringX = new int[circumference];
            ringZOffset = new int[circumference];
            for (int x = 0; x < circumference; x++) {
                double angle = Math.PI * 2.0 * x / circumference;
                ringX[x] = rounded(radius * Math.sin(angle));
                ringZOffset[x] = rounded(radius * Math.cos(angle));
            }
        } else {
            ringX = null;
            ringZOffset = null;
        }
    }

    public int ringX(int sourceX) {
        int canonicalX = Math.floorMod(sourceX, circumference);
        if (ringX != null) return ringX[canonicalX];
        return rounded(radius * Math.sin(Math.PI * 2.0 * canonicalX / circumference));
    }

    public int ringZ(int sourceX, int sourceZ) {
        int canonicalX = Math.floorMod(sourceX, circumference);
        int offset = ringZOffset != null
                ? ringZOffset[canonicalX]
                : rounded(radius * Math.cos(Math.PI * 2.0 * canonicalX / circumference));
        return sourceZ + offset;
    }

    private static int rounded(double value) {
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("Ring dimensions exceed vanilla noise coordinate range");
        }
        return (int) Math.round(value);
    }
}
