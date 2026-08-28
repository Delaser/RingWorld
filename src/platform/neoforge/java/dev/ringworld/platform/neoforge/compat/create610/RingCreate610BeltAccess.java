package dev.ringworld.platform.neoforge.compat.create610;

import net.minecraft.core.BlockPos;

/** Exact-runtime view exposed by the belt mixin without linking its Ponder hierarchy. */
public interface RingCreate610BeltAccess {
    BlockPos getController();

    boolean isController();
}
