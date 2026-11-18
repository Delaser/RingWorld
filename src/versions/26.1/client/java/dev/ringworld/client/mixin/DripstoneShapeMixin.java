package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Use the server's block position to seed the shape's horizontal offset in each client X image. */
@Mixin(PointedDripstoneBlock.class)
abstract class DripstoneShapeMixin {
    @ModifyVariable(method = "getShape", at = @At("HEAD"), argsOnly = true)
    private BlockPos ringworld$shapePosition(BlockPos position, BlockState state, BlockGetter level,
                                              BlockPos original, CollisionContext context) {
        if (!(level instanceof ClientLevel)) return position;
        var geometry = ClientRingState.geometry();
        if (geometry == null) return position;
        int x = geometry.wrapBlockX(position.getX());
        return x == position.getX() ? position : new BlockPos(x, position.getY(), position.getZ());
    }
}
