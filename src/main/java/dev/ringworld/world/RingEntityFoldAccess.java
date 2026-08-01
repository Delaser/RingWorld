package dev.ringworld.world;

/** Optional entity hook for transient coordinates that must follow a canonical X fold. */
public interface RingEntityFoldAccess {
    void ringworld$onCanonicalFold(double deltaX);
}
