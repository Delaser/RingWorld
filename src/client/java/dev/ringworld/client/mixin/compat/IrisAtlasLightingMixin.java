package dev.ringworld.client.mixin.compat;

import dev.ringworld.client.compat.IrisAtlasTarget;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
abstract class IrisAtlasLightingMixin {
    @Inject(method = {"beginLevelRendering", "destroy"}, at = @At("HEAD"))
    private void ringworld$clearPending(CallbackInfo ci) {
        IrisAtlasTarget.clear(this);
    }

    @Inject(method = "beginTranslucents", at = @At("RETURN"))
    private void ringworld$drawLitAtlas(CallbackInfo ci) {
        IrisAtlasTarget.drawLit(this);
    }
}
