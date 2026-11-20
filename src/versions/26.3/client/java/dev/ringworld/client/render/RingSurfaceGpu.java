package dev.ringworld.client.render;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.ringworld.client.RingMinecraftClientAccess;
import dev.ringworld.world.RingSurfaceMesh;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** 26.3 GPU ABI calls isolated from the shared surface/atlas implementation. */
public final class RingSurfaceGpu {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("ringworld", "pipeline/textured_ring_surface"))
            .withVertexShader(Identifier.fromNamespaceAndPath("ringworld", "core/ring_surface"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("ringworld", "core/ring_surface"))
            .withShaderDefine("RINGWORLD_REVERSED_DEPTH")
            // MATRICES_PROJECTION already contains DynamicTransforms as well
            // as Projection. Adding DYNAMIC_TRANSFORMS duplicates that name.
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
            .withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("Sampler3", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build())
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();

    private RingSurfaceGpu() { }
    public static RenderPipeline pipeline() { return PIPELINE; }
    /** Reversed far clip boundary; Metal/Vulkan and OpenGL can use different NDC ranges. */
    public static float farBackgroundDepth() {
        return RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 0.0001F : -0.9999F;
    }
    /** CPU-only packing. The returned native storage belongs to the build job. */
    public static PackedMesh packMesh(RingSurfaceMesh.Mesh mesh, int vertexArgb) {
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        ByteBufferBuilder allocator = ByteBufferBuilder.exactlySized(
                Math.multiplyExact(mesh.vertexCount(), format.getVertexSize()));
        try {
            BufferBuilder builder = new BufferBuilder(allocator, PrimitiveTopology.TRIANGLES, format);
            mesh.emitTriangles(new RingSurfaceMesh.VertexConsumer() {
                private int color = vertexArgb;
                @Override public void sideColor(int rgb) { color = rgb < 0 ? vertexArgb : 0xFF000000 | rgb; }
                @Override public void vertex(float x, float y, float z, float u, float v) {
                    builder.addVertex(x, y, z).setUv(u, v).setColor(color);
                }
            });
            return new PackedMesh(allocator, builder.buildOrThrow(), mesh.vertexCount(), mesh.layerOffsets());
        } catch (RuntimeException | Error exception) {
            allocator.close();
            throw exception;
        }
    }

    record MeshPage(GpuBuffer buffer) implements AutoCloseable {
        @Override public void close() { buffer.close(); }
    }

    static MeshPage uploadPage(ByteBuffer data) {
        return new MeshPage(RenderSystem.getDevice().createBuffer(
                () -> "RingWorld surface triangles", GpuBuffer.USAGE_VERTEX, data));
    }

    public record PackedMesh(ByteBufferBuilder allocator, MeshData data,
                             int vertexCount, int[] layerOffsets) implements AutoCloseable {
        @Override public void close() {
            try { data.close(); } finally { allocator.close(); }
        }
    }

    public static GpuTexture createSurfaceTexture(int width, int height, int mipLevels) {
        return RenderSystem.getDevice().createTexture("RingWorld canonical surface atlas",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, width, height, 1, mipLevels);
    }
    public static int uploadRows(GpuTexture target, NativeImage[] levels, dev.ringworld.world.RingSurfaceRowUpdates.Cursor cursor) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        int texels = cursor.transferNext(512 * 1024, (mip, row, count) -> {
            NativeImage image = levels[mip];
            int width = image.getWidth();
            int stride = width * Integer.BYTES;
            var bytes = image.getPixelBytes().slice(row * stride, count * stride);
            encoder.writeToTexture(target, bytes, mip, 0, 0, row, width, count);
        });
        encoder.submit();
        return texels;
    }

    public static void uploadSurfaceTexture(GpuTexture texture, NativeImage[] levels) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        for (int level = 0; level < levels.length; level++) encoder.writeToTexture(texture, levels[level], level, 0, 0, 0);
        encoder.submit();
    }
    public static void draw(Minecraft client, RingSurfaceBuffers mesh, int vertexCount, int[] offsets, int first, int end,
                            GpuTextureView current, GpuTextureView previous, GpuTextureView walls, GpuBufferSlice transforms) {
        GpuTextureView color = RingMinecraftClientAccess.mainRenderTarget(client).getColorTextureView();
        GpuTextureView depth = RingMinecraftClientAccess.mainRenderTarget(client).getDepthTextureView();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(() -> "RingWorld textured surface", color, Optional.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE)); RenderSystem.bindDefaultUniforms(pass); pass.setUniform("DynamicTransforms", transforms);
            pass.setUniform("Sampler0", current, RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, true));
            pass.setUniform("Sampler1", previous, RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, true));
            pass.setUniform("Sampler2", client.gameRenderer.levelLightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("Sampler3", walls, RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, true));
            if (offsets == null || first == end) {
                drawRange(pass, mesh, 0, vertexCount);
            } else if (first < end) {
                drawRange(pass, mesh, 0, offsets[0]);
                drawRange(pass, mesh, offsets[first], offsets[end]);
            } else {
                drawRange(pass, mesh, 0, offsets[end]);
                drawRange(pass, mesh, offsets[first], vertexCount);
            }
        }
        encoder.submit();
    }
    private static void drawRange(RenderPass pass, RingSurfaceBuffers mesh, int first, int end) {
        while (first < end) {
            int local = first % RingSurfaceBuffers.PAGE_VERTICES;
            int count = Math.min(end - first, RingSurfaceBuffers.PAGE_VERTICES - local);
            var page = mesh.pages[first / RingSurfaceBuffers.PAGE_VERTICES].buffer();
            pass.setVertexBuffer(0, page.slice());
            pass.draw(count, 1, local, 0);
            first += count;
        }
    }

    public static void writeBuffer(GpuBufferSlice target, ByteBuffer data) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder(); encoder.writeToBuffer(target, data); encoder.submit();
    }
}
