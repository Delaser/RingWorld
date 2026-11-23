package dev.ringworld.trial.dh.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import dev.ringworld.trial.dh.DhSurfaceTrial;
import dev.ringworld.world.RingTerrainAtlas;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ringworld.client.render.RingSurfaceTextureRenderer", remap = false)
abstract class SurfaceInputMixin {
    @ModifyExpressionValue(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ldev/ringworld/client/ClientRingState;terrainAtlas()Ldev/ringworld/world/RingTerrainAtlas;"))
    private static RingTerrainAtlas select(RingTerrainAtlas original) {
        return DhSurfaceTrial.select(original);
    }

    @ModifyExpressionValue(
            method = "captureReady",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ldev/ringworld/client/ClientRingState;terrainAtlas()Ldev/ringworld/world/RingTerrainAtlas;"))
    private static RingTerrainAtlas captureSource(RingTerrainAtlas original) {
        return DhSurfaceTrial.displayedSource(original);
    }

    @ModifyExpressionValue(
            method = {"ensureResources", "displayReady"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ldev/ringworld/client/ClientRingState;terrainAtlasRevision()I"))
    private static int revision(int original) {
        return DhSurfaceTrial.revision(original);
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private static void clear(CallbackInfo ci) {
        DhSurfaceTrial.clear();
    }
}
