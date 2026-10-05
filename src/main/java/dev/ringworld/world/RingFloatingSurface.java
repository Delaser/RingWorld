package dev.ringworld.world;

/** One detached upper solid interval and the exposed surface below it. Trial only. */
public record RingFloatingSurface(int x, int z, int bottom, int top,
                                  int groundTop, int groundColor, int color, int waterCoverage) {
    public RingFloatingSurface {
        if (top <= bottom || groundTop > bottom - 8)
            throw new IllegalArgumentException("floating trial requires an eight-block air gap");
    }
}
