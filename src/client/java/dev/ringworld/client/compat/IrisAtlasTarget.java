package dev.ringworld.client.compat;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.ringworld.client.render.RingSkyView;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import dev.ringworld.client.mixin.compat.IrisPackTargetAccess;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ShaderKey;

/**
 * Draws the already shaded Atlas into the pack’s lit scene before translucent terrain.
 */
public final class IrisAtlasTarget {

    private static boolean reported;

    private IrisAtlasTarget() {}

    public static boolean active() {
        return Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline;
    }

    private static GlFramebuffer litBuffer(IrisRenderingPipeline iris) {
        var program = iris.getShaderMap().getShader(ShaderKey.TERRAIN_TRANSLUCENT);
        return program instanceof IrisPackTargetAccess target ? target.ringworld$litFramebuffer() : null;
    }

    private static Runnable pending;
    private static IrisRenderingPipeline owner;
    private static boolean drawingLit;

    public static boolean canDefer() {
        var view = RingSkyView.current();
        if (view == null || !view.primary() || view.world() == null || drawingLit ||
                !(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline iris)
                || !iris.isBeforeTranslucent) return false;
        var buffer = litBuffer(iris);
        return buffer != null && buffer.getColorAttachment(0) != 0;
    }

    public static void schedule(Runnable draw) {
        // The original sky projection and transform slice belong to this frame.
        owner = (IrisRenderingPipeline) Iris.getPipelineManager().getPipelineNullable();
        var projection = RenderSystem.getProjectionMatrixBuffer();
        var type = RenderSystem.getProjectionType();
        pending = () -> {
            var previousProjection = RenderSystem.getProjectionMatrixBuffer();
            var previousType = RenderSystem.getProjectionType();
            RenderSystem.setProjectionMatrix(projection, type);
            try {
                draw.run();
            } finally {
                RenderSystem.setProjectionMatrix(previousProjection, previousType);
            }
        };
    }

    public static void clear(Object pipeline) {
        if (pipeline == owner) {
            pending = null;
            owner = null;
        }
    }

    public static void drawLit(Object pipeline) {
        if (pipeline != owner || pending == null) return;
        var draw = pending;
        clear(pipeline);
        drawingLit = true;
        try {
            draw.run();
        } finally {
            drawingLit = false;
        }
    }

    public static long bind(int mainColor) {
        if (!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline iris))
            return 0;
        GlFramebuffer buffer;
        if (drawingLit) {
            // Physical attachment zero is the first output of THIS material. Iris
            // maps it to the pack’s own scene-color slot, including any ping-pong.
            buffer = litBuffer(iris);
        } else {
            var view = RingSkyView.current();
            if (view == null || !view.primary() || view.world() == null || !iris.isBeforeTranslucent)
                return 0;
            var program = iris.getShaderMap().getShader(ShaderKey.MOVING_BLOCK);
            if (!(program instanceof IrisPackTargetAccess target)) return 0;
            buffer = target.ringworld$opaqueFramebuffer();
        }
        if (buffer == null) return 0;
        int color = buffer.getColorAttachment(0);
        if (color == 0 || color == mainColor) return 0;
        int framebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        if (framebuffer == 0) return 0;
        if (glGetFramebufferAttachmentParameteri(
                        GL_DRAW_FRAMEBUFFER,
                        GL_COLOR_ATTACHMENT0,
                        GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)
                != GL_TEXTURE) return 0;
        int original =
                glGetFramebufferAttachmentParameteri(
                        GL_DRAW_FRAMEBUFFER,
                        GL_COLOR_ATTACHMENT0,
                        GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if (original != mainColor) return 0;
        if (glGetFramebufferAttachmentParameteri(
                        GL_DRAW_FRAMEBUFFER,
                        GL_COLOR_ATTACHMENT0,
                        GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL)
                != 0) return 0;
        attach(color);
        if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            attach(original);
            return 0;
        }
        if (!reported) {
            reported = true;
            com.mojang.logging.LogUtils.getLogger().info("RingWorld Atlas attached to the active shader pack (after deferred lighting: {})", drawingLit);
        }
        return ((long) framebuffer << 32) | Integer.toUnsignedLong(original);
    }

    // Other renderer hooks may have changed the binding during the draw.
    public static void restore(long saved) {
        int framebuffer = (int) (saved >>> 32);
        int bound = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        if (framebuffer != bound) glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
        attach((int) saved);
        if (framebuffer != bound) glBindFramebuffer(GL_DRAW_FRAMEBUFFER, bound);
    }

    private static void attach(int texture) {
        glFramebufferTexture2D(
                GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
    }
}
