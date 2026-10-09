package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import net.minecraft.server.level.ServerLevel;
import dev.ringworld.world.RingBuildingSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Direct player fluid placement follows the same outside-building policy. */
@Mixin(BucketItem.class)
abstract class BucketItemMixin {
    @Inject(method = "emptyContents", at = @At("HEAD"), cancellable = true)
    private void ringworld$placementPolicy(LivingEntity actor, Level world, BlockPos pos,
            BlockHitResult hit, CallbackInfoReturnable<Boolean> cir) {
        if (actor instanceof Player && world instanceof ServerLevel server && server.dimension() == Level.OVERWORLD
                && !RingBuildingSettings.allows(RingBuildingSettings.get(server).outsideBuilding(), RingWorldServer.geometryFor(server), pos.getZ())) cir.setReturnValue(false);
    }
}
