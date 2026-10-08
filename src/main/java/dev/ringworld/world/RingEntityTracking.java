package dev.ringworld.world;

/** Entity visibility is independent of whether exterior void terrain is sent. */
public final class RingEntityTracking {
    private RingEntityTracking() { }

    /**
     * Keeps an existing pairing while its canonical destination chunk remains
     * in the player's periodic watch window. Vanilla still owns initial
     * pairing inside the terrain band. Exterior entities may also start a
     * pairing within the same distance window without waiting for a void chunk
     * which is deliberately never sent. Vanilla still owns tracking range.
     */
    public static boolean shouldRemainPaired(boolean vanillaChunkTracked,
                                             boolean alreadyPaired,
                                             boolean canonicalChunkWatched,
                                             boolean exteriorChunkWatched) {
        return vanillaChunkTracked || exteriorChunkWatched
                || alreadyPaired && canonicalChunkWatched;
    }
}
