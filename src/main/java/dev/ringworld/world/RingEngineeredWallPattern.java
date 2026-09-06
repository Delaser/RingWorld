package dev.ringworld.world;

/** Periodic structural bays and correlated weathering for the engineered rim. */
public final class RingEngineeredWallPattern {
    private RingEngineeredWallPattern() { }

    public record Bay(int index, double position, double width) { }

    public static Bay bay(int x, int circumference) {
        if (circumference <= 0) throw new IllegalArgumentException("positive circumference required");
        int count = Math.max(1, (circumference + 12) / 24);
        double coordinate = (double)Math.floorMod(x, circumference) * count / circumference;
        double width = (double)circumference / count;
        return new Bay((int)coordinate, (coordinate - Math.floor(coordinate)) * width, width);
    }

    public static boolean support(int x, int circumference) {
        Bay bay = bay(x, circumference);
        return bay.position() < 2.0;
    }

    public static int materialRoll(RingWallStyle style, int x, int y, int depth,
                                   int circumference, long seed) {
        return RingWallPattern.originalPanelRoll(style, x, y, depth, circumference, seed);
    }

    public static int collapseDepth(RingWallStyle style, int x, int depth,
                                    int circumference, long seed) {
        if (depth < 0) throw new IllegalArgumentException("nonnegative depth required");
        if (style.decayPercent() == 0) return 0;
        Bay bay = bay(x, circumference);
        double broad = RingWallPattern.smoothNoise2d(seed ^ 0x42524F4B454E4241L,
                x, depth, 24, 7, circumference);
        double fracture = RingWallPattern.smoothNoise2d(seed ^ 0x4652414354555245L,
                x, depth, 5, 3, circumference);
        double wear = style.decayPercent() / 100.0;
        double weakness = broad * 0.7 + fracture * 0.3;
        double severity = Math.max(0, (weakness - (0.70 - wear * 0.48)) / 0.48);
        double distanceFromSupport = Math.min(bay.position(), bay.width() - bay.position());
        double structural = Math.min(1.0, 0.18 + distanceFromSupport / 6.0);
        int maximum = 2 + style.decayPercent() * 32 / 100;
        return Math.min(maximum, (int)Math.floor(severity * maximum * structural));
    }
}
