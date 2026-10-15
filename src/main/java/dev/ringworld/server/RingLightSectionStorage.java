package dev.ringworld.server;

import net.minecraft.world.level.chunk.DataLayer;

/** Internal bridge: null retains the untouched vanilla implementation. */
public interface RingLightSectionStorage {
    RingLightSectionTable<DataLayer> ringworld$table();
    void ringworld$table(RingLightSectionTable<DataLayer> table);
}
