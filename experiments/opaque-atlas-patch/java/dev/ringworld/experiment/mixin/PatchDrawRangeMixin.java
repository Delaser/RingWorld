package dev.ringworld.experiment.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlRenderPipeline;

import dev.ringworld.experiment.PatchMaterial;

import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.lang.ref.WeakReference;

/** Experimental Iris/OpenGL bridge. Iris has bound its program before this draw. */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
abstract class PatchDrawRangeMixin {
    @Unique private WeakReference<GlProgram> ringworld$lastProgram = new WeakReference<>(null);
    @Unique private int ringworld$farLocation;

    @WrapMethod(method = "drawFromBuffers")
    private void ringworld$patchRange(
            @Coerce Object pass,
            int baseVertex,
            int firstIndex,
            int drawCount,
            IndexType index,
            GlRenderPipeline pipeline,
            int instanceCount,
            int firstInstance,
            Operation<Void> draw) {
        if (dev.ringworld.experiment.NativeAtlasTarget.ENABLED
                && pipeline.info()
                        == dev.ringworld.client.render.RingSurfaceTextureRenderer.pipeline()) {
            int original = dev.ringworld.experiment.NativeAtlasTarget.bind();
            try {
                draw.call(
                        pass,
                        baseVertex,
                        firstIndex,
                        drawCount,
                        index,
                        pipeline,
                        instanceCount,
                        firstInstance);
            } finally {
                if (original != 0) dev.ringworld.experiment.NativeAtlasTarget.attach(original);
            }
            return;
        }
        if (!PatchMaterial.ID.equals(pipeline.info().getLocation())) {
            draw.call(
                    pass,
                    baseVertex,
                    firstIndex,
                    drawCount,
                    index,
                    pipeline,
                    instanceCount,
                    firstInstance);
            return;
        }
        GlProgram program = pipeline.program();
        if (ringworld$lastProgram.get() != program) {
            ringworld$lastProgram = new WeakReference<>(program);
            ringworld$farLocation = GL20.glGetUniformLocation(program.getProgramId(), "far");
        }
        if (ringworld$farLocation < 0) {
            draw.call(
                    pass,
                    baseVertex,
                    firstIndex,
                    drawCount,
                    index,
                    pipeline,
                    instanceCount,
                    firstInstance);
            return;
        }
        // Another mod or the pack can set a different value. Restore the actual binding.
        float previous = GL20.glGetUniformf(program.getProgramId(), ringworld$farLocation);
        GL20.glUniform1f(ringworld$farLocation, Math.max(previous, PatchMaterial.opticalRange));
        try {
            draw.call(
                    pass,
                    baseVertex,
                    firstIndex,
                    drawCount,
                    index,
                    pipeline,
                    instanceCount,
                    firstInstance);
        } finally {
            GL20.glUniform1f(ringworld$farLocation, previous);
        }
    }
}
