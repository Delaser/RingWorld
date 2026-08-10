package dev.ringworld.world;

/** Pure policy for the temporary haze covering an incomplete terrain Atlas. */
public final class RingSurfaceGenerationFog {
    public static final float MAX_FOG = 0.88F;

    private RingSurfaceGenerationFog() { }

    /** Returns the fraction of proxy terrain replaced by the live fog colour. */
    public static float amount(double atlasCompletion) {
        double completion = Double.isFinite(atlasCompletion)
                ? Math.max(0.0, Math.min(1.0, atlasCompletion)) : 0.0;
        double clear = completion * completion * completion
                * (completion * (completion * 6.0 - 15.0) + 10.0);
        return (float)(MAX_FOG * (1.0 - clear));
    }
}
