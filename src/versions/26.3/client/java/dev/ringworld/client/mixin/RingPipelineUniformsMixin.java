package dev.ringworld.client.mixin;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import dev.ringworld.client.render.RingShaderContract;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Adapt the finished stage pair without changing the builder a mod may reuse. */
@Mixin(RenderPipeline.Builder.class)
abstract class RingPipelineUniformsMixin {
    @ModifyArgs(method = "build", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;<init>"))
    private void ringworld$declarePrivateUniforms(Args args) {
        Map<ShaderType, Identifier> shaders = args.get(1);
        if (shaders.size() != 2) return;
        var material = RingShaderContract.pairedMaterial(shaders.get(ShaderType.VERTEX), shaders.get(ShaderType.FRAGMENT));
        if (material == null) return;
        var adapted = new EnumMap<>(shaders);
        adapted.put(ShaderType.VERTEX, material);
        adapted.put(ShaderType.FRAGMENT, material);
        args.set(1, adapted);
        Collection<BindGroupLayout> layouts = args.get(3);
        var adaptedLayouts = new ArrayList<>(layouts);
        adaptedLayouts.add(BindGroupLayout.builder().withUniform(RingShaderContract.UNIFORM, UniformType.UNIFORM_BUFFER).build());
        if (material.getPath().equals("core/terrain")) {
            adaptedLayouts.add(BindGroupLayout.builder().withUniform("RingWorldSurface", UniformType.COMBINED_IMAGE_SAMPLER).build());
        }
        args.set(3, List.copyOf(adaptedLayouts));
    }
}
