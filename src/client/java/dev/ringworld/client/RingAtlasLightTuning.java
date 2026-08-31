package dev.ringworld.client;

import dev.ringworld.world.RingAtlasLightProfile;

/** Process-local Atlas lighting controls used by the dual-loader debug command. */
public final class RingAtlasLightTuning {
    private static volatile RingAtlasLightProfile profile = RingAtlasLightProfile.GAMMA;

    private RingAtlasLightTuning() { }

    public static RingAtlasLightProfile profile() {
        return profile;
    }

    public static RingAtlasLightProfile useGamma(float falloffExponent, float peakStrength) {
        profile = RingAtlasLightProfile.gamma(falloffExponent, peakStrength);
        return profile;
    }

    public static RingAtlasLightProfile reset() {
        profile = RingAtlasLightProfile.MIDPOINT;
        return profile;
    }
}
