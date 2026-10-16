package dev.ringworld.world;

/** Weather tone for terrain; precipitation must never erase the ring surface. */
public final class RingSurfaceWeather {
    public static final float FULL_RAIN_REVEAL = 0.55F;

    private RingSurfaceWeather() { }

    /**
     * Fraction of lit terrain retained against the live fog colour. This is
     * distinct from celestial alpha and from the compositor's opaque coverage.
     */
    public static float reveal(float rainLevel) {
        float rain = Float.isFinite(rainLevel)
                ? Math.max(0.0F, Math.min(1.0F, rainLevel)) : 1.0F;
        return 1.0F - rain * (1.0F - FULL_RAIN_REVEAL);
    }
}
