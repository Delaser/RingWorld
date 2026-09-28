package dev.ringworld.world;

/** Alpha-weighted colour of already-filtered texture pixels. */
public final class RingMaterialColorAverage {
    private long alpha, red, green, blue;

    public void add(int argb) {
        int weight = argb >>> 24;
        alpha += weight;
        red += (long)(argb >>> 16 & 255) * weight;
        green += (long)(argb >>> 8 & 255) * weight;
        blue += (long)(argb & 255) * weight;
    }

    public int rgbOr(int fallback) {
        if (alpha == 0) return fallback;
        return (int)((red + alpha / 2) / alpha) << 16
                | (int)((green + alpha / 2) / alpha) << 8
                | (int)((blue + alpha / 2) / alpha);
    }
}
