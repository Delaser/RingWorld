package dev.ringworld.world;

import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.context.BlockPlaceContext;

/** Check both halves of a bed before placing either, including a crossing head. */
public final class RingBuildingPlacement {
    private RingBuildingPlacement() { }
    public static boolean allows(boolean enabled, RingGeometry geometry, BlockPlaceContext context) {
        return RingBuildingSettings.allows(enabled, geometry, context.getClickedPos().getZ())
                && (!(context.getItemInHand().getItem() instanceof BedItem)
                || RingBuildingSettings.allows(enabled, geometry,
                    context.getClickedPos().relative(context.getHorizontalDirection()).getZ()));
    }
}
