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

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
