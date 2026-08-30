package dev.ringworld.world;

/** Pure deterministic sampling for periodic rim materials and top-edge decay. */
public final class RingWallPattern {
    private static final long MATERIAL_SALT = 0x52494D5F4D41544CL;
    private static final long MATERIAL_FINE_SALT = 0x46494E455F4D4154L;
    private static final long MATERIAL_COARSE_SALT = 0x434F415253454D41L;
    private static final long ACCENT_SALT = 0x52494D5F41434354L;
    private static final long DECAY_SALT = 0x52494D5F44454341L;
    private static final long DECAY_DEPTH_SALT = 0x44454341595F4450L;

    private RingWallPattern() { }

    /** Returns a stable value in [0, 99] interpreted by the selected palette. */
    public static int materialRoll(RingWallStyle style, int x, int y, int depth,
                                   int circumference, long worldSeed) {
        requireCircumference(circumference);
        int canonicalX = Math.floorMod(x, circumference);
        int fine = materialNoise(worldSeed, MATERIAL_FINE_SALT, canonicalX, y, depth,
                style.palette().id());
        int coarse = materialNoise(worldSeed, MATERIAL_COARSE_SALT,
                Math.floorDiv(canonicalX, 7), Math.floorDiv(y, 5), Math.floorDiv(depth, 2),
                style.palette().id());

        return switch (style.pattern()) {
            case CLUSTERED -> blend(coarse, fine, 72);
            case MASONRY -> {
                int course = Math.floorDiv(y, 2);
                int brickWidth = 3 + materialNoise(worldSeed, MATERIAL_SALT,
                        course, depth, 0, style.palette().id()) % 4;
                int offset = materialNoise(worldSeed, MATERIAL_COARSE_SALT,
                        course, depth, 1, style.palette().id()) % brickWidth;
                int brick = Math.floorDiv(canonicalX + offset, brickWidth);
                int body = materialNoise(worldSeed, MATERIAL_SALT, brick, course, depth,
                        style.palette().id());
                yield blend(body, fine, 76);
            }
            case STRATA -> {
                int section = Math.floorDiv(canonicalX, 19);
                int wave = materialNoise(worldSeed, MATERIAL_COARSE_SALT,
                        section, depth, 2, style.palette().id()) % 9 - 4;
                int bandHeight = 3 + materialNoise(worldSeed, MATERIAL_SALT,
                        section, Math.floorDiv(y, 13), depth, style.palette().id()) % 7;
                int band = Math.floorDiv(y + wave, bandHeight);
                int body = materialNoise(worldSeed, MATERIAL_SALT,
                        Math.floorDiv(canonicalX, 11), band, depth, style.palette().id());
                yield blend(body, fine, 68);
            }
            case PANELS -> panelRoll(style, canonicalX, y, depth, worldSeed, fine);
            case GRADIENT -> {
                // Keep a broad bottom-to-top bias, but let irregular stone-scale noise
                // dominate. Unlike the old y%128 ramp this never visibly restarts.
                int vertical = clamp((y + 64) * 100 / 224);
                int grain = blend(coarse, fine, 46);
                yield clamp((vertical * 28 + grain * 72) / 100);
            }
            case HYBRID -> {
                int clustered = blend(coarse, fine, 66);
                int panel = panelRoll(style, canonicalX, y, depth, worldSeed, fine);
                int selector = materialNoise(worldSeed, MATERIAL_SALT,
                        Math.floorDiv(canonicalX, 23), Math.floorDiv(y, 17), depth,
                        style.palette().id());
                yield selector < 28 ? panel : blend(clustered, panel, 82);
            }
        };
    }

    /**
     * Returns a stable rare-accent decision at per-mille precision. This is
     * separate from the 100-value palette roll so genuinely sparse details do
     * not have to occupy a full one percent of a wall.
     */
    public static boolean rareAccent(RingWallStyle style, int x, int y, int depth,
                                     int circumference, long worldSeed,
                                     int frequencyPerThousand) {
        requireCircumference(circumference);
        if (style == null) throw new IllegalArgumentException("wall style is required");
        if (depth < 0) throw new IllegalArgumentException("wall depth must be non-negative");
        if (frequencyPerThousand < 0 || frequencyPerThousand > 1_000) {
            throw new IllegalArgumentException("accent frequency must be in [0, 1000]");
        }
        if (frequencyPerThousand == 0) return false;
        int canonicalX = Math.floorMod(x, circumference);
        long value = worldSeed ^ ACCENT_SALT
                ^ (long)canonicalX * 0x9E3779B97F4A7C15L
                ^ (long)y * 0xC2B2AE3D27D4EB4FL
                ^ (long)depth * 0x165667B19E3779F9L
                ^ (long)style.palette().id() << 41;
        return unsignedMod(mix(value), 1_000) < frequencyPerThousand;
    }

    /** Number of blocks removed downward from the wall top at this longitude. */
    public static int topCollapseDepth(RingWallStyle style, int x, int circumference,
                                       long worldSeed) {
        return topCollapseDepth(style, x, 0, circumference, worldSeed);
    }

