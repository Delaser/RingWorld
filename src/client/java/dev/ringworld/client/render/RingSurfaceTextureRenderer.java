package dev.ringworld.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ringworld.RingWorldMod;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.RingClientLodTuning;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingDimensionReport;
import dev.ringworld.world.RingRenderProfile;
import dev.ringworld.world.RingSurfaceLod;
import dev.ringworld.world.RingSurfaceBuildSnapshot;
import dev.ringworld.world.RingSurfaceMesh;
import dev.ringworld.world.RingSurfaceMeshRefreshPolicy;
import dev.ringworld.world.RingSurfaceGenerationFog;
import dev.ringworld.world.RingSurfaceMorph;
import dev.ringworld.world.RingSurfacePlaceholder;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingAtlasFidelity;
import dev.ringworld.world.RingTerrainPreview;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
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
    private static GpuBuffer vertexBuffer;
    private static int vertexCount;
    private static GpuTexture surfaceTexture;
    private static GpuTextureView surfaceTextureView;
    private static GpuTexture previousSurfaceTexture;
    private static GpuTextureView previousSurfaceTextureView;
    private static float surfaceCompletion;
    private static float previousSurfaceCompletion;
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
    private static volatile long textureBuildGeneration;
    private static Matrix4f wallPaletteMatrix = new Matrix4f();
    // Serial even across clear/reload: abandoned jobs cannot multiply peak native allocations.
    private static final java.util.concurrent.ExecutorService BUILD_WORKER =
            java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "RingWorld surface builder");
                thread.setDaemon(true);
                return thread;
            });

    private RingSurfaceTextureRenderer() { }

    /** Loader adapters register this pipeline during their client setup event. */
    public static RenderPipeline pipeline() { return RingSurfaceGpu.pipeline(); }

    public static void render(PoseStack matrices, RingGeometry geometry, Vec3 camera,
                              float alpha) {
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        // Never fall through to buffers left by another world while the
        // current world's atlas is absent or has no trustworthy cells. Session
        // callbacks normally destroy those resources; this guard makes the
        // world-hash boundary authoritative even if a callback is missed.
        if (atlas == null) return;
        long resourceStarted = System.nanoTime();
        ensureResources(geometry, atlas);
        flagRenderStall("surface resource update (including allocation/release)", resourceStarted);
        if (!geometry.equals(bufferedGeometry) || atlas.worldHash() != bufferedWorldHash
                || vertexBuffer == null || surfaceTexture == null || vertexCount == 0) return;

        Minecraft client = Minecraft.getInstance();
        float textureMorph = updateTextureMorph(System.nanoTime());
        float visibleCompletion = previousSurfaceTextureView == null
                ? surfaceCompletion
                : previousSurfaceCompletion
                        + (surfaceCompletion - previousSurfaceCompletion) * textureMorph;
        float generationFog = RingSurfaceGenerationFog.amount(
                visibleCompletion,
                ClientRingState.terrainPreview() != null);
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
                new Vector4f(ClientRingState.skyProfile().backdrop().id(),
                        alpha, textureMorph, generationFog),
                new Vector3f((float)cameraAngle, (float)camera.z, RingSurfaceGpu.farBackgroundDepth()),
                wallPaletteMatrix);

        RingSurfaceGpu.draw(client, vertexBuffer, vertexCount, surfaceTextureView,
                previousSurfaceTextureView == null ? surfaceTextureView : previousSurfaceTextureView,
                wallTextureView, transforms);
        modelView.popMatrix();
    }

    private static GpuTexture wallTexture;
    private static GpuTextureView wallTextureView;

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
        bufferedGeometry = geometry;
        bufferedWorldHash = builtAtlas.worldHash();
        bufferedAtlasRevision = buildSnapshot.renderRevision();
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
            try (build) {
                if (build.images().generation() == textureBuildGeneration
                        && build.snapshot().canPublish(geometry, atlas.worldHash(), revision,
                                geometry.equals(bufferedGeometry) && atlas.worldHash() == bufferedWorldHash
                                        ? bufferedAtlasRevision : -1)) {
                    GpuBuffer replacement = null;
                    try {
                        if (build.mesh() != null) {
                            long started = System.nanoTime();
                            replacement = RingSurfaceGpu.uploadMesh(build.mesh());
                            flagRenderStall("mesh GPU upload", started);
                        }
                        if (!uploadTexture(build.images(), (float)build.snapshot().atlas().completion())) {
                            return null;
                        }
                        if (build.wallImage() != null) {
                            long wallUploadStarted = System.nanoTime();
                            var image = build.wallImage();
                            GpuTexture next = RingSurfaceGpu.createSurfaceTexture(image.getWidth(), image.getHeight(), 1);
                            GpuTextureView view = null;
                            try {
                                RingSurfaceGpu.uploadSurfaceTexture(next, new NativeImage[]{image});
                                view = RenderSystem.getDevice().createTextureView(next);
                            } catch (RuntimeException | Error failure) {
                                if (view != null) view.close();
                                next.close(); throw failure;
                            }
                            if (wallTextureView != null) wallTextureView.close();
                            if (wallTexture != null) wallTexture.close();
                            wallTexture = next; wallTextureView = view;
                            flagRenderStall("wall texture GPU upload", wallUploadStarted);
                        }
                        if (replacement != null) {
                            if (vertexBuffer != null) vertexBuffer.close();
                            vertexBuffer = replacement;
                            replacement = null;
                            vertexCount = build.mesh().vertexCount();
                            wallPaletteMatrix = build.wallStyle().paletteMatrix();
                            bufferedMeshDetailed = build.snapshot().atlas().isComplete();
                            bufferedMeshHeightFingerprint = build.snapshot().heightFingerprint();
                        }
                        return build.snapshot();
                    } finally {
                        if (replacement != null) replacement.close();
                    }
                }
            }
        }

        var quality = RingClientLodTuning.quality();
        long snapshotStarted = System.nanoTime();
        RingTerrainAtlas snapshot = atlas.snapshot();
        flagRenderStall("Atlas snapshot copy", snapshotStarted);
        RingRenderProfile profile = RingClientLodTuning.profile(geometry, 16.0,
                ClientRingState.generationSettings().atlasFidelity());
        RingSurfaceBuildSnapshot buildSnapshot = new RingSurfaceBuildSnapshot(snapshot, revision);
        long generation = textureBuildGeneration;
        Minecraft client = Minecraft.getInstance();
        int worldBottomY = client.level == null ? RingDimensionReport.VANILLA_OVERWORLD_BOTTOM_Y
                : client.level.getMinY();
        MeshInputs meshInputs = new MeshInputs(ClientRingState.surfaceReferenceY(),
                worldBottomY + ClientRingState.wallHeightBlocks(),
                ClientRingState.wallStyle().thicknessBlocks(),
                RingWallShaderStyle.encode(ClientRingState.wallStyle(), ClientRingState.generatorSeed(), client.level),
                geometry.equals(bufferedGeometry) && atlas.worldHash() == bufferedWorldHash,
                vertexBuffer != null, bufferedMeshDetailed, bufferedMeshHeightFingerprint,
                ClientRingState.wallStyle(), ClientRingState.generatorSeed(), worldBottomY,
                RingWallShaderStyle.paletteColors(ClientRingState.wallStyle(), client.level));
        RingTerrainPreview preview = ClientRingState.terrainPreview();
        pendingTextureBuild = CompletableFuture.supplyAsync(
                () -> buildTexture(buildSnapshot, generation, profile, quality, meshInputs, preview), BUILD_WORKER);
        return null;
    }

    /** Runs entirely on the existing texture worker, including the complete-only hash scan. */
    private static TextureBuild buildTexture(RingSurfaceBuildSnapshot sourceSnapshot,
                                             long generation, RingRenderProfile profile,
                                             dev.ringworld.world.RingLodQuality quality,
                                             MeshInputs inputs, RingTerrainPreview preview) {
        if (generation != textureBuildGeneration) throw new java.util.concurrent.CancellationException("obsolete surface job");
        RingSurfaceBuildSnapshot displaySnapshot = quality == null
                || !sourceSnapshot.atlas().isComplete()
                || quality.sampleStep() <= sourceSnapshot.atlas().sampleStep() ? sourceSnapshot
                : new RingSurfaceBuildSnapshot(quality.displaySnapshot(sourceSnapshot.atlas()),
                        sourceSnapshot.renderRevision());
        RingSurfaceBuildSnapshot preparedSnapshot = displaySnapshot.resolveDetailedHeightFingerprint();
        RingTerrainAtlas atlas = preparedSnapshot.atlas();
        long meshStarted = System.nanoTime();
        RingSurfaceGpu.PackedMesh packed = null;
        NativeImage wallImage = null;
        try {
            if (RingSurfaceMeshRefreshPolicy.shouldRebuild(inputs.sameAtlas(), inputs.hasMesh(),
                    atlas.isComplete(), inputs.detailed(), preparedSnapshot.heightFingerprint(), inputs.fingerprint())) {
                RingSurfaceMesh.Mesh mesh = RingSurfaceMesh.build(atlas.geometry(), atlas, atlas.isComplete(),
                        inputs.referenceY(), inputs.wallTopY(), inputs.wallThickness(), profile);
                packed = RingSurfaceGpu.packMesh(mesh, inputs.wallStyle().vertexArgb());
                wallImage = RingWallTexture.build(atlas, inputs.savedStyle(), inputs.seed(),
                        inputs.bottomY(), inputs.wallTopY(), Math.min(16384, profile.textureColumns()), inputs.palette(),
                        () -> generation != textureBuildGeneration);
            }
            if (Boolean.getBoolean("ringworld.profileSurfaceBuilds") && packed != null) {
                RingWorldMod.LOGGER.info("RingWorld surface worker: mesh construction/packing took {} ms ({} vertices)",
                        (System.nanoTime() - meshStarted) / 1_000_000.0, packed.vertexCount());
            }
            if (generation != textureBuildGeneration) throw new java.util.concurrent.CancellationException("obsolete surface job");
            return new TextureBuild(preparedSnapshot,
                    buildTexturePixels(atlas, generation, profile, preview), packed, inputs.wallStyle(), wallImage);
        } catch (RuntimeException | Error exception) {
            if (packed != null) packed.close();
            if (wallImage != null) wallImage.close();
            throw exception;
        }
    }

    private static TextureImages buildTexturePixels(RingTerrainAtlas atlas, long generation,
                                                     RingRenderProfile profile, RingTerrainPreview preview) {
        RingGeometry geometry = atlas.geometry();
        // A partial atlas never needs the expanded final texture: its source
        // cells are the only trustworthy detail. Keeping the progressive
        // texture at source resolution bounds each coalesced rebuild; the
        // normal expanded texture is allocated once at completion.
        int targetColumns = atlas.isComplete()
                ? profile.textureColumns()
                : Math.min(Math.max(atlas.columns(), preview == null ? 0 : preview.columns()),
                        profile.textureColumns());
        int targetRows = atlas.isComplete()
                ? profile.textureRows()
                : Math.min(Math.max(atlas.rows(), preview == null ? 0 : preview.rows()),
                        profile.textureRows());
        int[] pixels;
        float[] heights;
        int[] blockLights = new int[targetColumns * targetRows];
        if (atlas.isComplete()) {
            pixels = new int[targetColumns * targetRows];
            heights = new float[pixels.length];
        } else {
            RingSurfacePlaceholder.Surface placeholder = RingSurfacePlaceholder.resolve(
                    atlas, targetColumns, targetRows, preview);
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
                    pixels[index] = sample.color() & 0xFFFFFF;
                    blockLights[index] = lightAlpha(sample.blockLight());
                }
            }
        } else {
            for (int row = 0; row < targetRows; row++) {
                double z = geometry.minWidthZ() + (row + 0.5) * spacingZ;
                for (int column = 0; column < targetColumns; column++) {
                    double x = (column + 0.5) * spacingX;
                    RingTerrainAtlas.SurfaceSample sample = atlas.sample(x, z);
                    blockLights[row * targetColumns + column] = sample.present()
                            ? lightAlpha(sample.blockLight()) : 0;
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
                float centerHeight = heights[index];
                int shaded = RingSurfaceLod.shadeSurfaceColor(
                        pixels[index], centerHeight,
                        presentHeightOr(heights, pixels, row * targetColumns + leftColumn, centerHeight),
                        presentHeightOr(heights, pixels, row * targetColumns + rightColumn, centerHeight),
                        presentHeightOr(heights, pixels, lowerRow * targetColumns + column, centerHeight),
                        presentHeightOr(heights, pixels, upperRow * targetColumns + column, centerHeight),
                        spacingX, spacingZ);
                // The RGB channels remain ordinary daytime terrain. Alpha is
                // a separate exposed block-light intensity consumed only by
                // the nighttime shader; mesh opacity does not depend on it.
                pixels[index] = blockLights[index] << 24 | shaded;
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
                    levelPixels = RingSurfaceLod.buildNextMipRgbLight(
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

    private static boolean uploadTexture(TextureImages images, float completion) {
        if (images.generation() != textureBuildGeneration) {
            return false;
        }
        long started = System.nanoTime();
        textureColumns = images.columns();
        textureRows = images.rows();
        int mipLevels = images.levels().length;
        // Each revision gets a new target so the shader can retain and blend
        // the previous visible texture without another CPU upload per frame.
        GpuTexture targetTexture = RingSurfaceGpu.createSurfaceTexture(
                textureColumns, textureRows, mipLevels);
        GpuTextureView targetView = null;
        try {
            targetView = RenderSystem.getDevice().createTextureView(targetTexture);
            RingSurfaceGpu.uploadSurfaceTexture(targetTexture, images.levels());
        } catch (RuntimeException | Error exception) {
            if (targetView != null) targetView.close();
            targetTexture.close();
            throw exception;
        }
        flagRenderStall("Atlas texture GPU upload", started);

        destroyPreviousSurfaceTexture();
        if (surfaceTexture == null || surfaceTextureView == null) {
            surfaceTexture = targetTexture;
            surfaceTextureView = targetView;
            surfaceCompletion = completion;
            previousSurfaceCompletion = completion;
            textureMorphStartedNanos = 0L;
        } else {
            previousSurfaceTexture = surfaceTexture;
            previousSurfaceTextureView = surfaceTextureView;
            previousSurfaceCompletion = surfaceCompletion;
            surfaceTexture = targetTexture;
            surfaceTextureView = targetView;
            surfaceCompletion = completion;
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
                                TextureImages images, RingSurfaceGpu.PackedMesh mesh,
                                RingWallShaderStyle.Encoded wallStyle, NativeImage wallImage) implements AutoCloseable {
        @Override
        public void close() {
            try { images.close(); } finally {
                try { if (mesh != null) mesh.close(); } finally { if (wallImage != null) wallImage.close(); }
            }
        }
    }

    private record MeshInputs(int referenceY, int wallTopY, int wallThickness,
                              RingWallShaderStyle.Encoded wallStyle, boolean sameAtlas,
                              boolean hasMesh, boolean detailed, long fingerprint,
                              dev.ringworld.world.RingWallStyle savedStyle, long seed, int bottomY, int[] palette) { }

    private static void flagRenderStall(String operation, long started) {
        long elapsed = System.nanoTime() - started;
        if (Boolean.getBoolean("ringworld.profileSurfaceBuilds") && elapsed >= 1_000_000L) {
            RingWorldMod.LOGGER.info("RingWorld surface timing: {} took {} ms", operation, elapsed / 1_000_000.0);
        }
        if (elapsed >= 16_000_000L) {
            RingWorldMod.LOGGER.warn("RingWorld render stall candidate: {} took {} ms", operation,
                    String.format(java.util.Locale.ROOT, "%.2f", elapsed / 1_000_000.0));
        }
    }

    private static float presentHeightOr(float[] heights, int[] pixels, int index, float fallback) {
        return Float.isFinite(heights[index]) ? heights[index] : fallback;
    }

    private static int lightAlpha(double blockLight) {
        return Math.max(0, Math.min(255, (int)Math.round(blockLight * 255.0 / 15.0)));
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

    public static boolean displayReady() {
        return vertexBuffer != null && surfaceTexture != null && pendingTextureBuild == null
                && bufferedAtlasRevision == ClientRingState.terrainAtlasRevision();
    }

    public static void clear() {
        textureBuildGeneration++;
        wallPaletteMatrix = new Matrix4f();
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
        if (wallTextureView != null) wallTextureView.close();
        wallTextureView = null;
        if (wallTexture != null) wallTexture.close();
        wallTexture = null;
        destroyPreviousSurfaceTexture();
        if (surfaceTextureView != null) surfaceTextureView.close();
        surfaceTextureView = null;
        if (surfaceTexture != null) surfaceTexture.close();
        surfaceTexture = null;
        surfaceCompletion = 0.0F;
        previousSurfaceCompletion = 0.0F;
        textureMorphStartedNanos = 0L;
    }

    private static void destroyPreviousSurfaceTexture() {
        if (previousSurfaceTextureView != null) previousSurfaceTextureView.close();
        previousSurfaceTextureView = null;
        if (previousSurfaceTexture != null) previousSurfaceTexture.close();
        previousSurfaceTexture = null;
    }
}
