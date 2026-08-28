package dev.ringworld.platform.neoforge.compat.create610;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Exact-runtime fluid view without exposing Create's Ponder-derived hierarchy. */
public interface RingCreate610TankAccess {
    IFluidHandler ringworld$tankInventory();
}
