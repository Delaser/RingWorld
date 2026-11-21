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
import com.mojang.blaze3d.opengl.GlTexture;
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
            var data = builder.buildOrThrow();
            try {
                int[] unique = RingSurfacePageIndices.pack(data.vertexBuffer(), mesh.vertexCount(), format.getVertexSize());
                return new PackedMesh(allocator, data, mesh.vertexCount(), mesh.layerOffsets(), unique);
            } catch (RuntimeException | Error ex) {
                data.close();
                throw ex;
            }
        } catch (RuntimeException | Error exception) {
            allocator.close();
            throw exception;
        }
    }

    record MeshPage(GpuBuffer buffer, GpuBuffer indices) implements AutoCloseable {
        @Override public void close() {
            try { buffer.close(); } finally { if (indices != null) indices.close(); }
        }
    }

    static MeshPage uploadPage(ByteBuffer data, ByteBuffer indices) {
        var device = RenderSystem.getDevice();
        var vertices = device.createBuffer(() -> "RingWorld surface vertices", GpuBuffer.USAGE_VERTEX, data);
        try {
            return new MeshPage(vertices, indices == null ? null : device.createBuffer(
                    () -> "RingWorld surface indices", GpuBuffer.USAGE_INDEX, indices));
        } catch (RuntimeException | Error ex) {
            vertices.close();
            throw ex;
        }
    }

    public record PackedMesh(ByteBufferBuilder allocator, MeshData data,
                             int vertexCount, int[] layerOffsets, int[] uniqueCounts) implements AutoCloseable {
        @Override public void close() {
            try { data.close(); } finally { allocator.close(); }
        }
    }

    public static GpuTexture createSurfaceTexture(int width, int height, int mipLevels) {
        return RenderSystem.getDevice().createTexture("RingWorld canonical surface atlas",
                GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8,
                width, height, 1, mipLevels);
    }

    static boolean copySurfaceTexture(GpuTexture source, GpuTexture target, int levels) {
        if (source instanceof GlTexture from && target instanceof GlTexture to) {
            return RingSurfaceGlCopy.copy(from.glId(), to.glId(), source.getWidth(0), source.getHeight(0), levels);
        }
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        for (int mip = 0; mip < levels; mip++) {
            encoder.copyTextureToTexture(source, target, mip, 0, 0, 0, 0, source.getWidth(mip), source.getHeight(mip));
        }
        return true;
    }

    public static int uploadRows(GpuTexture target, NativeImage[] levels, dev.ringworld.world.RingSurfaceRowUpdates.Cursor cursor) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        int texels = cursor.transferNext(dev.ringworld.world.RingSurfaceRowUpdates.UPLOAD_BUDGET, (mip, row, count) -> {
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
            var page = mesh.pages[first / RingSurfaceBuffers.PAGE_VERTICES];
            pass.setVertexBuffer(0, page.buffer());
            if (page.indices() == null) {
                pass.draw(local, count);
            } else {
                pass.setIndexBuffer(page.indices(), VertexFormat.IndexType.SHORT);
                pass.drawIndexed(0, local, count, 1);
            }
            first += count;
        }
    }

    public static void writeBuffer(GpuBufferSlice target, ByteBuffer data) {
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(target, data);
    }
}
