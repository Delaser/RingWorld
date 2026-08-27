package dev.ringworld.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ringworld.client.RingMinecraftClientAccess;
import dev.ringworld.world.RingSurfaceMesh;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** 26.2 GPU ABI calls isolated from the shared surface/atlas implementation. */
public final class RingSurfaceGpu {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("ringworld", "pipeline/textured_ring_surface"))
            .withVertexShader(Identifier.fromNamespaceAndPath("ringworld", "core/ring_surface"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("ringworld", "core/ring_surface"))
            // GUI_TEXTURED_SNIPPET already owns Globals, MATRICES_PROJECTION and Sampler0.
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();

    private RingSurfaceGpu() { }
    public static RenderPipeline pipeline() { return PIPELINE; }
    public static GpuBuffer createVertexBuffer(RingSurfaceMesh.Mesh mesh) {
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        try (ByteBufferBuilder allocator = ByteBufferBuilder.exactlySized(mesh.vertexCount() * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(allocator, PrimitiveTopology.TRIANGLES, format);
            mesh.emitTriangles((x, y, z, u, v) -> builder.addVertex(x, y, z).setUv(u, v).setColor(0xFFFFFFFF));
            try (MeshData built = builder.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> "RingWorld textured surface mesh", GpuBuffer.USAGE_VERTEX, built.vertexBuffer());
            }
        }
    }
    public static GpuTexture createSurfaceTexture(int width, int height, int mipLevels) {
        return RenderSystem.getDevice().createTexture("RingWorld canonical surface atlas",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, width, height, 1, mipLevels);
    }
    public static void uploadSurfaceTexture(GpuTexture texture, NativeImage[] levels) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        for (int level = 0; level < levels.length; level++) encoder.writeToTexture(texture, levels[level], level, 0, 0, 0);
        encoder.submit();
    }
    public static void draw(Minecraft client, GpuBuffer vertexBuffer, int vertexCount,
                            GpuTextureView current, GpuTextureView previous, GpuBufferSlice transforms) {
        GpuTextureView color = RingMinecraftClientAccess.mainRenderTarget(client).getColorTextureView();
        GpuTextureView depth = RingMinecraftClientAccess.mainRenderTarget(client).getDepthTextureView();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(() -> "RingWorld textured surface", color, Optional.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE); RenderSystem.bindDefaultUniforms(pass); pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", current, RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, true));
            pass.bindTexture("Sampler1", previous, RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, true));
            pass.bindTexture("Sampler2", client.gameRenderer.levelLightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setVertexBuffer(0, vertexBuffer.slice());
            // 26.2 orders counts first, then offsets: vertices, instances,
            // first vertex, first instance (the latter must be zero on macOS).
            pass.draw(vertexCount, 1, 0, 0);
        }
        encoder.submit();
    }
    public static void writeBuffer(GpuBufferSlice target, ByteBuffer data) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder(); encoder.writeToBuffer(target, data); encoder.submit();
    }
}
