package dev.ringworld.world;

/** Pure policy for retaining an existing entity pairing during chunk delivery transitions. */
public final class RingEntityTracking {
    private RingEntityTracking() { }

    /**
     * Keeps an existing pairing while its canonical destination chunk remains
     * in the player's periodic watch window. Vanilla still owns initial
     * pairing, entity tracking range, and removal after leaving that window.
     */
    public static boolean shouldRemainPaired(boolean vanillaChunkTracked,
                                             boolean alreadyPaired,
                                             boolean canonicalChunkWatched) {
        return vanillaChunkTracked || alreadyPaired && canonicalChunkWatched;
    }
}
