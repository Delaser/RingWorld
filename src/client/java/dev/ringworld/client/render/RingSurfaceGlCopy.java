package dev.ringworld.client.render;

import org.lwjgl.opengl.GL30C;

/** The bundled GL encoder attaches mip zero for every copy. Attach the matching source and target levels. */
final class RingSurfaceGlCopy {
    private RingSurfaceGlCopy() {}

    static boolean copy(int source, int target, int width, int height, int levels) {
        int savedRead = GL30C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int savedDraw = GL30C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissor = GL30C.glIsEnabled(GL30C.GL_SCISSOR_TEST);
        boolean srgb = GL30C.glIsEnabled(GL30C.GL_FRAMEBUFFER_SRGB);
        int read = GL30C.glGenFramebuffers();
        int draw = GL30C.glGenFramebuffers();
        try {
            if (scissor) GL30C.glDisable(GL30C.GL_SCISSOR_TEST);
            if (srgb) GL30C.glDisable(GL30C.GL_FRAMEBUFFER_SRGB);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, read);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, draw);
            for (int mip = 0; mip < levels; mip++) {
                if (!attach(GL30C.GL_READ_FRAMEBUFFER, source, mip)
                        || !attach(GL30C.GL_DRAW_FRAMEBUFFER, target, mip)) return false;
                int w = Math.max(1, width >> mip);
                int h = Math.max(1, height >> mip);
                GL30C.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL30C.GL_COLOR_BUFFER_BIT, GL30C.GL_NEAREST);
            }
            return true;
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, savedRead);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, savedDraw);
            if (scissor) GL30C.glEnable(GL30C.GL_SCISSOR_TEST);
            if (srgb) GL30C.glEnable(GL30C.GL_FRAMEBUFFER_SRGB);
            GL30C.glDeleteFramebuffers(read);
            GL30C.glDeleteFramebuffers(draw);
        }
    }

    private static boolean attach(int binding, int texture, int mip) {
        GL30C.glFramebufferTexture2D(binding, GL30C.GL_COLOR_ATTACHMENT0, GL30C.GL_TEXTURE_2D, texture, mip);
        return GL30C.glCheckFramebufferStatus(binding) == GL30C.GL_FRAMEBUFFER_COMPLETE;
    }
}
