package dev.ringworld.world;

/**
 * Stable identity for every immutable value that changes canonical terrain or
 * the physical cylinder. Both sides of the settings handshake compute this
 * value independently.
 */
public final class RingLayoutFingerprint {
    private static final int FINGERPRINT_VERSION = 2;

    private RingLayoutFingerprint() { }

    public static long compute(RingWorldSettings settings) {
        return compute(settings.widthBlocks(), settings.circumferenceBlocks(),
                settings.generatorSeed(), settings.wallHeightBlocks(),
                settings.surfaceReferenceY(), settings.terrainNoiseMapping(),
                settings.formatVersion());
    }

    public static long compute(int widthBlocks, int circumferenceBlocks, long generatorSeed,
                               int wallHeightBlocks, int surfaceReferenceY, int formatVersion) {
        return compute(widthBlocks, circumferenceBlocks, generatorSeed, wallHeightBlocks,
                surfaceReferenceY, RingTerrainNoiseMapping.forSettingsFormat(formatVersion),
                formatVersion);
    }

    public static long compute(int widthBlocks, int circumferenceBlocks, long generatorSeed,
                               int wallHeightBlocks, int surfaceReferenceY,
                               int terrainNoiseMapping, int formatVersion) {
        long value = 0x9E3779B97F4A7C15L ^ generatorSeed;
        value = mix(value ^ FINGERPRINT_VERSION);
        value = mix(value ^ Integer.toUnsignedLong(widthBlocks));
        value = mix(value ^ (Integer.toUnsignedLong(circumferenceBlocks) << 1));
        value = mix(value ^ (Integer.toUnsignedLong(wallHeightBlocks) << 17));
        value = mix(value ^ (Integer.toUnsignedLong(surfaceReferenceY) << 33));
        value = mix(value ^ (Integer.toUnsignedLong(terrainNoiseMapping) << 49));
        value = mix(value ^ (Integer.toUnsignedLong(formatVersion) << 41));
        value = mix(value ^ (Integer.toUnsignedLong(RingGenerationBoundary.RIM_THICKNESS) << 9));
        return mix(value ^ (Integer.toUnsignedLong(RingGenerationBoundary.RIM_STYLE_VERSION) << 25));
    }

    static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
