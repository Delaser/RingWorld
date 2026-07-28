package dev.ringworld.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ringworld.RingWorldMod;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRenderProfile;
import dev.ringworld.world.RingSurfaceLod;
import dev.ringworld.world.RingTerrainAtlas;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Static, texture-backed representation of the complete generated surface.
 *
 * <p>The mesh lives at the ring's real physical radius. Its texture U axis is
 * canonical circumference X and repeats exactly at X=0, while V is the finite
 * width. Player movement changes one model transform; it never rebuilds the
 * mesh or allocates distant vanilla chunk sections.</p>
 */
public final class RingSurfaceTextureRenderer {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("ringworld", "pipeline/textured_ring_surface"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("ringworld", "core/ring_surface"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("ringworld", "core/ring_surface"))
                    .withSampler("Sampler2")
                    .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthWrite(false)
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR,
                            VertexFormat.Mode.TRIANGLES)
                    .build());

    private static GpuBuffer vertexBuffer;
    private static int vertexCount;
    private static GpuTexture surfaceTexture;
    private static GpuTextureView surfaceTextureView;
    private static RingGeometry bufferedGeometry;
    private static long bufferedWorldHash;
    private static int bufferedAtlasRevision = -1;
    private static long projectionDiagnosticWorldHash = Long.MIN_VALUE;
    private static int textureColumns;
    private static int textureRows;

    private RingSurfaceTextureRenderer() { }

    public static void render(PoseStack matrices, RingGeometry geometry, Vec3 camera,
                              float alpha) {
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        // Never fall through to buffers left by another world while the
        // current world's atlas is absent or still being generated. Session
        // callbacks normally destroy those resources; this guard makes the
        // world-hash boundary authoritative even if a callback is missed.
        if (atlas == null || !atlas.isComplete()) return;
        ensureResources(geometry, atlas);
        if (vertexBuffer == null || surfaceTexture == null || vertexCount == 0) return;

        Minecraft client = Minecraft.getInstance();
        double cameraAngle = Math.PI * 2.0 * geometry.wrapX(camera.x)
                / geometry.circumferenceBlocks();
        double cameraRadius = geometry.physicalRadiusAt(camera.y);
        if (projectionDiagnosticWorldHash != atlas.worldHash()) {
            projectionDiagnosticWorldHash = atlas.worldHash();
            double oppositeSurfaceDistance =
                    geometry.oppositeReferenceSurfaceDistance(camera.y);
            double oppositeWidthEdgeDistance =
                    geometry.maximumReferenceSurfaceDistance(camera.y, camera.z);
            float vanillaFarPlane = client.gameRenderer.getDepthFar();
            RingWorldMod.LOGGER.info(
                    "Ring proxy projection: level far plane={} blocks, radial-up opposite "
                            + "surface={} blocks, far width edge={} blocks; "
                            + "sky-depth compression required={}",
                    String.format(java.util.Locale.ROOT, "%.2f", vanillaFarPlane),
                    String.format(java.util.Locale.ROOT, "%.2f", oppositeSurfaceDistance),
                    String.format(java.util.Locale.ROOT, "%.2f", oppositeWidthEdgeDistance),
                    oppositeWidthEdgeDistance > vanillaFarPlane);
        }

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(matrices.last().pose());
        // Model vertices use global ring coordinates. Rotate the entire static
        // object into the camera's tangent frame, then move its centre to the
        // same local point used by curved real chunk vertices.
        modelView.translate(0.0F, (float)cameraRadius, (float)-camera.z);
        modelView.rotateZ((float)-cameraAngle);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                modelView,
                new Vector4f(1.0F, alpha, 0.0F, 0.0F),
                new Vector3f((float)cameraAngle, (float)camera.z, 0.0F),
                new Matrix4f());

        GpuTextureView color = client.getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = client.getMainRenderTarget().getDepthTextureView();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "RingWorld textured surface", color, OptionalInt.empty(),
                depth, OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", surfaceTextureView, RenderSystem.getSamplerCache().getSampler(
                    AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, true));
            // The atlas contains surface albedo, while real chunk vertices are
            // multiplied by Minecraft's live lightmap. Sampling the same
            // full-skylight/no-block-light texel keeps exposed proxy terrain
            // synchronized with time, weather, gamma, lightning, darkness,
            // and night vision instead of applying a hand-tuned grey scalar.
            pass.bindTexture("Sampler2",
                    client.gameRenderer.lightTexture().getTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
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
                && vertexBuffer != null && surfaceTexture != null
                && surfaceTextureView != null) return;

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
        RingRenderProfile profile = RingRenderProfile.create(geometry, 16.0);
        textureColumns = profile.textureColumns();
        textureRows = profile.textureRows();
        int[] pixels = new int[textureColumns * textureRows];
        float[] heights = new float[pixels.length];
        double spacingX = (double)geometry.circumferenceBlocks() / textureColumns;
        double spacingZ = (double)geometry.widthBlocks() / textureRows;
        for (int row = 0; row < textureRows; row++) {
            double z = geometry.minWidthZ()
                    + (row + 0.5) * spacingZ;
            for (int column = 0; column < textureColumns; column++) {
                double x = (column + 0.5) * spacingX;
                RingTerrainAtlas.SurfaceSample sample = atlas.sample(x, z);
                int index = row * textureColumns + column;
                heights[index] = (float)sample.height();
                pixels[index] = sample.color();
            }
        }
        for (int row = 0; row < textureRows; row++) {
            int lowerRow = Math.max(0, row - 1);
            int upperRow = Math.min(textureRows - 1, row + 1);
            for (int column = 0; column < textureColumns; column++) {
                int leftColumn = Math.floorMod(column - 1, textureColumns);
                int rightColumn = Math.floorMod(column + 1, textureColumns);
                int index = row * textureColumns + column;
                int shaded = RingSurfaceLod.shadeSurfaceColor(
                        pixels[index], heights[index],
                        heights[row * textureColumns + leftColumn],
                        heights[row * textureColumns + rightColumn],
                        heights[lowerRow * textureColumns + column],
                        heights[upperRow * textureColumns + column],
                        spacingX, spacingZ);
                pixels[index] = 0xFF000000 | shaded;
            }
        }

        int mipLevels = mipLevels(textureColumns, textureRows);
        GpuTexture replacement = RenderSystem.getDevice().createTexture(
                "RingWorld canonical surface atlas",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, textureColumns, textureRows, 1, mipLevels);
        GpuTextureView replacementView = RenderSystem.getDevice().createTextureView(replacement);
        int[] levelPixels = pixels;
        int levelWidth = textureColumns;
        int levelHeight = textureRows;
        for (int level = 0; level < mipLevels; level++) {
            try (NativeImage image = new NativeImage(levelWidth, levelHeight, false)) {
                for (int y = 0; y < levelHeight; y++) {
                    for (int x = 0; x < levelWidth; x++) {
                        image.setPixel(x, y, levelPixels[y * levelWidth + x]);
                    }
                }
                RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                        replacement, image, level, 0, 0, 0,
                        levelWidth, levelHeight, 0, 0);
            }
            if (level + 1 < mipLevels) {
                levelPixels = RingSurfaceLod.buildNextMipArgb(
                        levelPixels, levelWidth, levelHeight);
                levelWidth = Math.max(1, levelWidth >> 1);
                levelHeight = Math.max(1, levelHeight >> 1);
            }
        }

        destroySurfaceTexture();
        surfaceTexture = replacement;
        surfaceTextureView = replacementView;
    }

    private static void buildMesh(RingGeometry geometry, RingTerrainAtlas atlas) {
        RingRenderProfile profile = RingRenderProfile.create(geometry, 16.0);
        int segments = Math.min(atlas.columns(), profile.circumferenceSegments());
        int bands = Math.min(atlas.rows(), profile.widthBands());
        int count = segments * bands * 6;
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        try (ByteBufferBuilder allocator = ByteBufferBuilder.exactlySized(count * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(
                    allocator, VertexFormat.Mode.TRIANGLES, format);
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
            try (MeshData built = builder.buildOrThrow()) {
                GpuBuffer replacement = RenderSystem.getDevice().createBuffer(
                        () -> "RingWorld textured surface mesh", GpuBuffer.USAGE_VERTEX,
                        built.vertexBuffer());
                if (vertexBuffer != null) vertexBuffer.close();
                vertexBuffer = replacement;
            }
        }
        vertexCount = count;
    }

    private static int mipLevels(int width, int height) {
        int levels = 1;
        // Minecraft's GpuTexture reports raw shifted dimensions rather than
        // clamping each axis to one. Stop before either non-square axis would
        // become zero.
        int smallest = Math.min(width, height);
        while (smallest > 1) {
            smallest >>= 1;
            levels++;
        }
        return levels;
    }

    private static void vertex(BufferBuilder builder, RingGeometry geometry,
                               double x, double z, double surfaceHeight,
                               float u, float v) {
        double angle = Math.PI * 2.0 * x / geometry.circumferenceBlocks();
        double radius = geometry.physicalRadiusAt(surfaceHeight);
        builder.addVertex((float)(radius * Math.sin(angle)),
                        (float)(-radius * Math.cos(angle)), (float)z)
                .setUv(u, v)
                .setColor(0xFFFFFFFF);
    }

    public static void clear() {
        if (vertexBuffer != null) vertexBuffer.close();
        vertexBuffer = null;
        vertexCount = 0;
        destroySurfaceTexture();
        bufferedGeometry = null;
        bufferedWorldHash = 0L;
        bufferedAtlasRevision = -1;
        projectionDiagnosticWorldHash = Long.MIN_VALUE;
    }

    private static void destroySurfaceTexture() {
        if (surfaceTextureView != null) surfaceTextureView.close();
        surfaceTextureView = null;
        if (surfaceTexture != null) surfaceTexture.close();
        surfaceTexture = null;
    }
}
