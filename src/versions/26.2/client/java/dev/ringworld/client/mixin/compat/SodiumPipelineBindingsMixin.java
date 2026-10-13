package dev.ringworld.client.mixin.compat;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.shaders.UniformType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
abstract class SodiumPipelineBindingsMixin {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/BindGroupLayout$Builder;build()Lcom/mojang/blaze3d/pipeline/BindGroupLayout;", ordinal = 0))
    private static BindGroupLayout ringworld$declare(BindGroupLayout.Builder builder) {
        return builder.withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withUniform("RingWorld", UniformType.UNIFORM_BUFFER).build();
    }
}
