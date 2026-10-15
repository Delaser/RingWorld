package dev.ringworld.world;

/** Revisions order edits within a history; content must also match across histories. */
public final class RingAtlasCacheIdentity {
    private RingAtlasCacheIdentity() {}

    public static boolean matches(long serverRevision, long serverContent,
                                  long clientRevision, long clientContent) {
        return serverRevision >= 0 && serverRevision == clientRevision && serverContent == clientContent;
    }
}
