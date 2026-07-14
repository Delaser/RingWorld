package dev.ringworld.world;

/** Implemented by the vanilla noise generator mixin once it belongs to a ring Overworld. */
public interface RingWorldGeneratorAccess {
    void ringworld$setGeometry(RingGeometry geometry);
    RingGeometry ringworld$getGeometry();
    void ringworld$setWallHeight(int wallHeightBlocks);
    int ringworld$getWallHeight();
}
