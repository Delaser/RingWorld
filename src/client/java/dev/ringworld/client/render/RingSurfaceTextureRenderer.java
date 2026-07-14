package dev.ringworld.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ringworld.RingWorldMod;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTerrainAtlas;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Static, texture-backed representation of the complete generated surface.
 *
 * <p>The mesh lives at the ring's real physical radius. Its texture U axis is
 * canonical circumference X and repeats exactly at X=0, while V is the finite
 * width. Player movement changes one model transform; it never rebuilds the
 * mesh or allocates distant vanilla chunk sections.</p>
 */
public final class RingSurfaceTextureRenderer {
    private static final Identifier TEXTURE_ID = Identifier.of("ringworld", "dynamic/ring_surface");
    private static final int MAX_TEXTURE_COLUMNS = 4_096;
    private static final int MAX_TEXTURE_ROWS = 1_024;
    private static final int MAX_CIRCUMFERENCE_SEGMENTS = 512;
    private static final int MAX_WIDTH_SEGMENTS = 128;
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("ringworld", "pipeline/textured_ring_surface"))
                    .withCull(false)
                    .withDepthWrite(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR,
                            VertexFormat.DrawMode.TRIANGLES)
                    .build());

    private static GpuBuffer vertexBuffer;
    private static int vertexCount;
    private static NativeImageBackedTexture surfaceTexture;
    private static RingGeometry bufferedGeometry;
    private static long bufferedWorldHash;
    private static int bufferedAtlasRevision = -1;
    private static int textureColumns;
    private static int textureRows;

    private RingSurfaceTextureRenderer() { }

    public static void render(MatrixStack matrices, RingGeometry geometry, Vec3d camera,
                              float brightness, float alpha) {
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        ensureResources(geometry, atlas);
        if (vertexBuffer == null || surfaceTexture == null || vertexCount == 0) return;

        double cameraAngle = Math.PI * 2.0 * geometry.wrapX(camera.x)
                / geometry.circumferenceBlocks();
        double cameraRadius = geometry.radius() + RingGeometry.SURFACE_Y - camera.y;

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(matrices.peek().getPositionMatrix());
        // Model vertices use global ring coordinates. Rotate the entire static
        // object into the camera's tangent frame, then move its centre to the
        // same local point used by curved real chunk vertices.
        modelView.translate(0.0F, (float)cameraRadius, (float)-camera.z);
        modelView.rotateZ((float)-cameraAngle);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().write(
                modelView, new Vector4f(brightness, brightness, brightness, alpha),
                new Vector3f(), new Matrix4f());

        MinecraftClient client = MinecraftClient.getInstance();
        GpuTextureView color = client.getFramebuffer().getColorAttachmentView();
        GpuTextureView depth = client.getFramebuffer().getDepthAttachmentView();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "RingWorld textured surface", color, OptionalInt.empty(),
                depth, OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", surfaceTexture.getGlTextureView(), surfaceTexture.getSampler());
            pass.setVertexBuffer(0, vertexBuffer);
            pass.draw(0, vertexCount);
        }
        modelView.popMatrix();
    }

    private static void ensureResources(RingGeometry geometry, RingTerrainAtlas atlas) {
        if (atlas == null || !atlas.isComplete()) return;
        int revision = ClientRingState.terrainAtlasRevision();
        if (geometry.equals(bufferedGeometry)
                && atlas.worldHash() == bufferedWorldHash
                && revision == bufferedAtlasRevision
                && vertexBuffer != null && surfaceTexture != null) return;

        buildTexture(atlas);
        buildMesh(geometry, atlas);
        bufferedGeometry = geometry;
        bufferedWorldHash = atlas.worldHash();
        bufferedAtlasRevision = revision;
        RingWorldMod.LOGGER.info(
                "Textured ring surface ready: {}x{} source atlas expanded to {}x{} GPU texture, "
                        + "{} vertices, exact radius {}",
                atlas.columns(), atlas.rows(), textureColumns, textureRows,
                vertexCount, geometry.radius());
    }

    private static void buildTexture(RingTerrainAtlas atlas) {
        RingGeometry geometry = atlas.geometry();
        textureColumns = Math.min(geometry.circumferenceBlocks(), MAX_TEXTURE_COLUMNS);
        textureRows = Math.min(geometry.widthBlocks(), MAX_TEXTURE_ROWS);
        NativeImage image = new NativeImage(textureColumns, textureRows, false);
        for (int row = 0; row < textureRows; row++) {
            double z = geometry.minWidthZ()
                    + (row + 0.5) * geometry.widthBlocks() / textureRows;
            for (int column = 0; column < textureColumns; column++) {
                double x = (column + 0.5) * geometry.circumferenceBlocks() / textureColumns;
                int color = atlas.sample(x, z).color();
                image.setColorArgb(column, row, color < 0 ? 0 : 0xFF000000 | color);
            }
        }
        NativeImageBackedTexture replacement = new NativeImageBackedTexture(
                () -> "RingWorld canonical surface atlas", image);
        MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE_ID, replacement);
        surfaceTexture = replacement;
    }

    private static void buildMesh(RingGeometry geometry, RingTerrainAtlas atlas) {
        int segments = Math.max(1, Math.min(atlas.columns(), MAX_CIRCUMFERENCE_SEGMENTS));
        int bands = Math.max(1, Math.min(atlas.rows(), MAX_WIDTH_SEGMENTS));
        int count = segments * bands * 6;
        VertexFormat format = VertexFormats.POSITION_TEXTURE_COLOR;
        try (BufferAllocator allocator = BufferAllocator.fixedSized(count * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(
                    allocator, VertexFormat.DrawMode.TRIANGLES, format);
            for (int segment = 0; segment < segments; segment++) {
                double x0 = (double)segment * geometry.circumferenceBlocks() / segments;
                double x1 = (double)(segment + 1) * geometry.circumferenceBlocks() / segments;
                float u0 = (float)(x0 / geometry.circumferenceBlocks());
                float u1 = (float)(x1 / geometry.circumferenceBlocks());
                for (int band = 0; band < bands; band++) {
                    double z0 = geometry.minWidthZ() + (double)band * geometry.widthBlocks() / bands;
                    double z1 = geometry.minWidthZ() + (double)(band + 1) * geometry.widthBlocks() / bands;
                    float v0 = (float)((z0 - geometry.minWidthZ()) / geometry.widthBlocks());
                    float v1 = (float)((z1 - geometry.minWidthZ()) / geometry.widthBlocks());
                    double h00 = atlas.sample(x0, z0).height();
                    double h10 = atlas.sample(x1, z0).height();
                    double h11 = atlas.sample(x1, z1).height();
                    double h01 = atlas.sample(x0, z1).height();

                    vertex(builder, geometry, x0, z0, h00, u0, v0);
                    vertex(builder, geometry, x1, z0, h10, u1, v0);
                    vertex(builder, geometry, x1, z1, h11, u1, v1);
                    vertex(builder, geometry, x0, z0, h00, u0, v0);
                    vertex(builder, geometry, x1, z1, h11, u1, v1);
                    vertex(builder, geometry, x0, z1, h01, u0, v1);
                }
            }
            try (BuiltBuffer built = builder.end()) {
                GpuBuffer replacement = RenderSystem.getDevice().createBuffer(
                        () -> "RingWorld textured surface mesh", GpuBuffer.USAGE_VERTEX,
                        built.getBuffer());
                if (vertexBuffer != null) vertexBuffer.close();
                vertexBuffer = replacement;
            }
        }
        vertexCount = count;
    }

    private static void vertex(BufferBuilder builder, RingGeometry geometry,
                               double x, double z, double surfaceHeight,
                               float u, float v) {
        double angle = Math.PI * 2.0 * x / geometry.circumferenceBlocks();
        double radius = geometry.radius() + RingGeometry.SURFACE_Y - surfaceHeight;
        builder.vertex((float)(radius * Math.sin(angle)),
                        (float)(-radius * Math.cos(angle)), (float)z)
                .texture(u, v)
                .color(0xFFFFFFFF);
    }

    public static void clear() {
        if (vertexBuffer != null) vertexBuffer.close();
        vertexBuffer = null;
        vertexCount = 0;
        if (surfaceTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(TEXTURE_ID);
            surfaceTexture = null;
        }
        bufferedGeometry = null;
        bufferedWorldHash = 0L;
        bufferedAtlasRevision = -1;
    }
}
