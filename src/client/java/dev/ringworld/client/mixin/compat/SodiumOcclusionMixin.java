package dev.ringworld.client.mixin.compat;

import dev.ringworld.client.ClientRingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
abstract class SodiumOcclusionMixin {
    @Inject(method = "shouldUseOcclusionCulling", at = @At("HEAD"), cancellable = true)
    private void ringworld$curvedVisibility(CallbackInfoReturnable<Boolean> cir) {
        if (ClientRingState.geometry() != null) cir.setReturnValue(false);
    }
}
