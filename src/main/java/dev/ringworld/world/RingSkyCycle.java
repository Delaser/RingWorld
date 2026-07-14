package dev.ringworld.world;

/**
 * Global shadow-square-array cycle. Vanilla still owns the authoritative
 * 24,000-tick daylight clock; this class maps that clock to repeated eclipses
 * of the fixed ringworld sun by a slowly rotating inner array.
 */
public final class RingSkyCycle {
    public static final long DAY_LENGTH_TICKS = 24_000L;
    public static final float FIXED_SUN_ANGLE_RADIANS = 0.0F;
    public static final float SUN_HALF_WIDTH = 30.0F;
    /** Fraction of vanilla's padded sun quad occupied by the bright disc. */
    public static final float SUN_VISIBLE_TEXTURE_SCALE = 0.2625F;
    /** Angular radius of the visible disc inside vanilla's padded sun sprite. */
    public static final float SUN_ANGULAR_HALF_WIDTH_DEGREES =
            (float)Math.toDegrees(Math.atan((SUN_HALF_WIDTH * SUN_VISIBLE_TEXTURE_SCALE) / 100.0F));
    public static final float SUN_RENDER_DISTANCE = 100.0F;
    /** Niven's reference design uses twenty linked shadow squares. */
    public static final int SHADOW_PANEL_COUNT = 20;
    public static final float PANEL_SPACING_DEGREES = 360.0F / SHADOW_PANEL_COUNT;
    public static final float PANEL_ORBIT_RADIUS = 75.0F;
    public static final float PANEL_NEAR_DISTANCE = SUN_RENDER_DISTANCE - PANEL_ORBIT_RADIUS;
    /** Constant physical ribbon width that matches the visible sun at closest approach. */
    public static final float PANEL_PHYSICAL_HALF_WIDTH =
            (float)Math.tan(Math.toRadians(SUN_ANGULAR_HALF_WIDTH_DEGREES)) * PANEL_NEAR_DISTANCE;
    /** Orbital angle whose projection from the player reaches the sun's visible edge. */
    public static final float SUN_ORBITAL_HALF_WIDTH_DEGREES = (float)Math.toDegrees(
            Math.asin(SUN_RENDER_DISTANCE / PANEL_ORBIT_RADIUS
                    * Math.sin(Math.toRadians(SUN_ANGULAR_HALF_WIDTH_DEGREES)))
                    - Math.toRadians(SUN_ANGULAR_HALF_WIDTH_DEGREES));
    /**
     * One panel plus the projected solar radius occupies a quarter of one
     * pitch. It therefore reaches first contact at dusk, centers at midnight,
     * and clears the sun at the end of the Minecraft day.
     */
    public static final float PANEL_ANGULAR_HALF_LENGTH_DEGREES =
            PANEL_SPACING_DEGREES / 4.0F - SUN_ORBITAL_HALF_WIDTH_DEGREES;
    /** The whole array takes twenty Minecraft days to make one revolution. */
    public static final long PANEL_ROTATION_PERIOD_TICKS = DAY_LENGTH_TICKS * SHADOW_PANEL_COUNT;
    public static final float PANEL_SPEED_DEGREES_PER_TICK =
            360.0F / PANEL_ROTATION_PERIOD_TICKS;
    public static final float PANEL_REPEAT_ANGLE_DEGREES = PANEL_SPACING_DEGREES;
    private static final double DUSK_START = 12_000.0;
    private static final float DUSK_CONTACT_ANGLE =
            -(PANEL_ANGULAR_HALF_LENGTH_DEGREES + SUN_ORBITAL_HALF_WIDTH_DEGREES);

    private RingSkyCycle() { }

    public static ShadowPanel shadowPanel(double timeOfDay) {
        double phase = wrapDayTime(timeOfDay);
        float unwrappedOffset = DUSK_CONTACT_ANGLE
                + (float)(phase - DUSK_START) * PANEL_SPEED_DEGREES_PER_TICK;
        float offset = wrapCentered(unwrappedOffset, PANEL_REPEAT_ANGLE_DEGREES);
        return moving(offset);
    }

    private static ShadowPanel moving(float offset) {
        float panelMin = offset - PANEL_ANGULAR_HALF_LENGTH_DEGREES;
        float panelMax = offset + PANEL_ANGULAR_HALF_LENGTH_DEGREES;
        float overlap = Math.max(0.0F,
                Math.min(SUN_ORBITAL_HALF_WIDTH_DEGREES, panelMax)
                        - Math.max(-SUN_ORBITAL_HALF_WIDTH_DEGREES, panelMin));
        return new ShadowPanel(true, offset,
                Math.min(1.0F, overlap / (SUN_ORBITAL_HALF_WIDTH_DEGREES * 2.0F)));
    }

    private static float wrapCentered(float value, float period) {
        float wrapped = value % period;
        if (wrapped < -period / 2.0F) wrapped += period;
        if (wrapped >= period / 2.0F) wrapped -= period;
        return wrapped;
    }

    private static double wrapDayTime(double timeOfDay) {
        double wrapped = timeOfDay % DAY_LENGTH_TICKS;
        return wrapped < 0.0 ? wrapped + DAY_LENGTH_TICKS : wrapped;
    }

    public record ShadowPanel(boolean visible, float offset, float occlusion) {
    }
}
