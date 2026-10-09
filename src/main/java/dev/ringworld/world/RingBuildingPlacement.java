package dev.ringworld.world;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.item.context.BlockPlaceContext;

/** Check both halves of a bed before placing either, including a crossing head. */
public final class RingBuildingPlacement {
    private RingBuildingPlacement() { }
    public static boolean allows(boolean enabled, RingGeometry geometry, BlockPlaceContext context) {
        return RingBuildingSettings.allows(enabled, geometry, context.getClickedPos().getZ())
                && (!(context.getItemInHand().getItem() instanceof BlockItem item
                    && item.getBlock() instanceof BedBlock)
                || RingBuildingSettings.allows(enabled, geometry,
                    context.getClickedPos().relative(context.getHorizontalDirection()).getZ()));
    }
}
