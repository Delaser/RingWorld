package dev.ringworld.experiment.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.renderer.RenderPipelines;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderPipelines.class)
public interface BlockPipelineAccess {
    @Accessor("BLOCK_SNIPPET")
    static RenderPipeline.Snippet block() {
        throw new AssertionError();
    }
}
