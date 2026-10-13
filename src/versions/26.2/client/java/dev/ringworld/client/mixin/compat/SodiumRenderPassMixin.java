package dev.ringworld.client.mixin.compat;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ringworld.client.render.RingRenderUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
abstract class SodiumRenderPassMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    private void ringworld$bind(RenderPass pass, String name, GpuBufferSlice slice) {
        pass.setUniform(name, slice);
        pass.setUniform("Globals", RenderSystem.getGlobalSettingsUniform());
        RingRenderUniforms.bind(pass);
    }
}
