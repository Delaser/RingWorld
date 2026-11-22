package dev.ringworld.experiment.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.ringworld.experiment.AtlasPatch;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class PatchMixin {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void ringworld$submitAtlasPatch(
            PoseStack poses,
            LevelRenderState frame,
            SubmitNodeCollector collector,
            CallbackInfo ci) {
        AtlasPatch.submit(poses, frame, collector);
    }
}
