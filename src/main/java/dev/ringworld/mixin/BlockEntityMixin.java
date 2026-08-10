package dev.ringworld.mixin;

import dev.ringworld.server.RingBlockEntityLoadContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntity.class)
abstract class BlockEntityMixin {
    @Inject(method = "getPosFromTag", at = @At("RETURN"), cancellable = true)
    private static void ringworld$restoreSavedPeriodicAlias(
            ChunkPos owner, CompoundTag tag, CallbackInfoReturnable<BlockPos> cir) {
        cir.setReturnValue(RingBlockEntityLoadContext.restoreSavedAlias(owner, tag, cir.getReturnValue()));
    }
}
