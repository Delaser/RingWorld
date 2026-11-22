package dev.ringworld.experiment;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

import dev.ringworld.client.render.RingSkyView;
import dev.ringworld.experiment.mixin.PackTargetAccess;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ShaderKey;

/**
 * Trial on Iris' OpenGL backend. Borrow only its opaque color destination for the stock Atlas draw.
 */
public final class NativeAtlasTarget {
    public static final boolean ENABLED = Boolean.getBoolean("ringworld.nativeAtlasTrial");
    private static boolean reported;

    private NativeAtlasTarget() {}

    public static int bind() {
        var view = RingSkyView.current();
        if (!ENABLED
                || view == null
                || !view.primary()
                || view.world() == null
                || !(Iris.getPipelineManager().getPipelineNullable()
                        instanceof IrisRenderingPipeline iris)
                || !iris.isBeforeTranslucent) return 0;
        var program = iris.getShaderMap().getShader(ShaderKey.MOVING_BLOCK);
        if (!(program instanceof PackTargetAccess target)) return 0;
        var buffer = target.ringworld$opaqueFramebuffer();
        if (buffer == null) return 0;
        int color = buffer.getColorAttachment(0);
        if (color == 0 || glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == 0) return 0;
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
        var main =
                dev.ringworld.client.RingMinecraftClientAccess.mainRenderTarget(
                        net.minecraft.client.Minecraft.getInstance());
        if (!(main.getColorTexture() instanceof com.mojang.blaze3d.opengl.GlTexture mainColor)
                || original != mainColor.glId()
                || original == color) return 0;
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
            System.out.println(
                    "NATIVE_ATLAS_TARGET stock mesh, water and original depth paired with opaque"
                            + " target");
        }
        return original;
    }

    public static void attach(int texture) {
        glFramebufferTexture2D(
                GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
    }
}
