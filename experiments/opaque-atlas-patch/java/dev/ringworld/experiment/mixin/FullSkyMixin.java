package dev.ringworld.experiment.mixin;

import dev.ringworld.client.render.RingSkyView;
import dev.ringworld.experiment.FullRingScene;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ringworld.client.render.RingSurfaceGpu", remap = false)
abstract class FullSkyMixin {
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private static void trial$oneOwner(CallbackInfo ci) {
        var view = RingSkyView.current();
        if (!dev.ringworld.experiment.NativeAtlasTarget.ENABLED
                && view != null
                && FullRingScene.replacesSky(view)) ci.cancel();
    }
}
