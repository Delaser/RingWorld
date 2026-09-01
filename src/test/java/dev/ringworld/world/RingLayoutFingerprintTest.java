package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RingLayoutFingerprintTest {
    @Test
    void bothHandshakeSidesDeriveTheSameIdentity() {
        RingWorldSettings settings = new RingWorldSettings(
                416, 2_048, 123L, 160, 64, RingWorldSettings.FORMAT_VERSION);

        assertEquals(settings.layoutFingerprint(), RingLayoutFingerprint.compute(
                settings.widthBlocks(), settings.circumferenceBlocks(),
                settings.generatorSeed(), settings.wallHeightBlocks(),
                settings.surfaceReferenceY(), settings.terrainNoiseMapping(),
                settings.wallStyle(), settings.formatVersion()));
    }

    @Test
    void everySynchronizedLayoutFieldChangesIdentity() {
        long baseline = RingLayoutFingerprint.compute(416, 2_048, 123L, 160, 64,
                RingTerrainNoiseMapping.ANNULAR, RingWorldSettings.FORMAT_VERSION);

        assertNotEquals(baseline, RingLayoutFingerprint.compute(432, 2_048, 123L, 160, 64,
                RingTerrainNoiseMapping.ANNULAR, 3));
        assertNotEquals(baseline, RingLayoutFingerprint.compute(416, 2_064, 123L, 160, 64,
                RingTerrainNoiseMapping.ANNULAR, 3));
        assertNotEquals(baseline, RingLayoutFingerprint.compute(416, 2_048, 124L, 160, 64,
                RingTerrainNoiseMapping.ANNULAR, 3));
        assertNotEquals(baseline, RingLayoutFingerprint.compute(416, 2_048, 123L, 176, 64,
                RingTerrainNoiseMapping.ANNULAR, 3));
        assertNotEquals(baseline, RingLayoutFingerprint.compute(416, 2_048, 123L, 160, 65,
                RingTerrainNoiseMapping.ANNULAR, 3));
        assertNotEquals(baseline, RingLayoutFingerprint.compute(416, 2_048, 123L, 160, 64,
                RingTerrainNoiseMapping.LEGACY_AXIAL, 3));
        assertNotEquals(baseline, RingLayoutFingerprint.compute(416, 2_048, 123L, 160, 64,
                RingTerrainNoiseMapping.ANNULAR_COMPLETE, 3));
        assertNotEquals(baseline, RingLayoutFingerprint.compute(416, 2_048, 123L, 160, 64,
                RingTerrainNoiseMapping.ANNULAR_COMPLETE_V2, 3));
        assertNotEquals(baseline, RingLayoutFingerprint.compute(416, 2_048, 123L, 160, 64,
                RingTerrainNoiseMapping.LEGACY_AXIAL, 2));
    }

    @Test
    void everyWallStyleFieldChangesWorldIdentity() {
        RingWallStyle baselineStyle = RingWallStyle.custom(
                5, RingWallStyle.Palette.WEATHERED, RingWallStyle.Pattern.CLUSTERED, 20);
        long baseline = RingLayoutFingerprint.compute(256, 16_384, 123L, 160, 64,
                RingTerrainNoiseMapping.CURRENT, baselineStyle, RingWorldSettings.FORMAT_VERSION);

        assertNotEquals(baseline, fingerprint(RingWallStyle.custom(
                6, baselineStyle.palette(), baselineStyle.pattern(), baselineStyle.decayPercent())));
        assertNotEquals(baseline, fingerprint(RingWallStyle.custom(
                5, RingWallStyle.Palette.ANCIENT, baselineStyle.pattern(), baselineStyle.decayPercent())));
        assertNotEquals(baseline, fingerprint(RingWallStyle.custom(
                5, baselineStyle.palette(), RingWallStyle.Pattern.MASONRY, baselineStyle.decayPercent())));
        assertNotEquals(baseline, fingerprint(RingWallStyle.custom(
                5, baselineStyle.palette(), baselineStyle.pattern(), 21)));
    }

    private static long fingerprint(RingWallStyle style) {
        return RingLayoutFingerprint.compute(256, 16_384, 123L, 160, 64,
                RingTerrainNoiseMapping.CURRENT, style, RingWorldSettings.FORMAT_VERSION);
    }
}