    /**
     * Number of blocks removed downward from one column of the wall top.
     * Depth is measured inward from the outer face, allowing the broken edge
     * to vary across a thick rim instead of cutting identical slots through it.
     */
    public static int topCollapseDepth(RingWallStyle style, int x, int depth,
                                       int circumference, long worldSeed) {
        requireCircumference(circumference);
        if (depth < 0) throw new IllegalArgumentException("wall depth must be non-negative");
        if (style.decayPercent() == 0) return 0;
        int canonicalX = Math.floorMod(x, circumference);
        // Collapse follows correlated structural weakness, not independent block
        // rolls: broad failed bays contain smaller fractures and chipped edges.
        double coarse = smoothNoise2d(worldSeed ^ 0x434F415253455F44L,
                canonicalX, depth, 32, 8, circumference);
        double medium = smoothNoise2d(worldSeed ^ 0x4D454449554D5F44L,
                canonicalX, depth, 8, 3, circumference);
        double fracture = smoothNoise2d(worldSeed, canonicalX, depth,
                4, 2, circumference);
        double exposedFace = style.thicknessBlocks() == 1 ? 1.0
                : Math.abs(depth * 2.0 / (style.thicknessBlocks() - 1) - 1.0);
        double noise = Math.min(1.0,
                coarse * 0.48 + medium * 0.34 + fracture * 0.18
                        + exposedFace * 0.07);
        double threshold = 1.0 - style.decayPercent() / 100.0 * 0.88;
        if (noise <= threshold) return 0;
        int maximum = Math.max(1, 2 + style.decayPercent() * 30 / 100);
        double severity = (noise - threshold) / Math.max(0.01, 1.0 - threshold);
        double depthNoise = smoothNoise2d(worldSeed ^ DECAY_DEPTH_SALT,
                canonicalX, depth, 16, 4, circumference);
        int collapse = 1 + (int)Math.floor(Math.pow(severity, 0.72)
                * (0.42 + depthNoise * 0.58) * maximum);
        return Math.min(maximum, collapse);
    }

    public static boolean blockPresent(RingWallStyle style, int x, int y,
                                       int wallTopExclusive, int circumference,
                                       long worldSeed) {
        return blockPresent(style, x, y, 0, wallTopExclusive, circumference, worldSeed);
    }

    public static boolean blockPresent(RingWallStyle style, int x, int y, int depth,
                                       int wallTopExclusive, int circumference,
                                       long worldSeed) {
        return y < wallTopExclusive
                - topCollapseDepth(style, x, depth, circumference, worldSeed);
    }

    private static double unitNoise2d(long seed, int x, int depth) {
        long value = mix(seed ^ DECAY_SALT
                ^ (long)x * 0x9E3779B97F4A7C15L
                ^ (long)depth * 0xD1B54A32D192ED03L);
        return (value >>> 11) * 0x1.0p-53;
    }

    private static double smoothNoise2d(long seed, int canonicalX, int depth,
                                        int xScale, int depthScale, int circumference) {
        int xCells = Math.max(1, circumference / xScale);
        int x0 = Math.floorDiv(canonicalX, xScale);
        int x1 = Math.floorMod(x0 + 1, xCells);
        x0 = Math.floorMod(x0, xCells);
        int d0 = Math.floorDiv(depth, depthScale);
        int d1 = d0 + 1;
        double tx = smoothStep(Math.floorMod(canonicalX, xScale) / (double)xScale);
        double td = smoothStep(Math.floorMod(depth, depthScale) / (double)depthScale);
        double low = lerp(unitNoise2d(seed, x0, d0), unitNoise2d(seed, x1, d0), tx);
        double high = lerp(unitNoise2d(seed, x0, d1), unitNoise2d(seed, x1, d1), tx);
        return lerp(low, high, td);
    }

    private static double smoothStep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static int panelRoll(RingWallStyle style, int canonicalX, int y, int depth,
                                 long worldSeed, int fine) {
        int region = Math.floorDiv(canonicalX, 67);
        int panelWidth = 11 + materialNoise(worldSeed, MATERIAL_COARSE_SALT,
                region, depth, 3, style.palette().id()) % 13;
        int xOffset = materialNoise(worldSeed, MATERIAL_SALT,
                region, depth, 4, style.palette().id()) % panelWidth;
        int panelX = Math.floorMod(canonicalX + xOffset, panelWidth);
        int panel = Math.floorDiv(canonicalX + xOffset, panelWidth);
        int courseHeight = 8 + materialNoise(worldSeed, MATERIAL_COARSE_SALT,
                panel, depth, 5, style.palette().id()) % 11;
        int yOffset = materialNoise(worldSeed, MATERIAL_SALT,
                panel, depth, 6, style.palette().id()) % courseHeight;
        boolean rib = panelX == 0 || (panelX == panelWidth - 1
                && materialNoise(worldSeed, MATERIAL_FINE_SALT,
                panel, Math.floorDiv(y, 5), depth, style.palette().id()) < 74)
                || Math.floorMod(y + yOffset, courseHeight) == 0;
        if (rib) {
            int wear = materialNoise(worldSeed, MATERIAL_FINE_SALT,
                    canonicalX, y, depth + 17, style.palette().id());
            return clamp(76 + wear / 4);
        }
        int body = materialNoise(worldSeed, MATERIAL_SALT,
                panel, Math.floorDiv(y + yOffset, courseHeight), depth,
                style.palette().id());
        return blend(body, fine, 70);
    }

    private static int materialNoise(long seed, long salt, int x, int y, int depth,
                                     int palette) {
        long value = seed ^ salt
                ^ (long)x * 0x9E3779B97F4A7C15L
                ^ (long)y * 0xC2B2AE3D27D4EB4FL
                ^ (long)depth * 0x165667B19E3779F9L
                ^ (long)palette << 41;
        return unsignedMod(mix(value), 100);
    }

    /** Coarse weight is expressed as a percentage; fine noise breaks its edges. */
    private static int blend(int coarse, int fine, int coarseWeight) {
        return clamp((coarse * coarseWeight + fine * (100 - coarseWeight)) / 100);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(99, value));
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
