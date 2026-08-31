package dev.ringworld.world;

/**
 * Loader-neutral client rendering parameters for the distant Atlas light layer.
 * The profile is process-local debug state; it is never saved or sent over the
 * network.
 */
public record RingAtlasLightProfile(Mode mode, float falloffExponent, float peakStrength) {
    public static final float MIN_FALLOFF = 0.5F;
    public static final float MAX_FALLOFF = 6.0F;
    public static final float MIN_PEAK = 0.1F;
    public static final float MAX_PEAK = 3.0F;
    public static final float DEFAULT_GAMMA_FALLOFF = 2.0F;
    public static final float DEFAULT_GAMMA_PEAK = 1.25F;
    public static final RingAtlasLightProfile MIDPOINT =
            new RingAtlasLightProfile(Mode.MIDPOINT, DEFAULT_GAMMA_FALLOFF, DEFAULT_GAMMA_PEAK);
    public static final RingAtlasLightProfile GAMMA = gamma(
            DEFAULT_GAMMA_FALLOFF, DEFAULT_GAMMA_PEAK);

    public RingAtlasLightProfile {
        if (mode == null) throw new IllegalArgumentException("mode is required");
        if (!Float.isFinite(falloffExponent)
                || falloffExponent < MIN_FALLOFF || falloffExponent > MAX_FALLOFF) {
            throw new IllegalArgumentException("falloff must be between "
                    + MIN_FALLOFF + " and " + MAX_FALLOFF);
        }
        if (!Float.isFinite(peakStrength)
                || peakStrength < MIN_PEAK || peakStrength > MAX_PEAK) {
            throw new IllegalArgumentException("peak must be between "
                    + MIN_PEAK + " and " + MAX_PEAK);
        }
    }

    public static RingAtlasLightProfile gamma(float falloffExponent, float peakStrength) {
        return new RingAtlasLightProfile(Mode.GAMMA, falloffExponent, peakStrength);
    }

    /** Values written to the trailing RingWorld Atlas-light shader vec4. */
    public float shaderMode() {
        return mode == Mode.GAMMA ? 1.0F : 0.0F;
    }

    public String summary() {
        return mode == Mode.GAMMA
                ? "Gamma (falloff %.2f, peak %.2f)".formatted(falloffExponent, peakStrength)
                : "Midpoint (default)";
    }

    public enum Mode {
        MIDPOINT,
        GAMMA
    }
}
