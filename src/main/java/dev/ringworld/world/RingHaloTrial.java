package dev.ringworld.world;

/** Opt-in literal Installation 04 scale; never a normal generation preset. */
public final class RingHaloTrial {
    public static final int WIDTH = 318_000;
    // Nearest chunk-aligned circumference to pi * 10,000,000 metres.
    public static final int CIRCUMFERENCE = 31_415_920;
    public static final int PLACEHOLDER_STEP = 16_384;
    private RingHaloTrial() { }

    public static boolean dimensionsMatch(RingGeometry geometry) {
        return geometry != null && geometry.widthBlocks() == WIDTH
                && geometry.circumferenceBlocks() == CIRCUMFERENCE;
    }

    public static boolean active(RingGeometry geometry) {
        return Boolean.getBoolean("ringworld.haloTrial") && dimensionsMatch(geometry);
    }
}
