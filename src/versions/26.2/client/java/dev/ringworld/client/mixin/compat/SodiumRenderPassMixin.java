package dev.ringworld.client.mixin.compat;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ringworld.client.render.RingRenderUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
abstract class SodiumRenderPassMixin {
    // Sodium may bind other slices later in this pass. Add ours once.
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", ordinal = 0, shift = At.Shift.AFTER))
    private void ringworld$bind(CallbackInfo ci, @Local RenderPass pass) {
        pass.setUniform("Globals", RenderSystem.getGlobalSettingsUniform());
        RingRenderUniforms.bind(pass);
    }
}
