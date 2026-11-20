package dev.ringworld.client.render;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ringworld.RingWorldMod;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.RingClientLodTuning;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingDimensionReport;
import dev.ringworld.world.RingLodQuality;
import dev.ringworld.world.RingRenderProfile;
import dev.ringworld.world.RingSurfaceLod;
import dev.ringworld.world.RingSurfaceBuildSnapshot;
import dev.ringworld.world.RingSurfaceMesh;
import dev.ringworld.world.RingSurfaceMeshRefreshPolicy;
import dev.ringworld.world.RingSurfaceGenerationFog;
import dev.ringworld.world.RingSurfaceMorph;
import dev.ringworld.world.RingSurfacePlaceholder;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingWaterColor;
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
    private static RingSurfaceBuffers vertexBuffer;
    private static int vertexCount;
    private static int[] layerOffsets;
    private static GpuTexture surfaceTexture;
    private static GpuTextureView surfaceTextureView;
    private static GpuTexture previousSurfaceTexture;
    private static GpuTextureView previousSurfaceTextureView;
    // Each texture carries the hashes of its own last upload. A morph can skip any number of revisions.
    private static long[][] surfaceRowHashes, previousRowHashes;
    private static int previousColumns;
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
    private static StagedUpload stagedUpload;
    private static volatile long textureBuildGeneration;
    private static Matrix4f wallPaletteMatrix = new Matrix4f();
    // Serial even across clear/reload: abandoned jobs cannot multiply peak native allocations.
    private static final java.util.concurrent.ExecutorService BUILD_WORKER =
            java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "RingWorld surface builder");
                thread.setDaemon(true);
                return thread;
            });

    private static final RingTeleportCoverage teleportCoverage = new RingTeleportCoverage();

    /** Only a complete, currently displayed Atlas can supply live water colours. */
    public static GpuTextureView waterSurface() {
        var atlas = ClientRingState.terrainAtlas();
        return atlas != null && atlas.worldHash() == bufferedWorldHash && surfaceCompletion == 1F
                ? surfaceTextureView : null;
    }

    public static float teleportCoverage(Minecraft client, RingGeometry geometry, Vec3 camera) {
        if (client.level == null || vertexBuffer == null || !geometry.equals(bufferedGeometry)
                || dev.ringworld.client.RingMinecraftClientAccess.camera(client).getFluidInCamera()
                        == net.minecraft.world.level.material.FogType.WATER) return 0;
        return teleportCoverage.opacity(client, geometry, camera);
    }

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
        Minecraft client = Minecraft.getInstance();
        // Distant scenery must not remain visible through underwater fog.
        // Use the render camera (also correct for third person), retain buffers
        // for resurfacing, and leave real chunks to Minecraft's normal fog.
        if (dev.ringworld.client.RingMinecraftClientAccess.camera(client).getFluidInCamera()
                == net.minecraft.world.level.material.FogType.WATER) return;
        long resourceStarted = System.nanoTime();
        ensureResources(geometry, atlas);
        flagRenderStall("surface resource update (including allocation/release)", resourceStarted);
        if (!geometry.equals(bufferedGeometry) || atlas.worldHash() != bufferedWorldHash
                || vertexBuffer == null || surfaceTexture == null || vertexCount == 0) return;

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
        if (!dev.ringworld.client.RingDistortionTuning.enabled()) {
            modelView.translate(0.0F, (float)cameraRadius, (float)-camera.z);
            modelView.rotateZ((float)-cameraAngle);
        }
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                modelView,
                new Vector4f(dev.ringworld.world.RingLayerBand.bandWidth(),
                        alpha, textureMorph, generationFog),
                new Vector3f((float)cameraAngle, (float)camera.z, RingSurfaceGpu.farBackgroundDepth()),
                wallPaletteMatrix);

        int first = 0, end = 0;
        if (layerOffsets != null) {
            int segments = layerOffsets.length - 1;
            double step = (double)geometry.circumferenceBlocks() / segments;
            int centre = (int)(geometry.wrapX(camera.x) / step);
            int radius = 1 + (int)Math.ceil(client.options.getEffectiveRenderDistance() * 48.0 / step);
            if (2L * radius + 1 < segments && geometry.physicalRadiusAt(client.level.getMaxY()) > 0) {
                first = Math.floorMod(centre - radius, segments);
                end = Math.floorMod(centre + radius + 1, segments);
            }
        }
        RingSurfaceGpu.draw(client, vertexBuffer, vertexCount, layerOffsets, first, end, surfaceTextureView,
                textureMorph >= 1.0f || previousSurfaceTextureView == null ? surfaceTextureView : previousSurfaceTextureView,
                wallTextureView, transforms);
        modelView.popMatrix();
    }

    private static GpuTexture wallTexture;
    private static GpuTextureView wallTextureView;
    private static RingWallTexture.ContentKey wallTextureKey;

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
            try {
                stagedUpload = new StagedUpload(pendingTextureBuild.join());
            } catch (RuntimeException exception) {
                RingWorldMod.LOGGER.error("Could not build RingWorld surface texture", exception);
                return null;
            } finally {
                pendingTextureBuild = null;
            }
        }
        if (stagedUpload != null) {
            TextureBuild build = stagedUpload.build;
            int displayedRevision = geometry.equals(bufferedGeometry) && atlas.worldHash() == bufferedWorldHash
                    ? bufferedAtlasRevision : -1;
            boolean retain = false;
            try {
                if (build.images().generation() == textureBuildGeneration
                        && build.snapshot().canPublish(geometry, atlas.worldHash(), revision, displayedRevision)) {
                    if (stagedUpload.prepareNext()) {
                        retain = true;
                        return null;
                    }
                    stagedUpload.publish();
                    return build.snapshot();
                }
            } finally {
                if (!retain) {
                    stagedUpload.close();
                    stagedUpload = null;
                }
            }
        }

        var quality = RingClientLodTuning.quality();
        long snapshotStarted = System.nanoTime();
        RingTerrainAtlas snapshot = atlas.snapshot();
        flagRenderStall("Atlas snapshot copy", snapshotStarted);
        RingRenderProfile profile = RingClientLodTuning.profile(geometry, 16.0);
        RingSurfaceBuildSnapshot buildSnapshot = new RingSurfaceBuildSnapshot(snapshot, revision);
        long generation = textureBuildGeneration;
        Minecraft client = Minecraft.getInstance();
        int worldBottomY = client.level == null ? RingDimensionReport.VANILLA_OVERWORLD_BOTTOM_Y
                : client.level.getMinY();
        MeshInputs meshInputs = new MeshInputs(ClientRingState.surfaceReferenceY(),
                worldBottomY + ClientRingState.wallHeightBlocks(),
                ClientRingState.wallHeightBlocks(),
                RingWallShaderStyle.encode(ClientRingState.wallStyle(), ClientRingState.generatorSeed(), client.level),
                geometry.equals(bufferedGeometry) && atlas.worldHash() == bufferedWorldHash,
                vertexBuffer != null, bufferedMeshDetailed, bufferedMeshHeightFingerprint,
                ClientRingState.wallStyle(), ClientRingState.generatorSeed(), worldBottomY,
                RingWallShaderStyle.paletteColors(ClientRingState.wallStyle(), client.level),
                wallTextureView == null ? null : wallTextureKey);
        RingTerrainPreview preview = ClientRingState.terrainPreview();
        pendingTextureBuild = CompletableFuture.supplyAsync(
                () -> buildTexture(buildSnapshot, generation, profile, quality, meshInputs, preview), BUILD_WORKER);
        return null;
    }

    /** Runs entirely on the existing texture worker, including the complete-only hash scan. */
    private static TextureBuild buildTexture(RingSurfaceBuildSnapshot sourceSnapshot,
                                             long generation, RingRenderProfile profile,
                                             RingLodQuality quality,
                                             MeshInputs inputs, RingTerrainPreview preview) {
        if (generation != textureBuildGeneration) throw new java.util.concurrent.CancellationException("obsolete surface job");
        RingSurfaceBuildSnapshot displaySnapshot = quality == null
                || !sourceSnapshot.atlas().isComplete()
                || quality.sampleStep() <= sourceSnapshot.atlas().sampleStep() ? sourceSnapshot
                : new RingSurfaceBuildSnapshot(quality.displaySnapshot(sourceSnapshot.atlas()),
                        sourceSnapshot.renderRevision());
        RingSurfaceBuildSnapshot preparedSnapshot = displaySnapshot.resolveDetailedHeightFingerprint(
                inputs.wallHeightBlocks() > 0 ? inputs.savedStyle().thicknessBlocks() : 0);
        RingTerrainAtlas atlas = preparedSnapshot.atlas();
        long meshStarted = System.nanoTime();
        RingSurfaceGpu.PackedMesh packed = null;
        NativeImage[] wallImages = null;
        RingWallTexture.ContentKey nextWallTextureKey = null;
        try {
            if (RingSurfaceMeshRefreshPolicy.shouldRebuild(inputs.sameAtlas(), inputs.hasMesh(),
                    atlas.isComplete(), inputs.detailed(), preparedSnapshot.heightFingerprint(), inputs.fingerprint())) {
                RingSurfaceMesh.Mesh mesh = RingSurfaceMesh.build(atlas.geometry(), atlas, atlas.isComplete(),
                        inputs.referenceY(), inputs.wallTopY(), inputs.wallHeightBlocks(),
                        inputs.savedStyle(), inputs.seed(), profile, quality == RingLodQuality.MEDIUM);
                packed = RingSurfaceGpu.packMesh(mesh, inputs.wallStyle().vertexArgb());
                int wallColumns = Math.min(16384, profile.textureColumns());
                nextWallTextureKey = RingWallTexture.contentKey(atlas, inputs.savedStyle(), inputs.seed(),
                        inputs.bottomY(), inputs.wallTopY(), wallColumns, inputs.palette());
                if (!nextWallTextureKey.equals(inputs.wallTextureKey())) {
                    wallImages = RingWallTexture.buildMipmapped(atlas, inputs.savedStyle(), inputs.seed(),
                            inputs.bottomY(), inputs.wallTopY(), wallColumns, inputs.palette(),
                            () -> generation != textureBuildGeneration);
                }
            }
            if (Boolean.getBoolean("ringworld.profileSurfaceBuilds") && packed != null) {
                RingWorldMod.LOGGER.info("RingWorld surface worker: mesh construction/packing took {} ms ({} vertices)",
                        (System.nanoTime() - meshStarted) / 1_000_000.0, packed.vertexCount());
            }
            if (generation != textureBuildGeneration) throw new java.util.concurrent.CancellationException("obsolete surface job");
            return new TextureBuild(preparedSnapshot,
                    buildTexturePixels(atlas, sourceSnapshot.atlas(), generation, profile, preview), packed, inputs.wallStyle(),
                    wallImages, nextWallTextureKey);
        } catch (RuntimeException | Error exception) {
            if (packed != null) packed.close();
            if (wallImages != null) for (NativeImage image : wallImages) image.close();
            throw exception;
        }
    }

    private static TextureImages buildTexturePixels(RingTerrainAtlas atlas, RingTerrainAtlas source, long generation,
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
                    pixels[index] = RingWaterColor.tint(sample.color(), sample.waterCoverage());
                    if (source != atlas) {
                        // Medium retains captured material at its real world position on the coarser mesh.
                        boolean sourceColor = atlas.sampleStep() == RingLodQuality.MEDIUM.sampleStep()
                                || (sample.waterCoverage() > 0 && sample.waterCoverage() < 1);
                        int texel = RingSurfaceLod.detailTexel(source, column, row, targetColumns, targetRows, pixels[index], sourceColor);
                        pixels[index] = texel & 0xFFFFFF;
                        blockLights[index] = texel >>> 24;
                    } else {
                        blockLights[index] = lightAlpha(sample.blockLight());
                    }
                }
            }
        } else {
            for (int row = 0; row < targetRows; row++) {
                double z = geometry.minWidthZ() + (row + 0.5) * spacingZ;
                for (int column = 0; column < targetColumns; column++) {
                    double x = (column + 0.5) * spacingX;
                    RingTerrainAtlas.SurfaceSample sample = atlas.sample(x, z);
                    int index = row * targetColumns + column;
                    blockLights[index] = sample.present() ? lightAlpha(sample.blockLight()) : 0;
                    if (sample.present()) pixels[index] = RingWaterColor.tint(
                            pixels[index], sample.waterCoverage());
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
        long[][] rowHashes = new long[mipLevels][];
        int[] levelPixels = pixels;
        int levelWidth = targetColumns;
        int levelHeight = targetRows;
        try {
            for (int level = 0; level < mipLevels; level++) {
                NativeImage image = new NativeImage(levelWidth, levelHeight, false);
                images[level] = image;
                rowHashes[level] = new long[levelHeight];
                for (int y = 0; y < levelHeight; y++) {
                    long hash = 0xCBF29CE484222325L;
                    for (int x = 0; x < levelWidth; x++) {
                        int pixel = levelPixels[y * levelWidth + x];
                        image.setPixel(x, y, pixel);
                        hash = (hash ^ pixel) * 0x100000001B3L;
                    }
                    rowHashes[level][y] = hash;
                }
                if (level + 1 < mipLevels) {
                    levelPixels = RingSurfaceLod.buildNextMipRgbLight(
                            levelPixels, levelWidth, levelHeight);
                    levelWidth = Math.max(1, levelWidth >> 1);
                    levelHeight = Math.max(1, levelHeight >> 1);
                }
            }
            return new TextureImages(generation, targetColumns, targetRows, images, rowHashes);
        } catch (RuntimeException | Error exception) {
            for (NativeImage image : images) {
                if (image != null) image.close();
            }
            throw exception;
        }
    }

    private static float updateTextureMorph(long nowNanos) {
        if (previousSurfaceTextureView == null || textureMorphStartedNanos == 0L) return 1.0F;
        float progress = RingSurfaceMorph.progress(nowNanos - textureMorphStartedNanos);
        // Complete Atlas edits coalesce for up to ten seconds. Keep the spare across
        // several edits so updating a handful of rows doesn't allocate a full texture.
        boolean finishedInitialStream = surfaceCompletion >= 1.0F && previousSurfaceCompletion < 1.0F;
        long retention = surfaceCompletion >= 1.0F ? 30_000_000_000L : 10_000_000_000L;
        if (progress >= 1.0F && (finishedInitialStream || nowNanos - textureMorphStartedNanos >= retention)) {
            destroyPreviousSurfaceTexture();
            textureMorphStartedNanos = 0L;
        }
        return progress;
    }

    private record TextureImages(long generation, int columns, int rows,
                                 NativeImage[] levels, long[][] rowHashes) implements AutoCloseable {
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
                                RingWallShaderStyle.Encoded wallStyle, NativeImage[] wallImages,
                                RingWallTexture.ContentKey wallTextureKey) implements AutoCloseable {
        @Override
        public void close() {
            try { images.close(); } finally {
                try { if (mesh != null) mesh.close(); } finally { if (wallImages != null) for (NativeImage image : wallImages) image.close(); }
            }
        }
    }

    /** Keeps the displayed ring intact while the render thread uploads one resource per pass. */
    private static final class StagedUpload implements AutoCloseable {
        final TextureBuild build;
        RingSurfaceBuffers mesh;
        GpuTexture surface;
        GpuTextureView surfaceView;
        dev.ringworld.world.RingSurfaceRowUpdates.Cursor rows;
        long transferred;
        boolean reused;
        GpuTexture wall;
        GpuTextureView wallView;

        StagedUpload(TextureBuild build) {
            this.build = build;
        }

        boolean prepareNext() {
            if (build.mesh() != null) {
                var packed = build.mesh();
                if (mesh == null) mesh = new RingSurfaceBuffers(packed.vertexCount());
                long started = System.nanoTime();
                if (mesh.uploadNext(packed.data().vertexBuffer(), packed.vertexCount())) {
                    flagRenderStall("mesh GPU page upload", started);
                    return true;
                }
            }
            if (build.wallImages() != null && wall == null) {
                long started = System.nanoTime();
                NativeImage[] images = build.wallImages();
                wall = RingSurfaceGpu.createSurfaceTexture(images[0].getWidth(), images[0].getHeight(), images.length);
                RingSurfaceGpu.uploadSurfaceTexture(wall, images);
                wallView = RenderSystem.getDevice().createTextureView(wall);
                flagRenderStall("wall texture GPU upload", started);
                return true;
            }
            var images = build.images();
            if (surface == null) {
                // Keep both sides of the displayed cross-fade immutable until it finishes.
                if (updateTextureMorph(System.nanoTime()) < 1.0F) return true;
                reused = previousSurfaceTexture != null && previousColumns == images.columns()
                        && previousRowHashes.length == images.levels().length && previousRowHashes[0].length == images.rows();
                rows = new dev.ringworld.world.RingSurfaceRowUpdates.Cursor(
                        reused ? previousRowHashes : null, images.rowHashes(), images.columns());
                if (reused) {
                    surface = previousSurfaceTexture;
                    surfaceView = previousSurfaceTextureView;
                    previousSurfaceTexture = null;
                    previousSurfaceTextureView = null;
                    previousRowHashes = null;
                } else {
                    destroyPreviousSurfaceTexture();
                    surface = RingSurfaceGpu.createSurfaceTexture(images.columns(), images.rows(), images.levels().length);
                    surfaceView = RenderSystem.getDevice().createTextureView(surface);
                }
                return true;
            }
            if (!rows.done()) {
                long started = System.nanoTime();
                transferred += RingSurfaceGpu.uploadRows(surface, images.levels(), rows);
                flagRenderStall("Atlas texture GPU upload", started);
                return true;
            }
            return false;
        }

        void publish() {
            if (Boolean.getBoolean("ringworld.profileSurfaceBuilds")) {
                RingWorldMod.LOGGER.info("RingWorld Atlas transfer: {} {} texels", reused ? "rows" : "full", transferred);
            }
            previousSurfaceTexture = surfaceTexture;
            previousSurfaceTextureView = surfaceTextureView;
            previousRowHashes = surfaceRowHashes;
            previousColumns = textureColumns;
            previousSurfaceCompletion = surfaceCompletion;
            surfaceTexture = surface;
            surfaceTextureView = surfaceView;
            surface = null;
            surfaceView = null;
            surfaceRowHashes = build.images().rowHashes();
            textureColumns = build.images().columns();
            textureRows = build.images().rows();
            surfaceCompletion = (float)build.snapshot().atlas().completion();
            textureMorphStartedNanos = previousSurfaceTexture == null ? 0L : System.nanoTime();
            if (mesh != null) {
                if (vertexBuffer != null) vertexBuffer.close();
                vertexBuffer = mesh;
                mesh = null;
                vertexCount = build.mesh().vertexCount();
                layerOffsets = build.mesh().layerOffsets();
                wallPaletteMatrix = build.wallStyle().paletteMatrix();
                bufferedMeshDetailed = build.snapshot().atlas().isComplete();
                bufferedMeshHeightFingerprint = build.snapshot().heightFingerprint();
            }
            if (wall != null) {
                if (wallTextureView != null) wallTextureView.close();
                if (wallTexture != null) wallTexture.close();
                wallTexture = wall;
                wallTextureView = wallView;
                wallTextureKey = build.wallTextureKey();
                wall = null;
                wallView = null;
            }
        }

        @Override
        public void close() {
            if (mesh != null) mesh.close();
            if (surfaceView != null) surfaceView.close();
            if (surface != null) surface.close();
            if (wallView != null) wallView.close();
            if (wall != null) wall.close();
            build.close();
        }
    }

    private record MeshInputs(int referenceY, int wallTopY, int wallHeightBlocks,
                              RingWallShaderStyle.Encoded wallStyle, boolean sameAtlas,
                              boolean hasMesh, boolean detailed, long fingerprint,
                              dev.ringworld.world.RingWallStyle savedStyle, long seed, int bottomY,
                              int[] palette, RingWallTexture.ContentKey wallTextureKey) { }

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

    /** A complete published scene, including the preceding side of its cross-fade.
     * Newer complete revisions may still be building; they do not invalidate
     * the coherent complete scene already displayed. Client-thread only. */
    public static boolean captureReady() {
        var atlas = ClientRingState.terrainAtlas();
        return atlas != null && atlas.isComplete()
                && vertexBuffer != null && vertexCount > 0 && surfaceTexture != null && bufferedMeshDetailed
                && bufferedWorldHash == atlas.worldHash()
                && bufferedGeometry != null && bufferedGeometry.equals(ClientRingState.geometry())
                && surfaceCompletion >= 1.0F
                && (previousSurfaceTextureView == null || previousSurfaceCompletion >= 1.0F);
    }

    public static boolean displayReady() {
        return vertexBuffer != null && surfaceTexture != null && pendingTextureBuild == null && stagedUpload == null
                && bufferedAtlasRevision == ClientRingState.terrainAtlasRevision();
    }

    public static void clear() {
        teleportCoverage.clear();
        textureBuildGeneration++;
        wallPaletteMatrix = new Matrix4f();
        CompletableFuture<TextureBuild> abandonedBuild = pendingTextureBuild;
        if (abandonedBuild != null) abandonedBuild.thenAccept(TextureBuild::close);
        pendingTextureBuild = null;
        if (stagedUpload != null) {
            stagedUpload.close();
            stagedUpload = null;
        }
        if (vertexBuffer != null) vertexBuffer.close();
        vertexBuffer = null;
        vertexCount = 0;
        layerOffsets = null;
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
                && pendingTextureBuild == null && stagedUpload == null;
    }

    private static void destroySurfaceTexture() {
        if (wallTextureView != null) wallTextureView.close();
        wallTextureView = null;
        if (wallTexture != null) wallTexture.close();
        wallTexture = null;
        wallTextureKey = null;
        destroyPreviousSurfaceTexture();
        if (surfaceTextureView != null) surfaceTextureView.close();
        surfaceTextureView = null;
        if (surfaceTexture != null) surfaceTexture.close();
        surfaceTexture = null;
        surfaceRowHashes = null;
        surfaceCompletion = 0.0F;
        previousSurfaceCompletion = 0.0F;
        textureMorphStartedNanos = 0L;
    }

    private static void destroyPreviousSurfaceTexture() {
        if (previousSurfaceTextureView != null) previousSurfaceTextureView.close();
        previousSurfaceTextureView = null;
        if (previousSurfaceTexture != null) previousSurfaceTexture.close();
        previousSurfaceTexture = null;
        previousRowHashes = null;
    }
}
