package dev.ringworld.world;

import net.minecraft.world.level.ChunkPos;

/** Installs the owning Overworld's periodic coordinate topology on entity storage. */
public interface RingEntityManagerAccess {
    void ringworld$setGeometry(RingGeometry geometry);

    /** Starts entity-file IO for a terrain region whose periodic holders are already ready. */
    void ringworld$ensureLoaded(ChunkPos pos);
}
