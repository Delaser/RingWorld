package dev.ringworld.client.mixin.compat;

import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ExtendedShader.class, remap = false)
public interface IrisPackTargetAccess {
    @Accessor("writingToAfterTranslucent")
    GlFramebuffer ringworld$litFramebuffer();

    @Accessor("writingToBeforeTranslucent")
    GlFramebuffer ringworld$opaqueFramebuffer();
}
