package dev.ringworld.client.mixin.compat;

import dev.ringworld.client.ClientRingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
abstract class SodiumFaceVisibilityMixin {
    @Inject(method = "getVisibleFaces", at = @At("HEAD"), cancellable = true)
    private static void ringworld$curvedFaces(CallbackInfoReturnable<Integer> cir) {
        // Flat camera/section signs cannot reject faces on the curved surface.
        // GPU winding/backface culling still applies to the projected mesh.
        if (ClientRingState.geometry() != null) cir.setReturnValue(0x7f);
    }
}
