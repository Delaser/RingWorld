package dev.ringworld.world;

/** Pure deterministic sampling for periodic rim materials and top-edge decay. */
public final class RingWallPattern {
    private static final long MATERIAL_SALT = 0x52494D5F4D41544CL;
    private static final long DECAY_SALT = 0x52494D5F44454341L;

    private RingWallPattern() { }

    /** Returns a stable value in [0, 99] interpreted by the selected palette. */
    public static int materialRoll(RingWallStyle style, int x, int y, int depth,
                                   int circumference, long worldSeed) {
        requireCircumference(circumference);
        int canonicalX = Math.floorMod(x, circumference);
        int sampleX = canonicalX;
        int sampleY = y;
        int sampleDepth = depth;
        switch (style.pattern()) {
            case CLUSTERED -> {
                sampleX = canonicalX / 3;
                sampleY = Math.floorDiv(y, 3);
            }
            case MASONRY -> {
                sampleX = Math.floorDiv(canonicalX + (Math.floorDiv(y, 2) & 1) * 2, 4);
                sampleY = Math.floorDiv(y, 2);
            }
            case STRATA -> {
                sampleX = canonicalX / 12;
                sampleY = Math.floorDiv(y, 7);
            }
            case PANELS -> {
                int panelX = Math.floorMod(canonicalX, 16);
                if (panelX == 0 || panelX == 15 || Math.floorMod(y, 12) == 0) return 99;
                sampleX = canonicalX / 16;
                sampleY = Math.floorDiv(y, 12);
            }
            case GRADIENT -> {
                int vertical = Math.floorMod(y, 128) * 100 / 128;
                int variation = unsignedMod(mix(worldSeed ^ MATERIAL_SALT
                        ^ canonicalX * 0x9E3779B97F4A7C15L), 21) - 10;
                return Math.max(0, Math.min(99, vertical + variation));
            }
            case HYBRID -> {
                sampleX = canonicalX / 8;
                sampleY = Math.floorDiv(y, 5);
                sampleDepth = depth / 2;
            }
        }
        long value = worldSeed ^ MATERIAL_SALT
                ^ (long)sampleX * 0x9E3779B97F4A7C15L
                ^ (long)sampleY * 0xC2B2AE3D27D4EB4FL
                ^ (long)sampleDepth * 0x165667B19E3779F9L
                ^ (long)style.palette().id() << 41;
        return unsignedMod(mix(value), 100);
    }

    /** Number of blocks removed downward from the wall top at this longitude. */
    public static int topCollapseDepth(RingWallStyle style, int x, int circumference,
                                       long worldSeed) {
        requireCircumference(circumference);
        if (style.decayPercent() == 0) return 0;
        int controlPoints = Math.max(4, circumference / 64);
        double position = Math.floorMod(x, circumference) * (double)controlPoints / circumference;
        int left = (int)Math.floor(position);
        double fraction = position - left;
        double a = unitNoise(worldSeed, Math.floorMod(left, controlPoints));
        double b = unitNoise(worldSeed, Math.floorMod(left + 1, controlPoints));
        double smooth = fraction * fraction * (3.0 - 2.0 * fraction);
        double noise = a + (b - a) * smooth;
        double threshold = 1.0 - style.decayPercent() / 100.0;
        if (noise <= threshold) return 0;
        double strength = (noise - threshold) / Math.max(0.01, 1.0 - threshold);
        int maximum = Math.max(1, 2 + style.decayPercent() * 30 / 100);
        return Math.min(maximum, (int)Math.round(strength * strength * maximum));
    }

    public static boolean blockPresent(RingWallStyle style, int x, int y,
                                       int wallTopExclusive, int circumference,
                                       long worldSeed) {
        return y < wallTopExclusive - topCollapseDepth(style, x, circumference, worldSeed);
    }

    private static double unitNoise(long seed, int controlPoint) {
        long value = mix(seed ^ DECAY_SALT ^ (long)controlPoint * 0x9E3779B97F4A7C15L);
        return (value >>> 11) * 0x1.0p-53;
    }

    private static int unsignedMod(long value, int modulus) {
        return (int)Long.remainderUnsigned(value, modulus);
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static void requireCircumference(int circumference) {
        if (circumference <= 0) throw new IllegalArgumentException("circumference must be positive");
    }
}
