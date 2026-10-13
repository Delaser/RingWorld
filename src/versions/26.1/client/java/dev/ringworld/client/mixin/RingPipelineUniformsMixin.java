package dev.ringworld.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import dev.ringworld.client.render.RingShaderContract;
import java.util.Optional;
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
    @Shadow private Optional<Identifier> vertexShader;
    @Unique private boolean ringworld$bindingAdded;
    @Inject(method = "build", at = @At("HEAD"))
    private void ringworld$declarePrivateUniforms(CallbackInfoReturnable<RenderPipeline> cir) {
        if (!ringworld$bindingAdded && RingShaderContract.usesRingUniforms(vertexShader.orElse(null))) {
            ((RenderPipeline.Builder)(Object)this).withUniform(RingShaderContract.UNIFORM, UniformType.UNIFORM_BUFFER);
            ringworld$bindingAdded = true;
        }
    }
}
