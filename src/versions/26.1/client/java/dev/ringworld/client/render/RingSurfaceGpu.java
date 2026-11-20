package dev.ringworld.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ringworld.client.RingMinecraftClientAccess;
import dev.ringworld.world.RingSurfaceMesh;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** 26.1 GPU ABI calls isolated from the shared surface/atlas implementation. */
public final class RingSurfaceGpu {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("ringworld", "pipeline/textured_ring_surface"))
            .withVertexShader(Identifier.fromNamespaceAndPath("ringworld", "core/ring_surface"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("ringworld", "core/ring_surface"))
            .withSampler("Sampler1").withSampler("Sampler2").withSampler("Sampler3")
            .withUniform("Fog", UniformType.UNIFORM_BUFFER).withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLES).build();

    private RingSurfaceGpu() { }
    public static RenderPipeline pipeline() { return PIPELINE; }
    public static float farBackgroundDepth() { return 0.9999F; }

    /** CPU-only packing. The returned native storage belongs to the build job. */
    public static PackedMesh packMesh(RingSurfaceMesh.Mesh mesh, int vertexArgb) {
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        ByteBufferBuilder allocator = ByteBufferBuilder.exactlySized(
                Math.multiplyExact(mesh.vertexCount(), format.getVertexSize()));
        try {
            BufferBuilder builder = new BufferBuilder(allocator, VertexFormat.Mode.TRIANGLES, format);
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
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8,
                width, height, 1, mipLevels);
    }

    public static int uploadRows(GpuTexture target, NativeImage[] levels, dev.ringworld.world.RingSurfaceRowUpdates.Cursor cursor) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        int texels = cursor.transferNext(512 * 1024, (mip, row, count) -> {
            NativeImage image = levels[mip];
            int width = image.getWidth();
            encoder.writeToTexture(target, image, mip, 0, 0, row, width, count, 0, row);
        });
        return texels;
    }

    public static void uploadSurfaceTexture(GpuTexture texture, NativeImage[] levels) {
        for (int level = 0; level < levels.length; level++) {
            int width = levels[level].getWidth(), height = levels[level].getHeight();
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, levels[level], level, 0, 0, 0,
                    width, height, 0, 0);
        }
    }

    public static void draw(Minecraft client, RingSurfaceBuffers mesh, int vertexCount, int[] offsets, int first, int end,
                            GpuTextureView current, GpuTextureView previous, GpuTextureView walls, GpuBufferSlice transforms) {
        GpuTextureView color = RingMinecraftClientAccess.mainRenderTarget(client).getColorTextureView();
        GpuTextureView depth = RingMinecraftClientAccess.mainRenderTarget(client).getDepthTextureView();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "RingWorld textured surface", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE); RenderSystem.bindDefaultUniforms(pass); pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", current, RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, true));
            pass.bindTexture("Sampler1", previous, RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, true));
            pass.bindTexture("Sampler2", client.gameRenderer.levelLightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.bindTexture("Sampler3", walls, RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, true));
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
    }

    private static void drawRange(RenderPass pass, RingSurfaceBuffers mesh, int first, int end) {
        while (first < end) {
            int local = first % RingSurfaceBuffers.PAGE_VERTICES;
            int count = Math.min(end - first, RingSurfaceBuffers.PAGE_VERTICES - local);
            var page = mesh.pages[first / RingSurfaceBuffers.PAGE_VERTICES].buffer();
            pass.setVertexBuffer(0, page);
            pass.draw(local, count);
            first += count;
        }
    }

    public static void writeBuffer(GpuBufferSlice target, ByteBuffer data) {
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(target, data);
    }
}
