package dev.ringworld.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Older source ABIs retain their established map-colour wall palette. */
final class RingWallMaterialColors {
    static int color(BlockState state, Level world) {
        return state.getMapColor(world, BlockPos.ZERO).col & 0xFFFFFF;
    }
}
