package dev.ringworld.client.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import dev.ringworld.client.render.RingShaderContract;
import java.util.Map;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds only our private binding to materials which consume it. */
@Mixin(RenderPipeline.Builder.class)
abstract class RingPipelineUniformsMixin {
    @Shadow @Final private Map<ShaderType, Identifier> shaders;
    @Unique private boolean ringworld$bindingAdded;
    @Inject(method = "build", at = @At("HEAD"))
    private void ringworld$declarePrivateUniforms(CallbackInfoReturnable<RenderPipeline> cir) {
        var builder = (RenderPipeline.Builder)(Object)this;
        var vertex = shaders.get(ShaderType.VERTEX);
        var fragment = shaders.get(ShaderType.FRAGMENT);
        if (vertex != null) builder.withVertexShader(RingShaderContract.materialShader(vertex));
        if (fragment != null) builder.withFragmentShader(RingShaderContract.materialShader(fragment));
        if (!ringworld$bindingAdded && RingShaderContract.usesRingUniforms(shaders.get(ShaderType.VERTEX))) {
            ((RenderPipeline.Builder)(Object)this).withBindGroupLayout(BindGroupLayout.builder().withUniform(RingShaderContract.UNIFORM, UniformType.UNIFORM_BUFFER).build());
            ringworld$bindingAdded = true;
        }
    }
}
