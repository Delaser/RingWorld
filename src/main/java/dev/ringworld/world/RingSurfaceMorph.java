package dev.ringworld.world;

/** Pure timing policy for cross-fading one incomplete-Atlas texture revision into the next. */
public final class RingSurfaceMorph {
    public static final long DURATION_NANOS = 750_000_000L;

    private RingSurfaceMorph() { }

    public static float progress(long elapsedNanos) {
        if (elapsedNanos <= 0L) return 0.0F;
        if (elapsedNanos >= DURATION_NANOS) return 1.0F;
        double linear = (double)elapsedNanos / DURATION_NANOS;
        return (float)(linear * linear * linear
                * (linear * (linear * 6.0 - 15.0) + 10.0));
    }
}
