package dev.ringworld.world;

import java.util.function.Predicate;

/**
 * Bounded, loader-neutral candidate order for an opt-in ocean monument.
 * Vanilla biome and registry validation is supplied by a platform adapter.
 */
public final class RingMonumentPlacement {
    public static final int MAX_CANDIDATES = 512;
    /** Vanilla validates surrounding biomes in this block radius. */
    public static final int SURROUNDING_BIOME_RADIUS_BLOCKS = 29;
    /** Conservative complete-monument and terrain envelope, including the 29-block start offset. */
    public static final int FOOTPRINT_MARGIN_BLOCKS = 64;
    private static final long SEED_SALT = 0x4D4F4E554D454E54L;

    private RingMonumentPlacement() { }

    /** Searches a bounded seed-derived walk of canonical, conservatively in-band chunks. */
    public static SearchResult findCandidate(long worldSeed, RingGeometry geometry, Predicate<Candidate> validator) {
        Bounds bounds = Bounds.forGeometry(geometry);
        int width = Math.max(0, bounds.maxChunkX - bounds.minChunkX + 1);
        int height = Math.max(0, bounds.maxChunkZ - bounds.minChunkZ + 1);
        long total = (long) width * height;
        int checked = (int) Math.min(total, MAX_CANDIDATES);
        if (checked == 0) return new SearchResult(null, 0, false);
        long start = Math.floorMod(mix64(worldSeed ^ SEED_SALT), total);
        long step = coprimeStep(mix64(worldSeed + SEED_SALT), total);
        for (int index = 0; index < checked; index++) {
            long ordinal = Math.floorMod(start + step * index, total);
            Candidate candidate = new Candidate(bounds.minChunkX + (int)(ordinal % width),
                    bounds.minChunkZ + (int)(ordinal / width));
            if (validator.test(candidate)) return new SearchResult(candidate, index + 1, checked < total);
        }
        return new SearchResult(null, checked, checked < total);
    }

    public static boolean isConservativelyInBounds(Candidate candidate, RingGeometry geometry) {
        Bounds bounds = Bounds.forGeometry(geometry);
        return candidate.chunkX() >= bounds.minChunkX() && candidate.chunkX() <= bounds.maxChunkX()
                && candidate.chunkZ() >= bounds.minChunkZ() && candidate.chunkZ() <= bounds.maxChunkZ();
    }

    private static long coprimeStep(long mixed, long modulo) {
        if (modulo <= 1) return 1;
        long step = Math.floorMod(mixed, modulo - 1) + 1;
        while (gcd(step, modulo) != 1) step = step == modulo - 1 ? 1 : step + 1;
        return step;
    }

    private static long gcd(long left, long right) {
        while (right != 0) {
            long next = left % right;
            left = right;
            right = next;
        }
        return left;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private record Bounds(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        static Bounds forGeometry(RingGeometry geometry) {
            int minChunkX = divideCeil(FOOTPRINT_MARGIN_BLOCKS, 16);
            int maxChunkX = Math.floorDiv(geometry.circumferenceBlocks() - 1
                    - FOOTPRINT_MARGIN_BLOCKS - 15, 16);
            int minChunkZ = divideCeil(geometry.minWidthZ() + FOOTPRINT_MARGIN_BLOCKS, 16);
            int maxChunkZ = Math.floorDiv(geometry.maxWidthZ() - FOOTPRINT_MARGIN_BLOCKS - 15, 16);
            return new Bounds(minChunkX, maxChunkX, minChunkZ, maxChunkZ);
        }

        private static int divideCeil(int value, int divisor) {
            return -Math.floorDiv(-value, divisor);
        }
    }

    public record Candidate(int chunkX, int chunkZ) { }
    public record SearchResult(Candidate candidate, int checkedCandidates, boolean searchBoundReached) { }
}
