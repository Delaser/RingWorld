package dev.ringworld.world;

import java.util.function.IntFunction;

/** Bounded, column-local omission of thin manufactured intervals above actual air. */
public final class RingAtlasColumnSurface {
    public static final int MIN_AIR_GAP = 8;
    public static final int MAX_BUILD_THICKNESS = 64;
    public enum Material { AIR, BUILT, TERRAIN }
    private RingAtlasColumnSurface() { }

    /** Returns a block Y, just like WORLD_SURFACE; never reads below the dimension minimum. */
    public static int select(int top, int minimumY, IntFunction<Material> column) {
        int candidate = top;
        while (candidate >= minimumY && column.apply(candidate) == Material.BUILT) {
            int bottom = candidate;
            while (bottom > minimumY && column.apply(bottom - 1) != Material.AIR) {
                if (candidate - bottom + 2 > MAX_BUILD_THICKNESS) return candidate;
                bottom--;
            }
            if (bottom == minimumY || column.apply(bottom) != Material.BUILT) return candidate;
            int ground = bottom - 1;
            while (ground >= minimumY && column.apply(ground) == Material.AIR) ground--;
            if (ground < minimumY || bottom - ground - 1 < MIN_AIR_GAP) return candidate;
            candidate = ground; // Repeat for stacked detached builds; keep the first terrain surface.
        }
        return candidate;
    }
}
