package dev.ringworld.world;

/** Accepted 26.3 water appearance, applied before texture mip filtering. */
public final class RingWaterColor {
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
        double water = (value * 0.88 + peak * 0.12) * 1.15;
        return Math.max(0, Math.min(255, (int)Math.round(value + (water - value) * amount)));
    }
}
