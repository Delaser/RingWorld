package dev.ringworld.world;

/** Deterministic, periodic second candidate grid used by the opt-in structure-density setting. */
public final class RingStructureDensity {
    private RingStructureDensity() { }

    public static boolean isAdditionalCandidate(long seed, RingGeometry geometry, int structureSalt,
                                                 int vanillaSpacing, int separation,
                                                 int chunkX, int chunkZ) {
        int circumferenceChunks = geometry.circumferenceChunks();
        int canonicalX = Math.floorMod(chunkX, circumferenceChunks);
        int spacing = Math.max(separation + 1, (int)Math.round(vanillaSpacing * 0.70));
        int cellsX = Math.max(1, (int)Math.round((double)circumferenceChunks / spacing));
        int cellX = Math.min(cellsX - 1, (int)((long)canonicalX * cellsX / circumferenceChunks));
        int startX = (int)((long)cellX * circumferenceChunks / cellsX);
        int endX = (int)((long)(cellX + 1) * circumferenceChunks / cellsX);
        int spanX = Math.max(1, endX - startX);

        int cellZ = Math.floorDiv(chunkZ, spacing);
        long hash = mix(seed ^ Integer.toUnsignedLong(structureSalt) * 0x9E3779B97F4A7C15L
                ^ (long)cellX * 0xD1B54A32D192ED03L ^ (long)cellZ * 0x94D049BB133111EBL);
        int margin = Math.min(separation, Math.max(0, Math.min(spanX, spacing) / 3));
        int xRange = Math.max(1, spanX - margin);
        int zRange = Math.max(1, spacing - margin);
        int candidateX = Math.floorMod(startX + margin / 2
                + (int)Math.floorMod(hash, xRange), circumferenceChunks);
        int candidateZ = cellZ * spacing + margin / 2
                + (int)Math.floorMod(hash >>> 32, zRange);

        int rimMargin = Math.min(3, Math.max(0, geometry.widthChunks() / 8));
        int minZ = Math.floorDiv(geometry.minWidthZ(), 16) + rimMargin;
        int maxZ = Math.floorDiv(geometry.maxWidthZ(), 16) - rimMargin;
        return canonicalX == candidateX && chunkZ == candidateZ
                && chunkZ >= minZ && chunkZ <= maxZ;
    }

    /** Periodic X and finite Z have independent locate extents. */
    public static SearchBounds searchBounds(RingGeometry geometry, int originChunkX, int originChunkZ,
                                            int searchRadius, int spacing) {
        if (searchRadius < 0 || spacing <= 0) throw new IllegalArgumentException("invalid locate range");
        long radius = ((long) searchRadius + 1L) * spacing;
        int circumference = geometry.circumferenceChunks();
        long periodicRadius = Math.min(circumference / 2L, radius);
        int count = (int) Math.min(circumference, 2L * periodicRadius + 1L);
        int start = (int) Math.floorMod((long) originChunkX - periodicRadius, circumference);
        int minZ = (int) Math.max(geometry.minChunkZ(), Math.min(Integer.MAX_VALUE, (long) originChunkZ - radius));
        int maxZ = (int) Math.min(geometry.maxChunkZ(), Math.max(Integer.MIN_VALUE, (long) originChunkZ + radius));
        return new SearchBounds(start, count, circumference, minZ, maxZ);
    }

    public record SearchBounds(int startX, int countX, int circumferenceChunks, int minZ, int maxZ) {
        public int canonicalX(int offset) {
            if (offset < 0 || offset >= countX) throw new IndexOutOfBoundsException(offset);
            return (int) (((long) startX + offset) % circumferenceChunks);
        }
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
