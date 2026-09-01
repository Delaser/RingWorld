package dev.ringworld.client;

import dev.ringworld.world.RingAtlasLightProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingAtlasLightCommandTest {
    @AfterEach
    void restoreGammaDefault() {
        RingAtlasLightTuning.useGamma(
                RingAtlasLightProfile.DEFAULT_GAMMA_FALLOFF,
                RingAtlasLightProfile.DEFAULT_GAMMA_PEAK);
    }

    @Test
    void showReportsWithoutChangingProcessLocalTuning() {
        RingAtlasLightTuning.useGamma(3.5F, 1.75F);

        RingAtlasLightCommand.Result result = RingAtlasLightCommand.show();

        assertTrue(result.success());
        assertEquals("Ring Atlas light: Gamma (falloff 3.50, peak 1.75)", result.message());
        assertEquals(RingAtlasLightProfile.gamma(3.5F, 1.75F),
                RingAtlasLightTuning.profile());
    }

    @Test
    void resetAndNumericTuningUpdateOnlyTheProcessLocalProfile() {
        RingAtlasLightCommand.Result tuned = RingAtlasLightCommand.tune(0.5F, 3.0F);
        assertTrue(tuned.success());
        assertEquals(RingAtlasLightProfile.gamma(0.5F, 3.0F),
                RingAtlasLightTuning.profile());

        RingAtlasLightCommand.Result reset = RingAtlasLightCommand.reset();
        assertTrue(reset.success());
        assertEquals(RingAtlasLightProfile.MIDPOINT, RingAtlasLightTuning.profile());
        assertEquals("Ring Atlas light: Midpoint (default)", reset.message());
    }

    @Test
    void invalidAndNonFiniteNumericValuesFailWithoutChangingTuning() {
        RingAtlasLightProfile initial = RingAtlasLightTuning.profile();
        for (float[] values : new float[][] {
                {0.49F, 1.0F}, {6.01F, 1.0F},
                {1.0F, 0.09F}, {1.0F, 3.01F},
                {Float.NaN, 1.0F}, {1.0F, Float.POSITIVE_INFINITY}}) {
            RingAtlasLightCommand.Result result =
                    RingAtlasLightCommand.tune(values[0], values[1]);
            assertFalse(result.success());
            assertTrue(result.message().startsWith("Ring Atlas light error: "));
            assertEquals(initial, RingAtlasLightTuning.profile());
        }
    }
}
