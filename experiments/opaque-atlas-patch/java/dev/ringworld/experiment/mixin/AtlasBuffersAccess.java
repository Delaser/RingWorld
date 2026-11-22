package dev.ringworld.experiment.mixin;

import com.mojang.blaze3d.textures.GpuTextureView;

import dev.ringworld.client.render.RingSurfaceTextureRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RingSurfaceTextureRenderer.class)
public interface AtlasBuffersAccess {
    @Accessor("wallTextureView")
    static GpuTextureView walls() {
        throw new AssertionError();
    }
}
