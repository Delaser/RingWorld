package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RingAtlasLightProfileTest {
    @Test
    void gammaDefaultsUseDocumentedFalloffAndPeak() {
        assertEquals(RingAtlasLightProfile.Mode.GAMMA, RingAtlasLightProfile.GAMMA.mode());
        assertEquals(2.0F, RingAtlasLightProfile.GAMMA.falloffExponent());
        assertEquals(1.25F, RingAtlasLightProfile.GAMMA.peakStrength());
        assertEquals(1.0F, RingAtlasLightProfile.GAMMA.shaderMode());
    }

    @Test
    void midpointPreservesEstablishedShaderPath() {
        assertEquals(RingAtlasLightProfile.Mode.MIDPOINT,
                RingAtlasLightProfile.MIDPOINT.mode());
        assertEquals(0.0F, RingAtlasLightProfile.MIDPOINT.shaderMode());
        assertEquals("Midpoint (default)", RingAtlasLightProfile.MIDPOINT.summary());
    }

    @Test
    void rejectsNonFiniteAndOutOfRangeTuning() {
        assertEquals(0.5F, RingAtlasLightProfile.gamma(0.5F, 0.1F).falloffExponent());
        assertEquals(3.0F, RingAtlasLightProfile.gamma(6.0F, 3.0F).peakStrength());
        assertThrows(IllegalArgumentException.class,
                () -> RingAtlasLightProfile.gamma(Float.NaN, 1.0F));
        assertThrows(IllegalArgumentException.class,
                () -> RingAtlasLightProfile.gamma(0.49F, 1.0F));
        assertThrows(IllegalArgumentException.class,
                () -> RingAtlasLightProfile.gamma(6.01F, 1.0F));
        assertThrows(IllegalArgumentException.class,
                () -> RingAtlasLightProfile.gamma(2.0F, 0.09F));
        assertThrows(IllegalArgumentException.class,
                () -> RingAtlasLightProfile.gamma(2.0F, 3.01F));
    }
}
