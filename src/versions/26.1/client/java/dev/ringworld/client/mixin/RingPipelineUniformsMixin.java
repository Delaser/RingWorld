package dev.ringworld.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import dev.ringworld.client.render.RingShaderContract;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Adapt the finished stage pair without changing the builder a mod may reuse. */
@Mixin(RenderPipeline.Builder.class)
abstract class RingPipelineUniformsMixin {
    @ModifyArgs(method = "build", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderPipeline;<init>"))
    private void ringworld$declarePrivateUniforms(Args args) {
        var material = RingShaderContract.pairedMaterial(args.get(1), args.get(2));
        if (material == null) return;
        args.set(1, material);
        args.set(2, material);
        List<RenderPipeline.UniformDescription> uniforms = args.get(5);
        var adapted = new ArrayList<>(uniforms);
        adapted.add(new RenderPipeline.UniformDescription(RingShaderContract.UNIFORM, UniformType.UNIFORM_BUFFER));
        args.set(5, List.copyOf(adapted));
        if (material.getPath().equals("core/terrain")) {
            List<String> samplers = args.get(4);
            var textures = new ArrayList<>(samplers);
            textures.add("RingWorldSurface");
            args.set(4, List.copyOf(textures));
        }
    }
}
