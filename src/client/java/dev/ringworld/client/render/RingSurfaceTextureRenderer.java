package dev.ringworld.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.CompareOp;
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
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingDimensionReport;
import dev.ringworld.world.RingRenderProfile;
import dev.ringworld.world.RingSurfaceLod;
import dev.ringworld.world.RingSurfaceBuildSnapshot;
import dev.ringworld.world.RingSurfaceMesh;
import dev.ringworld.world.RingSurfaceMeshRefreshPolicy;
import dev.ringworld.world.RingSurfaceMorph;
import dev.ringworld.world.RingSurfacePlaceholder;
import dev.ringworld.world.RingTerrainAtlas;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Static, texture-backed representation of the generated surface.
 *
 * <p>The mesh lives at the ring's real physical radius. Its texture U axis is
 * canonical circumference X and repeats exactly at X=0, while V is the finite
 * width. Player movement changes one model transform; it never rebuilds the
 * mesh or allocates distant vanilla chunk sections.</p>
 */
public final class RingSurfaceTextureRenderer {
    private static final RenderPipeline PIPELINE =
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("ringworld", "pipeline/textured_ring_surface"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("ringworld", "core/ring_surface"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("ringworld", "core/ring_surface"))
                    .withSampler("Sampler1")
                    .withSampler("Sampler2")
                    .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withDepthStencilState(
                            new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR,
                            VertexFormat.Mode.TRIANGLES)
                    .build();

    private static GpuBuffer vertexBuffer;
    private static int vertexCount;
    private static GpuTexture surfaceTexture;
    private static GpuTextureView surfaceTextureView;
    private static GpuTexture previousSurfaceTexture;
    private static GpuTextureView previousSurfaceTextureView;
    private static long textureMorphStartedNanos;
    private static RingGeometry bufferedGeometry;
    private static long bufferedWorldHash;
    private static int bufferedAtlasRevision = -1;
    private static boolean bufferedMeshDetailed;
    private static long bufferedMeshHeightFingerprint =
            RingSurfaceBuildSnapshot.NO_DETAILED_HEIGHT_FINGERPRINT;
    private static long projectionDiagnosticWorldHash = Long.MIN_VALUE;
    private static int textureColumns;
    private static int textureRows;
    private static CompletableFuture<TextureBuild> pendingTextureBuild;
    private static long textureBuildGeneration;

    private RingSurfaceTextureRenderer() { }

    /** Loader adapters register this pipeline during their client setup event. */
    public static RenderPipeline pipeline() { return PIPELINE; }

    public static void render(PoseStack matrices, RingGeometry geometry, Vec3 camera,
                              float alpha) {
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        // Never fall through to buffers left by another world while the
        // current world's atlas is absent or has no trustworthy cells. Session
        // callbacks normally destroy those resources; this guard makes the
        // world-hash boundary authoritative even if a callback is missed.
        if (atlas == null) return;
        ensureResources(geometry, atlas);
        if (!geometry.equals(bufferedGeometry) || atlas.worldHash() != bufferedWorldHash
                || vertexBuffer == null || surfaceTexture == null || vertexCount == 0) return;

        Minecraft client = Minecraft.getInstance();
        float textureMorph = updateTextureMorph(System.nanoTime());
        double cameraAngle = Math.PI * 2.0 * geometry.wrapX(camera.x)
                / geometry.circumferenceBlocks();
        double cameraRadius = geometry.physicalRadiusAt(camera.y);
        if (projectionDiagnosticWorldHash != atlas.worldHash()) {
            projectionDiagnosticWorldHash = atlas.worldHash();
            double oppositeSurfaceDistance =
                    geometry.oppositeReferenceSurfaceDistance(camera.y);
            double oppositeWidthEdgeDistance =
                    geometry.maximumReferenceSurfaceDistance(camera.y, camera.z);
            float vanillaFarPlane = Math.max(
                    client.options.getEffectiveRenderDistance() * 64.0F,
                    client.options.cloudRange().get() * 16.0F);
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
                new Vector4f(1.0F, alpha, textureMorph, 0.0F),
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
            pass.bindTexture("Sampler1",
                    previousSurfaceTextureView == null
                            ? surfaceTextureView : previousSurfaceTextureView,
                    RenderSystem.getSamplerCache().getSampler(
                            AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE,
                            FilterMode.LINEAR, FilterMode.LINEAR, true));
            // The atlas contains surface albedo, while real chunk vertices are
            // multiplied by Minecraft's live lightmap. Sampling the same
            // full-skylight/no-block-light texel keeps exposed proxy terrain
            // synchronized with time, weather, gamma, lightning, darkness,
            // and night vision instead of applying a hand-tuned grey scalar.
            pass.bindTexture("Sampler2",
                    client.gameRenderer.levelLightmap(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setVertexBuffer(0, vertexBuffer);
            pass.draw(0, vertexCount);
        }
        modelView.popMatrix();
    }

    private static void ensureResources(RingGeometry geometry, RingTerrainAtlas atlas) {
        if (atlas == null) return;
        int revision = ClientRingState.terrainAtlasRevision();
        boolean sameAtlas = geometry.equals(bufferedGeometry)
                && atlas.worldHash() == bufferedWorldHash;
        if (sameAtlas
                && revision == bufferedAtlasRevision
                && vertexBuffer != null && surfaceTexture != null
                && surfaceTextureView != null) return;

        RingSurfaceBuildSnapshot buildSnapshot = finishOrScheduleTextureBuild(
                geometry, atlas, revision);
        if (buildSnapshot == null) return;
        RingTerrainAtlas builtAtlas = buildSnapshot.atlas();
        boolean detailed = builtAtlas.isComplete();
        // During generation the availability mask changes frequently but the
        // cylinder does not. Keep one conservative reference-height mesh and
        // update only its texture. Completion upgrades to the terrain-height
        // mesh. A later complete-atlas revision may change exposed heights, so
        // texture and relief must advance together.
        if (RingSurfaceMeshRefreshPolicy.shouldRebuild(sameAtlas, vertexBuffer != null,
                detailed, bufferedMeshDetailed, buildSnapshot.heightFingerprint(),
                bufferedMeshHeightFingerprint)) {
            buildMesh(geometry, builtAtlas, detailed);
            bufferedMeshDetailed = detailed;
            bufferedMeshHeightFingerprint = detailed
                    ? buildSnapshot.heightFingerprint()
                    : RingSurfaceBuildSnapshot.NO_DETAILED_HEIGHT_FINGERPRINT;
        }
        bufferedGeometry = geometry;
        bufferedWorldHash = builtAtlas.worldHash();
        bufferedAtlasRevision = revision;
        RingWorldMod.LOGGER.info(
                "Textured ring surface ready: {}x{} source atlas expanded to {}x{} GPU texture, "
                        + "{} vertices, {} cells ({}%), mesh={}",
                builtAtlas.columns(), builtAtlas.rows(), textureColumns, textureRows,
                vertexCount, builtAtlas.presentCount(),
                Math.round(builtAtlas.completion() * 1000.0) / 10.0,
                detailed ? "terrain-height" : "progressive-reference-height");
    }

    /**
     * Returns the immutable atlas whose pixels were just uploaded, or null
     * while a build is pending. Callers must use the returned atlas for any
     * matching detailed mesh rather than sampling the live atlas again.
     */
    private static RingSurfaceBuildSnapshot finishOrScheduleTextureBuild(RingGeometry geometry,
                                                                          RingTerrainAtlas atlas,
                                                                          int revision) {
        if (pendingTextureBuild != null) {
            if (!pendingTextureBuild.isDone()) return null;
            TextureBuild build;
            try {
                build = pendingTextureBuild.join();
            } catch (RuntimeException exception) {
                RingWorldMod.LOGGER.error("Could not build RingWorld surface texture", exception);
                pendingTextureBuild = null;
                return null;
            }
            pendingTextureBuild = null;
            if (build.snapshot().matches(geometry, atlas.worldHash(), revision)) {
                return uploadTexture(build.images()) ? build.snapshot() : null;
            }
            build.close();
        }

        RingTerrainAtlas snapshot = atlas.snapshot();
        RingSurfaceBuildSnapshot buildSnapshot = new RingSurfaceBuildSnapshot(snapshot, revision);
        long generation = textureBuildGeneration;
        pendingTextureBuild = CompletableFuture.supplyAsync(
                () -> buildTexture(buildSnapshot, generation));
        return null;
    }

    /** Runs entirely on the existing texture worker, including the complete-only hash scan. */
    private static TextureBuild buildTexture(RingSurfaceBuildSnapshot sourceSnapshot,
                                             long generation) {
        RingSurfaceBuildSnapshot preparedSnapshot =
                sourceSnapshot.resolveDetailedHeightFingerprint();
        return new TextureBuild(preparedSnapshot,
                buildTexturePixels(preparedSnapshot.atlas(), generation));
    }

    private static TextureImages buildTexturePixels(RingTerrainAtlas atlas, long generation) {
        RingGeometry geometry = atlas.geometry();
        RingRenderProfile profile = RingRenderProfile.create(geometry, 16.0);
        // A partial atlas never needs the expanded final texture: its source
        // cells are the only trustworthy detail. Keeping the progressive
        // texture at source resolution bounds each coalesced rebuild; the
        // normal expanded texture is allocated once at completion.
        int targetColumns = atlas.isComplete()
                ? profile.textureColumns() : Math.min(atlas.columns(), profile.textureColumns());
        int targetRows = atlas.isComplete()
                ? profile.textureRows() : Math.min(atlas.rows(), profile.textureRows());
        int[] pixels;
        float[] heights;
        if (atlas.isComplete()) {
            pixels = new int[targetColumns * targetRows];
            heights = new float[pixels.length];
        } else {
            RingSurfacePlaceholder.Surface placeholder = RingSurfacePlaceholder.resolve(
                    atlas, targetColumns, targetRows);
            pixels = placeholder.argb();
            heights = placeholder.heights();
        }
        double spacingX = (double)geometry.circumferenceBlocks() / targetColumns;
        double spacingZ = (double)geometry.widthBlocks() / targetRows;
        if (atlas.isComplete()) {
            for (int row = 0; row < targetRows; row++) {
                double z = geometry.minWidthZ()
                        + (row + 0.5) * spacingZ;
                for (int column = 0; column < targetColumns; column++) {
                    double x = (column + 0.5) * spacingX;
                    RingTerrainAtlas.SurfaceSample sample = atlas.sample(x, z);
                    int index = row * targetColumns + column;
                    heights[index] = (float)sample.height();
                    pixels[index] = RingSurfaceLod.surfaceArgb(sample.color(), sample.coverage());
                }
            }
        }
        for (int row = 0; row < targetRows; row++) {
            int lowerRow = Math.max(0, row - 1);
            int upperRow = Math.min(targetRows - 1, row + 1);
            for (int column = 0; column < targetColumns; column++) {
                int leftColumn = Math.floorMod(column - 1, targetColumns);
                int rightColumn = Math.floorMod(column + 1, targetColumns);
                int index = row * targetColumns + column;
                int alpha = pixels[index] >>> 24;
                if (alpha == 0) continue;
                float centerHeight = heights[index];
                int shaded = RingSurfaceLod.shadeSurfaceColor(
                        pixels[index], centerHeight,
                        presentHeightOr(heights, pixels, row * targetColumns + leftColumn, centerHeight),
                        presentHeightOr(heights, pixels, row * targetColumns + rightColumn, centerHeight),
                        presentHeightOr(heights, pixels, lowerRow * targetColumns + column, centerHeight),
                        presentHeightOr(heights, pixels, upperRow * targetColumns + column, centerHeight),
                        spacingX, spacingZ);
                pixels[index] = alpha << 24 | shaded;
            }
        }

        int mipLevels = mipLevels(targetColumns, targetRows);
        NativeImage[] images = new NativeImage[mipLevels];
        int[] levelPixels = pixels;
        int levelWidth = targetColumns;
        int levelHeight = targetRows;
        try {
            for (int level = 0; level < mipLevels; level++) {
                NativeImage image = new NativeImage(levelWidth, levelHeight, false);
                images[level] = image;
                for (int y = 0; y < levelHeight; y++) {
                    for (int x = 0; x < levelWidth; x++) {
                        image.setPixel(x, y, levelPixels[y * levelWidth + x]);
                    }
                }
                if (level + 1 < mipLevels) {
                    levelPixels = RingSurfaceLod.buildNextMipArgb(
                            levelPixels, levelWidth, levelHeight);
                    levelWidth = Math.max(1, levelWidth >> 1);
                    levelHeight = Math.max(1, levelHeight >> 1);
                }
            }
            return new TextureImages(generation, targetColumns, targetRows, images);
        } catch (RuntimeException | Error exception) {
            for (NativeImage image : images) {
                if (image != null) image.close();
            }
            throw exception;
        }
    }

    private static boolean uploadTexture(TextureImages images) {
        if (images.generation() != textureBuildGeneration) {
            images.close();
            return false;
        }
        textureColumns = images.columns();
        textureRows = images.rows();
        int mipLevels = images.levels().length;
        // Each revision gets a new target so the shader can retain and blend
        // the previous visible texture without another CPU upload per frame.
        GpuTexture targetTexture = RenderSystem.getDevice().createTexture(
                "RingWorld canonical surface atlas",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, textureColumns, textureRows, 1, mipLevels);
        GpuTextureView targetView = RenderSystem.getDevice().createTextureView(targetTexture);
        int levelWidth = textureColumns;
        int levelHeight = textureRows;
        try {
            for (int level = 0; level < mipLevels; level++) {
                NativeImage image = images.levels()[level];
                RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                        targetTexture, image, level, 0, 0, 0,
                        levelWidth, levelHeight, 0, 0);
                levelWidth = Math.max(1, levelWidth >> 1);
                levelHeight = Math.max(1, levelHeight >> 1);
            }
        } catch (RuntimeException | Error exception) {
            targetView.close();
            targetTexture.close();
            throw exception;
        } finally {
            images.close();
        }

        destroyPreviousSurfaceTexture();
        if (surfaceTexture == null || surfaceTextureView == null) {
            surfaceTexture = targetTexture;
            surfaceTextureView = targetView;
            textureMorphStartedNanos = 0L;
        } else {
            previousSurfaceTexture = surfaceTexture;
            previousSurfaceTextureView = surfaceTextureView;
            surfaceTexture = targetTexture;
            surfaceTextureView = targetView;
            textureMorphStartedNanos = System.nanoTime();
        }
        return true;
    }

    private static float updateTextureMorph(long nowNanos) {
        if (previousSurfaceTextureView == null || textureMorphStartedNanos == 0L) return 1.0F;
        float progress = RingSurfaceMorph.progress(nowNanos - textureMorphStartedNanos);
        if (progress >= 1.0F) {
            destroyPreviousSurfaceTexture();
            textureMorphStartedNanos = 0L;
        }
        return progress;
    }

    private record TextureImages(long generation, int columns, int rows,
                                 NativeImage[] levels) implements AutoCloseable {
        @Override
        public void close() {
            for (NativeImage image : levels) {
                if (image != null) image.close();
            }
        }
    }

    /** Native texture images plus their immutable source content. */
    private record TextureBuild(RingSurfaceBuildSnapshot snapshot,
                                TextureImages images) implements AutoCloseable {
        @Override
        public void close() {
            images.close();
        }
    }

    private static float presentHeightOr(float[] heights, int[] pixels, int index, float fallback) {
        return pixels[index] >>> 24 == 0 ? fallback : heights[index];
    }

    private static void buildMesh(RingGeometry geometry, RingTerrainAtlas atlas, boolean detailed) {
        int worldBottomY = Minecraft.getInstance().level == null
                ? RingDimensionReport.VANILLA_OVERWORLD_BOTTOM_Y
                : Minecraft.getInstance().level.getMinY();
        int wallTopY = worldBottomY + ClientRingState.wallHeightBlocks();
        RingSurfaceMesh.Mesh mesh = RingSurfaceMesh.build(
                geometry, atlas, detailed, ClientRingState.surfaceReferenceY(), wallTopY,
                RingGenerationBoundary.RIM_THICKNESS);
        int count = mesh.vertexCount();
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        try (ByteBufferBuilder allocator = ByteBufferBuilder.exactlySized(count * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(
                    allocator, VertexFormat.Mode.TRIANGLES, format);
            mesh.emitTriangles((x, y, z, u, v) -> builder.addVertex(x, y, z)
                    .setUv(u, v)
                    .setColor(0xFFFFFFFF));
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

    public static void clear() {
        textureBuildGeneration++;
        CompletableFuture<TextureBuild> abandonedBuild = pendingTextureBuild;
        if (abandonedBuild != null) abandonedBuild.thenAccept(TextureBuild::close);
        pendingTextureBuild = null;
        if (vertexBuffer != null) vertexBuffer.close();
        vertexBuffer = null;
        vertexCount = 0;
        destroySurfaceTexture();
        bufferedGeometry = null;
        bufferedWorldHash = 0L;
        bufferedAtlasRevision = -1;
        bufferedMeshDetailed = false;
        bufferedMeshHeightFingerprint =
                RingSurfaceBuildSnapshot.NO_DETAILED_HEIGHT_FINGERPRINT;
        projectionDiagnosticWorldHash = Long.MIN_VALUE;
    }

    /** Testable raw teardown state, independent of whether a client level is open. */
    public static boolean sessionCleared() {
        return vertexBuffer == null && vertexCount == 0
                && surfaceTexture == null && surfaceTextureView == null
                && previousSurfaceTexture == null && previousSurfaceTextureView == null
                && bufferedGeometry == null && bufferedWorldHash == 0L
                && pendingTextureBuild == null;
    }

    private static void destroySurfaceTexture() {
        destroyPreviousSurfaceTexture();
        if (surfaceTextureView != null) surfaceTextureView.close();
        surfaceTextureView = null;
        if (surfaceTexture != null) surfaceTexture.close();
        surfaceTexture = null;
        textureMorphStartedNanos = 0L;
    }

    private static void destroyPreviousSurfaceTexture() {
        if (previousSurfaceTextureView != null) previousSurfaceTextureView.close();
        previousSurfaceTextureView = null;
        if (previousSurfaceTexture != null) previousSurfaceTexture.close();
        previousSurfaceTexture = null;
    }
}
