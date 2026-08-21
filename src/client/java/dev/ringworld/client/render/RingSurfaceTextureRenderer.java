package dev.ringworld.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ringworld.world.RingGeometry;
import net.minecraft.world.phys.Vec3;

/**
 * Temporarily disabled for the 1.21.1 backport.
 *
 * The original renderer targets Minecraft's newer RenderPipeline/GpuTexture
 * rendering backend. Re-port this against the 1.21.1 ShaderInstance /
 * VertexBuffer / texture APIs after initial boot compatibility is established.
 */
public final class RingSurfaceTextureRenderer {
    private RingSurfaceTextureRenderer() { }

    public static void render(PoseStack matrices, RingGeometry geometry, Vec3 camera, float alpha) {
        // Disabled during initial 1.21.1 backport.
    }

    public static void clear() {
    }

    public static boolean sessionCleared() {
        return true;
    }
}