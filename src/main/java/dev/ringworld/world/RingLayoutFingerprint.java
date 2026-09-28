package dev.ringworld.world;

/**
 * Stable identity for every immutable value that changes canonical terrain or
 * the physical cylinder. Both sides of the settings handshake compute this
 * value independently.
 */
public final class RingLayoutFingerprint {
    private static final int FINGERPRINT_VERSION = 4;

    private RingLayoutFingerprint() { }

    public static long compute(RingWorldSettings settings) {
        return compute(settings.widthBlocks(), settings.circumferenceBlocks(),
                settings.generatorSeed(), settings.wallHeightBlocks(),
                settings.surfaceReferenceY(), settings.terrainNoiseMapping(),
                settings.wallStyle(), settings.generationSettings(), settings.formatVersion());
    }

    public static long compute(int widthBlocks, int circumferenceBlocks, long generatorSeed,
                               int wallHeightBlocks, int surfaceReferenceY, int formatVersion) {
        return compute(widthBlocks, circumferenceBlocks, generatorSeed, wallHeightBlocks,
                surfaceReferenceY, RingTerrainNoiseMapping.forSettingsFormat(formatVersion),
                RingWallStyle.LEGACY, formatVersion);
    }

    public static long compute(int widthBlocks, int circumferenceBlocks, long generatorSeed,
                               int wallHeightBlocks, int surfaceReferenceY,
                               int terrainNoiseMapping, int formatVersion) {
        return compute(widthBlocks, circumferenceBlocks, generatorSeed, wallHeightBlocks,
                surfaceReferenceY, terrainNoiseMapping, RingWallStyle.LEGACY, formatVersion);
    }

    public static long compute(int widthBlocks, int circumferenceBlocks, long generatorSeed,
                               int wallHeightBlocks, int surfaceReferenceY,
                               int terrainNoiseMapping, RingWallStyle wallStyle,
                               int formatVersion) {
        return compute(widthBlocks, circumferenceBlocks, generatorSeed, wallHeightBlocks,
                surfaceReferenceY, terrainNoiseMapping, wallStyle,
                RingWorldGenerationSettings.DEFAULT, formatVersion);
    }

    public static long compute(int widthBlocks, int circumferenceBlocks, long generatorSeed,
                               int wallHeightBlocks, int surfaceReferenceY,
                               int terrainNoiseMapping, RingWallStyle wallStyle,
                               RingWorldGenerationSettings generationSettings,
                               int formatVersion) {
        if (wallStyle == null) throw new IllegalArgumentException("wall style is required");
        if (generationSettings == null) {
            throw new IllegalArgumentException("generation settings are required");
        }
        long value = 0x9E3779B97F4A7C15L ^ generatorSeed;
        value = mix(value ^ FINGERPRINT_VERSION);
        value = mix(value ^ Integer.toUnsignedLong(widthBlocks));
        value = mix(value ^ (Integer.toUnsignedLong(circumferenceBlocks) << 1));
        value = mix(value ^ (Integer.toUnsignedLong(wallHeightBlocks) << 17));
        value = mix(value ^ (Integer.toUnsignedLong(surfaceReferenceY) << 33));
        value = mix(value ^ (Integer.toUnsignedLong(terrainNoiseMapping) << 49));
        value = mix(value ^ (Integer.toUnsignedLong(formatVersion) << 41));
        value = mix(value ^ (Integer.toUnsignedLong(wallStyle.thicknessBlocks()) << 9));
        value = mix(value ^ (Integer.toUnsignedLong(wallStyle.palette().id()) << 13));
        value = mix(value ^ (Integer.toUnsignedLong(wallStyle.pattern().id()) << 21));
        value = mix(value ^ (Integer.toUnsignedLong(wallStyle.decayPercent()) << 29));
        value = mix(value ^ (Integer.toUnsignedLong(wallStyle.formatVersion()) << 37));
        value = mix(value ^ (Integer.toUnsignedLong(
                generationSettings.atlasFidelity().id()) << 11));
        value = mix(value ^ (Integer.toUnsignedLong(generationSettings.layout().id()) << 19));
        value = mix(value ^ (generationSettings.continuousRiver() ? 0x52A1_7E2DL : 0L));
        value = mix(value ^ (generationSettings.moreStructures() ? 0x6D31_904BL : 0L));
        value = mix(value ^ (Integer.toUnsignedLong(
                generationSettings.formatVersion()) << 45));
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
