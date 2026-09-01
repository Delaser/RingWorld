package dev.ringworld.client;

import dev.ringworld.world.RingAtlasLightProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingAtlasLightTuningTest {
    @AfterEach
    void restoreGammaDefault() {
        RingAtlasLightTuning.useGamma(
                RingAtlasLightProfile.DEFAULT_GAMMA_FALLOFF,
                RingAtlasLightProfile.DEFAULT_GAMMA_PEAK);
    }

    @Test
    void localTuningCanSelectGammaAndResetToMidpoint() {
        assertEquals(RingAtlasLightProfile.GAMMA, RingAtlasLightTuning.profile());
        assertEquals(RingAtlasLightProfile.gamma(3.5F, 1.75F),
                RingAtlasLightTuning.useGamma(3.5F, 1.75F));
        assertEquals(RingAtlasLightProfile.MIDPOINT, RingAtlasLightTuning.reset());
        assertEquals(RingAtlasLightProfile.MIDPOINT, RingAtlasLightTuning.profile());
    }
}
