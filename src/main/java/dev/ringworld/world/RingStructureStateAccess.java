package dev.ringworld.world;

/** Loader-neutral bridge for attaching immutable Overworld geometry to structure state. */
public interface RingStructureStateAccess {
    void ringworld$setStructurePolicy(RingGeometry geometry, boolean guaranteeStronghold);
}
