package dev.ringworld.world;

/** Distant water target, applied before mip filtering. Keep material.glsl in sync. */
public final class RingWaterColor {
    public static final int MAX_BED_DEPTH = 7;

    /** A shallow bed reflects light back through the water. Red attenuates fastest. */
    public static int overBed(int water, int bed, int depth) {
        if (depth < 1 || depth > MAX_BED_DEPTH) return water;
        double transmission = (MAX_BED_DEPTH + 1.0 - depth) / MAX_BED_DEPTH;
        transmission *= transmission;
        int r = reflectedChannel(water >> 16, bed >> 16, 0.27 * transmission);
        int g = reflectedChannel(water >> 8, bed >> 8, 0.36 * transmission);
        int b = reflectedChannel(water, bed, 0.50 * transmission);
        return r << 16 | g << 8 | b;
    }

    private static int reflectedChannel(int water, int bed, double amount) {
        int base = water & 255;
        return base + (int)Math.round((255 - base) * ((bed & 255) / 255.0) * amount);
    }

    private RingWaterColor() { }

    public static int tint(int rgb, double coverage) {
        if (coverage <= 0.0) return rgb & 0xFFFFFF;
        double amount = Math.min(1.0, coverage);
        int r = rgb >>> 16 & 255, g = rgb >>> 8 & 255, b = rgb & 255;
        int peak = Math.max(r, Math.max(g, b));
        return channel(r, peak, amount) << 16 | channel(g, peak, amount) << 8
                | channel(b, peak, amount);
    }

    private static int channel(int value, int peak, double amount) {
        double water = (value * 0.94 + peak * 0.06) * 0.89;
        return Math.max(0, Math.min(255, (int)Math.round(value + (water - value) * amount)));
    }
}
