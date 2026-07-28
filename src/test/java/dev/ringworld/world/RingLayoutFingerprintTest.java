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
                settings.surfaceReferenceY(), settings.formatVersion()));
    }

    @Test
    void everySynchronizedLayoutFieldChangesIdentity() {
        long baseline = RingLayoutFingerprint.compute(416, 2_048, 123L, 160, 64, 2);

        assertNotEquals(baseline, RingLayoutFingerprint.compute(432, 2_048, 123L, 160, 64, 2));
        assertNotEquals(baseline, RingLayoutFingerprint.compute(416, 2_064, 123L, 160, 64, 2));
        assertNotEquals(baseline, RingLayoutFingerprint.compute(416, 2_048, 124L, 160, 64, 2));
        assertNotEquals(baseline, RingLayoutFingerprint.compute(416, 2_048, 123L, 176, 64, 2));
        assertNotEquals(baseline, RingLayoutFingerprint.compute(416, 2_048, 123L, 160, 65, 2));
        assertNotEquals(baseline, RingLayoutFingerprint.compute(416, 2_048, 123L, 160, 64, 1));
    }
}
