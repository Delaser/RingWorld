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
    /** One tenth of vanilla's padded 30-unit sun quad. */
    public static final float SUN_HALF_WIDTH = 3.0F;
    /** Fraction of vanilla's padded sun quad occupied by the bright disc. */
    public static final float SUN_VISIBLE_TEXTURE_SCALE = 0.2625F;
    /** Angular radius of the visible disc inside vanilla's padded sun sprite. */
    public static final float SUN_ANGULAR_HALF_WIDTH_DEGREES =
            (float)Math.toDegrees(Math.atan((SUN_HALF_WIDTH * SUN_VISIBLE_TEXTURE_SCALE) / 100.0F));
    public static final float SUN_RENDER_DISTANCE = 100.0F;

    private RingSkyCycle() { }

    /**
     * Rotation that expresses one inertially fixed star field in the player's
     * local tangent frame. Moving around the ring rotates local up, so the sky
     * applies the equal and opposite rotation instead of following the player.
     */
    public static float starFieldAngleRadians(RingGeometry geometry, double cameraX) {
        if (geometry == null) throw new IllegalArgumentException("ring geometry is required");
        return (float)-geometry.angleAt(cameraX);
    }

    /**
     * Smoothly removes the flat-world fog/sky colour split over the final
     * sixteen blocks below a finite wall top. Terrain hides that split near
     * the deck; the exposed rim-top view must converge to one atmosphere.
     */
    public static float exposedHorizonBlend(double cameraY, double wallTopY) {
        if (!Double.isFinite(cameraY) || !Double.isFinite(wallTopY)) return 0.0F;
        double progress = Math.max(0.0, Math.min(1.0,
                (cameraY - (wallTopY - 16.0)) / 16.0));
        return (float)(progress * progress * (3.0 - 2.0 * progress));
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
}
