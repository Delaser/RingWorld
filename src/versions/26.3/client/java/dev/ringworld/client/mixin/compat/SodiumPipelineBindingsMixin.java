package dev.ringworld.client.mixin.compat;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReceiver;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
abstract class SodiumPipelineBindingsMixin {
    @ModifyReceiver(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/pipeline/BindGroupLayout$Builder;build()Lcom/mojang/renderpearl/api/pipeline/BindGroupLayout;", ordinal = 0))
    private static BindGroupLayout.Builder ringworld$declare(BindGroupLayout.Builder builder) {
        return builder.withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withUniform("RingWorld", UniformType.UNIFORM_BUFFER)
                .withUniform("RingWorldSurface", UniformType.COMBINED_IMAGE_SAMPLER);
    }
}
