package dev.ringworld.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ringworld.client.ClientRingState;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ModelBlockRenderer.class)
abstract class DripstoneModelMixin {
    @ModifyArg(method = "tesselateBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getOffset(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"),
            index = 0)
    private BlockPos ringworld$modelOffsetPosition(BlockPos position, @Local(argsOnly = true) BlockState state) {
        return ClientRingState.dripstoneOffsetPosition(state, position);
    }
}
