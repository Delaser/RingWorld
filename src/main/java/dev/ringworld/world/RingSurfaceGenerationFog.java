package dev.ringworld.world;

/** Pure policy for the temporary haze covering an incomplete terrain Atlas. */
public final class RingSurfaceGenerationFog {
    public static final float MAX_FOG = 0.88F;
    public static final float MAX_SEED_PREVIEW_FOG = 0.20F;

    private RingSurfaceGenerationFog() { }

    /** Returns the fraction of proxy terrain replaced by the live fog colour. */
    public static float amount(double atlasCompletion) {
        return amount(atlasCompletion, false);
    }

    /** A seed-derived biome preview remains hazy, but no longer gets hidden by the fallback fog. */
    public static float amount(double atlasCompletion, boolean hasSeedPreview) {
        double completion = Double.isFinite(atlasCompletion)
                ? Math.max(0.0, Math.min(1.0, atlasCompletion)) : 0.0;
        double clear = completion * completion * completion
                * (completion * (completion * 6.0 - 15.0) + 10.0);
        float maximum = hasSeedPreview ? MAX_SEED_PREVIEW_FOG : MAX_FOG;
        return (float)(maximum * (1.0 - clear));
    }
}
