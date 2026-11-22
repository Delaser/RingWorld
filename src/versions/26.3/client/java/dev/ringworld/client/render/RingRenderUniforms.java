package dev.ringworld.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** Render-thread-owned buffer shared by adapters; never changes vanilla Globals. */
public final class RingRenderUniforms {
    public interface SkyViewSource {
        GpuBuffer ringworld$skyViewBuffer(ClientLevel world, RingSkyView view);
    }

    private static GpuBuffer current;
    private RingRenderUniforms() { }
    public static void publish(GpuBuffer buffer) { current = buffer; }
    public static void clear(GpuBuffer buffer) { if (current == buffer) current = null; }
    public static GpuBuffer buffer() { return current; }
    public static void bind(RenderPass pass) {
        if (current == null) return;
        pass.setUniform(RingShaderContract.UNIFORM, current);
        var surface = RingSurfaceTextureRenderer.waterSurface();
        // A valid binding is required even in a vanilla world, where the shader never samples it.
        if (surface == null) surface = Minecraft.getInstance().gameRenderer.levelLightmap();
        pass.setUniform("RingWorldSurface", surface, RenderSystem.getSamplerCache().getSampler(
                AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, true));
    }
}
