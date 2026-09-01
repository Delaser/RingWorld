package dev.ringworld.world;

/**
 * Visual phase of the fixed ringworld sun.
 *
 * <p>Vanilla still owns the authoritative 24,000-tick daylight clock and all
 * gameplay lighting. RingWorld keeps the sun stationary, then varies only its
 * apparent intensity and colour so the celestial visual follows that same
 * global clock without a physical shadow-panel array.</p>
 */
public final class RingSkyCycle {
    public static final long DAY_LENGTH_TICKS = 24_000L;
    public static final float FIXED_SUN_ANGLE_RADIANS = 0.0F;
    public static final int NIGHT_SKY_RGB = 0x050810;
    public static final int VOID_SKY_RGB = 0x010103;
    /** One tenth of vanilla's padded 30-unit sun quad. */
    public static final float SUN_HALF_WIDTH = 3.0F;
    /** Fraction of vanilla's padded sun quad occupied by the bright disc. */
    public static final float SUN_VISIBLE_TEXTURE_SCALE = 0.2625F;
    /** Angular radius of the visible disc inside vanilla's padded sun sprite. */
    public static final float SUN_ANGULAR_HALF_WIDTH_DEGREES =
            (float)Math.toDegrees(Math.atan((SUN_HALF_WIDTH * SUN_VISIBLE_TEXTURE_SCALE) / 100.0F));
    public static final float SUN_RENDER_DISTANCE = 100.0F;

    private RingSkyCycle() { }

    /** Keeps one inertially fixed star field while the local tangent frame rotates. */
    public static float starFieldAngleRadians(RingGeometry geometry, double cameraX) {
        if (geometry == null) throw new IllegalArgumentException("ring geometry is required");
        return (float)-geometry.angleAt(cameraX);
    }

    /** Returns a fixed backdrop RGB, or {@code -1} for Minecraft's native atmosphere. */
    public static int fixedBackdropRgb(RingSkyProfile profile) {
        if (profile == null) throw new IllegalArgumentException("sky profile is required");
        return switch (profile.backdrop()) {
            case ATMOSPHERE -> -1;
            case NIGHT -> NIGHT_SKY_RGB;
            case VOID -> VOID_SKY_RGB;
        };
    }

    /** Applies the saved backdrop's star-visibility policy before weather alpha. */
    public static float starBrightness(RingSkyProfile profile, float vanillaBrightness) {
        if (profile == null) throw new IllegalArgumentException("sky profile is required");
        return switch (profile.backdrop()) {
            case ATMOSPHERE -> vanillaBrightness;
            case NIGHT -> Math.max(vanillaBrightness, 0.88F);
            case VOID -> 0.0F;
        };
    }

    /** Large suns are deliberately softer; None is never submitted for drawing. */
    public static float sunAlphaScale(RingSkyProfile profile) {
        if (profile == null) throw new IllegalArgumentException("sky profile is required");
        return switch (profile.lightSource()) {
            case SMALL -> 1.0F;
            case LARGE -> 0.72F;
            case NONE -> 0.0F;
        };
    }

    /** Smoothly converges fog to the visible sky over the final 16 blocks below a rim. */
    public static float exposedHorizonBlend(double cameraY, double wallTopY) {
        if (!Double.isFinite(cameraY) || !Double.isFinite(wallTopY)) return 0.0F;
        double progress = Math.max(0.0, Math.min(1.0,
                (cameraY - (wallTopY - 16.0)) / 16.0));
        return (float)(progress * progress * (3.0 - 2.0 * progress));
    }

    /** Applies the saved ordinary-air fog colour without changing fog distance or shape. */
    public static FogColor fogColor(RingSkyProfile profile, FogColor vanillaFog,
                                    FogColor liveSky, float atmosphereBlend) {
        if (profile == null || vanillaFog == null || liveSky == null) {
            throw new IllegalArgumentException("fog profile and colours are required");
        }
        float blend = Math.max(0.0F, Math.min(1.0F, atmosphereBlend));
        return switch (profile.backdrop()) {
            case ATMOSPHERE -> new FogColor(
                    lerp(vanillaFog.red(), liveSky.red(), blend),
                    lerp(vanillaFog.green(), liveSky.green(), blend),
                    lerp(vanillaFog.blue(), liveSky.blue(), blend));
            case NIGHT -> FogColor.fromRgb(NIGHT_SKY_RGB);
            case VOID -> FogColor.fromRgb(VOID_SKY_RGB);
        };
    }

    /**
     * Smoothly interpolates four familiar Minecraft lighting keyframes:
     * warm dawn, neutral noon, warm dusk, and cool near-dark midnight.
     */
    public static SunVisual sunVisual(double timeOfDay) {
        double phase = wrapDayTime(timeOfDay);
        if (phase < 6_000.0) {
            return interpolate(DAWN, NOON, (float)(phase / 6_000.0));
        }
        if (phase < 12_000.0) {
            return interpolate(NOON, DUSK, (float)((phase - 6_000.0) / 6_000.0));
        }
        if (phase < 18_000.0) {
            return interpolate(DUSK, MIDNIGHT, (float)((phase - 12_000.0) / 6_000.0));
        }
        return interpolate(MIDNIGHT, DAWN, (float)((phase - 18_000.0) / 6_000.0));
    }

    private static SunVisual interpolate(SunVisual start, SunVisual end, float progress) {
        float t = progress * progress * (3.0F - 2.0F * progress);
        return new SunVisual(
                lerp(start.brightness, end.brightness, t),
                lerp(start.red, end.red, t),
                lerp(start.green, end.green, t),
                lerp(start.blue, end.blue, t));
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static double wrapDayTime(double timeOfDay) {
        double wrapped = timeOfDay % DAY_LENGTH_TICKS;
        return wrapped < 0.0 ? wrapped + DAY_LENGTH_TICKS : wrapped;
    }

    private static final SunVisual DAWN = new SunVisual(0.35F, 1.00F, 0.58F, 0.30F);
    private static final SunVisual NOON = new SunVisual(1.00F, 1.00F, 1.00F, 0.94F);
    private static final SunVisual DUSK = new SunVisual(0.35F, 1.00F, 0.52F, 0.26F);
    private static final SunVisual MIDNIGHT = new SunVisual(0.04F, 0.38F, 0.52F, 1.00F);

    public record SunVisual(float brightness, float red, float green, float blue) {
    }

    public record FogColor(float red, float green, float blue) {
        public FogColor {
            if (!Float.isFinite(red) || !Float.isFinite(green) || !Float.isFinite(blue)) {
                throw new IllegalArgumentException("fog colour must be finite");
            }
        }

        public static FogColor fromRgb(int rgb) {
            return new FogColor(
                    (rgb >> 16 & 255) / 255.0F,
                    (rgb >> 8 & 255) / 255.0F,
                    (rgb & 255) / 255.0F);
        }
    }
}
