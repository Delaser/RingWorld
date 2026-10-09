package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingBuildingPlacement;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevent client prediction of a placement the authoritative server rejects. */
@Mixin(BlockItem.class)
abstract class ClientBlockItemMixin {
    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void ringworld$placementPolicy(BlockPlaceContext context, BlockState state,
            CallbackInfoReturnable<Boolean> cir) {
        if (context.getLevel().isClientSide() && context.getPlayer() != null
                && ClientRingState.geometry() != null
                && !RingBuildingPlacement.allows(ClientRingState.outsideBuilding(),
                    ClientRingState.geometry(), context)) cir.setReturnValue(false);
    }
}
