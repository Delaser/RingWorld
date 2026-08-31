package dev.ringworld.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class RingAtlasLightProfileTest {
    @Test
    void gammaDefaultsUseRaisedPeak() {
        assertEquals(RingAtlasLightProfile.Mode.GAMMA, RingAtlasLightProfile.GAMMA.mode());
        assertEquals(2.0F, RingAtlasLightProfile.GAMMA.falloffExponent());
        assertEquals(1.25F, RingAtlasLightProfile.GAMMA.peakStrength());
        assertEquals(1.0F, RingAtlasLightProfile.GAMMA.shaderMode());
    }

    @Test
    void midpointPreservesEstablishedShaderPath() {
        assertEquals(RingAtlasLightProfile.Mode.MIDPOINT, RingAtlasLightProfile.MIDPOINT.mode());
        assertEquals(0.0F, RingAtlasLightProfile.MIDPOINT.shaderMode());
        assertEquals("Midpoint (default)", RingAtlasLightProfile.MIDPOINT.summary());
    }

    @Test
    void rejectsUnsafeLiveValues() {
        assertThrows(IllegalArgumentException.class,
                () -> RingAtlasLightProfile.gamma(Float.NaN, 1.0F));
        assertThrows(IllegalArgumentException.class,
                () -> RingAtlasLightProfile.gamma(0.49F, 1.0F));
        assertThrows(IllegalArgumentException.class,
                () -> RingAtlasLightProfile.gamma(2.0F, 3.01F));
    }
}
