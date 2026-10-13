package dev.ringworld.client.render;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;

/** Render-thread-owned buffer shared by adapters; never changes vanilla Globals. */
public final class RingRenderUniforms {
    private static GpuBuffer current;
    private RingRenderUniforms() { }
    public static void publish(GpuBuffer buffer) { current = buffer; }
    public static void clear(GpuBuffer buffer) { if (current == buffer) current = null; }
    public static GpuBuffer buffer() { return current; }
    public static void bind(RenderPass pass) {
        if (current != null) pass.setUniform(RingShaderContract.UNIFORM, current);
    }
}
