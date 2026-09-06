package dev.ringworld.world;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingWorldGenerationSettingsTest {
    @Test
    void codecRoundTripsEveryChoice() {
        RingWorldGenerationSettings original = new RingWorldGenerationSettings(
                RingAtlasFidelity.VERY_HIGH, RingWorldLayout.ARCHIPELAGO,
                true, true, RingWorldGenerationSettings.FORMAT_VERSION);
        var json = RingWorldGenerationSettings.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        assertEquals(original, RingWorldGenerationSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void fidelityProfilesAreOrderedAndResolvableFromAtlasStep() {
        assertTrue(RingAtlasFidelity.PERFORMANCE.sampleStepBlocks()
                > RingAtlasFidelity.VERY_HIGH.sampleStepBlocks());
        for (RingAtlasFidelity value : RingAtlasFidelity.values()) {
            assertSame(value, RingAtlasFidelity.forSampleStep(value.sampleStepBlocks()));
        }
    }
}
