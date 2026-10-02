package dev.ringworld.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.ringworld.RingWorldMod;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.RingClientLodTuning;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingWaterColor;
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
import dev.ringworld.world.RingWallShaderStyle;
import dev.ringworld.world.RingStreamingProxyCoverage;
import org.joml.Matrix4f;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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
    private static ShaderInstance shader;
    private static VertexBuffer vertexBuffer;
    private static int vertexCount;
    private static DynamicTexture surfaceTexture;
    private static DynamicTexture previousSurfaceTexture;
    private static float surfaceCompletion;
    private static float previousSurfaceCompletion;
    /**
     * Exact dynamic reveal envelope used by the proxy drawn after section
     * setup and immediately before live terrain in 1.21.1.
     */
    private static float legacyProxyRevealScale = 1.0F;
    private static float legacyProxyVisibleCompletion = 1.0F;
    private static float legacyProxyGenerationFog;
    private static boolean legacyProxyDrawnThisFrame;
    private static float legacyStreamingFadeStartBlocks;
    private static float legacyStreamingOpaqueFromBlocks;
    private static boolean legacyStreamingWindowComplete;
    private static ClientLevel streamingCoverageLevel;
    private static int streamingCoverageCameraChunkX = Integer.MIN_VALUE;
    private static int streamingCoverageCameraChunkZ = Integer.MIN_VALUE;
    private static int streamingCoverageEffectiveChunks = -1;
    private static int streamingCoverageLoadedChunkCount = -1;
    private static LevelChunk[] streamingCoverageChunkIdentities = new LevelChunk[0];
    private static LevelChunk[] streamingCoverageObservedChunkIdentities =
            new LevelChunk[0];
    private static long streamingCoverageEvaluatedGameTime = Long.MIN_VALUE;
    private static int streamingCoverageReadyObservations;
    private static boolean streamingCoverageTransferPending;
    private static boolean streamingCoverageComplete;
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
    // Serial even across clear/reload: abandoned jobs cannot multiply peak native allocations.
    private static final java.util.concurrent.ExecutorService BUILD_WORKER =
            java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "RingWorld surface builder");
                thread.setDaemon(true);
                return thread;
            });

    private RingSurfaceTextureRenderer() { }

    /** Loader adapters install the 1.21.1 core shader during resource reload. */
    public static void installShader(ShaderInstance loadedShader) {
        shader = loadedShader;
    }

    public static void render(PoseStack matrices, RingGeometry geometry, Vec3 camera,
                              float alpha) {
        beginLegacyProxyFrame();
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
                || shader == null || vertexBuffer == null
                || surfaceTexture == null || vertexCount == 0) return;

        Minecraft client = Minecraft.getInstance();
        float textureMorph = updateTextureMorph(System.nanoTime());
        float visibleCompletion = previousSurfaceTexture == null
                ? surfaceCompletion
                : previousSurfaceCompletion
                        + (surfaceCompletion - previousSurfaceCompletion) * textureMorph;
        float generationFog = RingSurfaceGenerationFog.amount(
                visibleCompletion, ClientRingState.terrainPreview() != null);
        legacyProxyVisibleCompletion = visibleCompletion;
        legacyProxyGenerationFog = generationFog;
        legacyProxyRevealScale = Mth.clamp(alpha, 0.0F, 1.0F)
                * (1.0F - Mth.clamp(generationFog, 0.0F, 1.0F));
        double cameraAngle = Math.PI * 2.0 * geometry.wrapX(camera.x)
                / geometry.circumferenceBlocks();
        double cameraRadius = geometry.physicalRadiusAt(camera.y);
        if (projectionDiagnosticWorldHash != atlas.worldHash()) {
            projectionDiagnosticWorldHash = atlas.worldHash();
            double oppositeSurfaceDistance =
                    geometry.oppositeReferenceSurfaceDistance(camera.y);
            double oppositeWidthEdgeDistance =
                    geometry.maximumReferenceSurfaceDistance(camera.y, camera.z);
            float vanillaFarPlane = client.options.getEffectiveRenderDistance() * 64.0F;
            RingWorldMod.LOGGER.info(
                    "Ring proxy projection: level far plane={} blocks, radial-up opposite "
                            + "surface={} blocks, far width edge={} blocks; "
                            + "sky-depth compression required={}",
                    String.format(java.util.Locale.ROOT, "%.2f", vanillaFarPlane),
                    String.format(java.util.Locale.ROOT, "%.2f", oppositeSurfaceDistance),
                    String.format(java.util.Locale.ROOT, "%.2f", oppositeWidthEdgeDistance),
                    oppositeWidthEdgeDistance > vanillaFarPlane);
        }

        Matrix4f modelView = new Matrix4f(matrices.last().pose());
        // Model vertices use global ring coordinates. Rotate the entire static
        // object into the camera's tangent frame, then move its centre to the
        // same local point used by curved real chunk vertices.
        modelView.translate(0.0F, (float)cameraRadius, (float)-camera.z);
        modelView.rotateZ((float)-cameraAngle);
        Uniform modelOffset = shader.getUniform("ModelOffset");
        if (modelOffset != null) {
            modelOffset.set((float)cameraAngle, (float)camera.z, 0.0F);
        }
        int effectiveChunks = client.options.getEffectiveRenderDistance();
        int cameraChunkX = Mth.floor(camera.x) >> 4;
        int cameraChunkZ = Mth.floor(camera.z) >> 4;
        legacyStreamingWindowComplete = hasCompleteDrawableWindow(
                client, geometry, camera, cameraChunkX, cameraChunkZ,
                effectiveChunks);
        RingStreamingProxyCoverage.Span streamingCoverage =
                RingStreamingProxyCoverage.span(
                        effectiveChunks, legacyStreamingWindowComplete);
        legacyStreamingFadeStartBlocks = (float)streamingCoverage.fadeStartBlocks();
        legacyStreamingOpaqueFromBlocks = (float)streamingCoverage.opaqueFromBlocks();
        Uniform legacyStreaming = shader.getUniform("RingWorldLegacyStreaming");
        if (legacyStreaming != null) {
            legacyStreaming.set(
                    legacyStreamingFadeStartBlocks, legacyStreamingOpaqueFromBlocks);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, surfaceTexture.getId());
        RenderSystem.setShaderTexture(3, wallTexture == null ? surfaceTexture.getId() : wallTexture.getId());
        RenderSystem.setShaderTexture(1,
                previousSurfaceTexture == null
                        ? surfaceTexture.getId() : previousSurfaceTexture.getId());
        // Unit two is Minecraft's live lightmap, preserving time, weather,
        // gamma, lightning, darkness, and night vision for atlas terrain.
        client.gameRenderer.lightTexture().turnOnLightLayer();
        // In 1.21.1 VertexBuffer.drawWithShader calls setDefaultUniforms just
        // before applying the shader. ColorModulator is replaced there from
        // RenderSystem state, so publish backdrop id, reveal, texture morph,
        // and generation fog through that authoritative path.
        float[] previousShaderColor = RenderSystem.getShaderColor();
        float previousRed = previousShaderColor[0];
        float previousGreen = previousShaderColor[1];
        float previousBlue = previousShaderColor[2];
        float previousAlpha = previousShaderColor[3];
        try {
            RenderSystem.setShaderColor(
                    ClientRingState.skyProfile().backdrop().id(),
                    alpha, textureMorph, generationFog);
            // Terrain renders later in this same frame. Publish ownership only
            // once every resource and shader needed by the underlay is valid;
            // until then its fragment shaders keep live chunks fully opaque.
            legacyProxyDrawnThisFrame = true;
            vertexBuffer.bind();
            vertexBuffer.drawWithShader(
                    modelView, RenderSystem.getProjectionMatrix(), shader);
        } finally {
            VertexBuffer.unbind();
            RenderSystem.setShaderColor(
                    previousRed, previousGreen, previousBlue, previousAlpha);
            client.gameRenderer.lightTexture().turnOffLightLayer();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
        }
    }

    private static DynamicTexture wallTexture;
    private static RingWallTexture.ContentKey bufferedWallKey;

    private static void ensureResources(RingGeometry geometry, RingTerrainAtlas atlas) {
        if (atlas == null) return;
        int revision = ClientRingState.terrainAtlasRevision();
        boolean sameAtlas = geometry.equals(bufferedGeometry)
                && atlas.worldHash() == bufferedWorldHash;
        if (sameAtlas
                && revision == bufferedAtlasRevision
                && vertexBuffer != null && surfaceTexture != null) return;

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
                    VertexBuffer replacement = null;
                    try {
                        if (build.mesh() != null) {
                            long started = System.nanoTime();
                            replacement = RingSurfaceGpu.uploadMesh(build.mesh());
                            flagRenderStall("mesh GPU upload", started);
                        }
                        if (!uploadTexture(build.images(), (float)build.snapshot().atlas().completion())) {
                            return null;
                        }
                        if (build.wallImages() != null) {
                            long wallUploadStarted = System.nanoTime();
                            var image = build.wallImages()[0];
                            DynamicTexture next = new DynamicTexture(image.getWidth(), image.getHeight(), false);
                            try {
                                next.bind();
                                TextureUtil.prepareImage(next.getId(), build.wallImages().length - 1, image.getWidth(), image.getHeight());
                                for (int mip = 0; mip < build.wallImages().length; mip++)
                                    build.wallImages()[mip].upload(mip, 0, 0, false);
                                next.setFilter(true, true);
                                RenderSystem.texParameter(3553, 33085, build.wallImages().length - 1);
                                RenderSystem.texParameter(3553, 10242, 10497);
                                RenderSystem.texParameter(3553, 10243, 33071);
                            } catch (RuntimeException | Error failure) { next.close(); throw failure; }
                            if (wallTexture != null) wallTexture.close();
                            wallTexture = next;
                            bufferedWallKey = build.wallKey();
                            flagRenderStall("wall texture GPU upload", wallUploadStarted);
                        }
                        if (replacement != null) {
                            if (vertexBuffer != null) vertexBuffer.close();
                            vertexBuffer = replacement;
                            replacement = null;
                            vertexCount = build.mesh().vertexCount();
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
                : client.level.getMinBuildHeight();
        MeshInputs meshInputs = new MeshInputs(ClientRingState.surfaceReferenceY(),
                worldBottomY + ClientRingState.wallHeightBlocks(),
                ClientRingState.wallStyle().thicknessBlocks(),
                RingWallShaderStyle.encode(ClientRingState.wallStyle(), ClientRingState.generatorSeed(), client.level),
                geometry.equals(bufferedGeometry) && atlas.worldHash() == bufferedWorldHash,
                vertexBuffer != null, bufferedMeshDetailed, bufferedMeshHeightFingerprint,
                ClientRingState.wallStyle(), ClientRingState.generatorSeed(), worldBottomY,
                RingWallShaderStyle.paletteColors(ClientRingState.wallStyle(), state -> RingWallMaterialColors.color(state, client.level)), bufferedWallKey);
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
        NativeImage[] wallImages = null;
        RingWallTexture.ContentKey wallKey = null;
        try {
            if (RingSurfaceMeshRefreshPolicy.shouldRebuild(inputs.sameAtlas(), inputs.hasMesh(),
                    atlas.isComplete(), inputs.detailed(), preparedSnapshot.heightFingerprint(), inputs.fingerprint())) {
                RingSurfaceMesh.Mesh mesh = RingSurfaceMesh.build(atlas.geometry(), atlas, atlas.isComplete(),
                        inputs.referenceY(), inputs.wallTopY(), inputs.savedStyle(), profile);
                packed = RingSurfaceGpu.packMesh(mesh, inputs.wallStyle().vertexArgb());
                int wallColumns = Math.min(16384, profile.textureColumns());
                wallKey = RingWallTexture.contentKey(atlas, inputs.savedStyle(), inputs.seed(),
                        inputs.bottomY(), inputs.wallTopY(), wallColumns, inputs.palette());
                wallImages = wallKey.equals(inputs.previousWallKey()) ? null : RingWallTexture.buildMipmapped(atlas, inputs.savedStyle(), inputs.seed(),
                        inputs.bottomY(), inputs.wallTopY(), wallColumns, inputs.palette(),
                        () -> generation != textureBuildGeneration);
            }
            if (Boolean.getBoolean("ringworld.profileSurfaceBuilds") && packed != null) {
                RingWorldMod.LOGGER.info("RingWorld surface worker: mesh construction/packing took {} ms ({} vertices)",
                        (System.nanoTime() - meshStarted) / 1_000_000.0, packed.vertexCount());
            }
            if (generation != textureBuildGeneration) throw new java.util.concurrent.CancellationException("obsolete surface job");
            return new TextureBuild(preparedSnapshot,
                    buildTexturePixels(atlas, generation, profile, preview), packed, inputs.wallStyle(), wallImages, wallKey);
        } catch (RuntimeException | Error exception) {
            if (packed != null) packed.close();
            if (wallImages != null) for (NativeImage image : wallImages) image.close();
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
                    pixels[index] = RingWaterColor.tint(sample.color(), sample.waterCoverage());
                    blockLights[index] = RingSurfaceLod.blockLightAlpha(sample.blockLight());
                }
            }
        } else {
            for (int row = 0; row < targetRows; row++) {
                double z = geometry.minWidthZ() + (row + 0.5) * spacingZ;
                for (int column = 0; column < targetColumns; column++) {
                    double x = (column + 0.5) * spacingX;
                    RingTerrainAtlas.SurfaceSample sample = atlas.sample(x, z);
                    if (sample.present()) pixels[row * targetColumns + column] =
                            RingWaterColor.tint(pixels[row * targetColumns + column], sample.waterCoverage());
                    blockLights[row * targetColumns + column] = sample.present()
                            ? RingSurfaceLod.blockLightAlpha(sample.blockLight()) : 0;
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
                        presentHeightOr(heights, row * targetColumns + leftColumn, centerHeight),
                        presentHeightOr(heights, row * targetColumns + rightColumn, centerHeight),
                        presentHeightOr(heights, lowerRow * targetColumns + column, centerHeight),
                        presentHeightOr(heights, upperRow * targetColumns + column, centerHeight),
                        spacingX, spacingZ);
                // RGB remains opaque terrain data. Alpha independently stores
                // exposed block light and never controls mesh coverage.
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
                        // RingSurfaceLod is loader-neutral ARGB. NativeImage's
                        // 1.21.1 upload API consumes ABGR32, unlike mainline's
                        // ARGB setPixel API, so convert exactly at this adapter.
                        image.setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(
                                levelPixels[y * levelWidth + x]));
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
        DynamicTexture targetTexture = new DynamicTexture(textureColumns, textureRows, false);
        targetTexture.bind();
        TextureUtil.prepareImage(targetTexture.getId(), mipLevels - 1,
                textureColumns, textureRows);
        int levelWidth = textureColumns;
        int levelHeight = textureRows;
        try {
            for (int level = 0; level < mipLevels; level++) {
                NativeImage image = images.levels()[level];
                image.upload(level, 0, 0, 0, 0,
                        levelWidth, levelHeight,
                        true, false, true, false);
                levelWidth = Math.max(1, levelWidth >> 1);
                levelHeight = Math.max(1, levelHeight >> 1);
            }
            // Repeat canonical X exactly at U=0/1 while clamping the finite
            // width so rim pixels do not bleed across V.
            RenderSystem.texParameter(3553, 10242, 10497);
            RenderSystem.texParameter(3553, 10243, 33071);
            targetTexture.setFilter(true, true);
        } catch (RuntimeException | Error exception) {
            targetTexture.close();
            throw exception;
        }
        flagRenderStall("Atlas texture GPU upload", started);

        destroyPreviousSurfaceTexture();
        if (surfaceTexture == null) {
            surfaceTexture = targetTexture;
            surfaceCompletion = completion;
            previousSurfaceCompletion = completion;
            textureMorphStartedNanos = 0L;
        } else {
            previousSurfaceTexture = surfaceTexture;
            previousSurfaceCompletion = surfaceCompletion;
            surfaceTexture = targetTexture;
            surfaceCompletion = completion;
            textureMorphStartedNanos = System.nanoTime();
        }
        return true;
    }

    /**
     * Fails closed at the head of every RingWorld level frame. A blocked sky
     * path (for example lava or blindness) skips the later Atlas proxy draw,
     * while live terrain still renders and must therefore stay opaque.
     */
    public static void beginLegacyProxyFrame() {
        legacyProxyRevealScale = 0.0F;
        legacyProxyVisibleCompletion = 0.0F;
        legacyProxyGenerationFog = RingSurfaceGenerationFog.amount(0.0F);
        legacyProxyDrawnThisFrame = false;
        legacyStreamingFadeStartBlocks = 0.0F;
        legacyStreamingOpaqueFromBlocks = 0.0F;
        legacyStreamingWindowComplete = false;
    }

    private static boolean hasCompleteDrawableWindow(
            Minecraft client, RingGeometry geometry, Vec3 cameraPosition,
            int cameraChunkX, int cameraChunkZ, int effectiveChunks) {
        if (client.level == null) {
            invalidateStreamingCoverageProof();
            return false;
        }
        RingRenderProfile profile = RingRenderProfile.create(
                geometry, effectiveChunks * 16.0);
        // Queue-empty alone does not prove that the current drawable list has
        // compiled buffers. The proxy is invoked after setupRender and
        // compileSections but before terrain, so this bridge checks the exact
        // sections the live layers will draw in this frame. Dirty rebuilds
        // retain a compiled buffer and therefore keep the settled no-op stable.
        boolean currentViewSectionsCompiled =
                client.levelRenderer instanceof RingDrawableSectionView sectionView
                        && sectionView.ringworld$hasCompiledSectionsInsideProxyHole(
                                geometry, cameraPosition, effectiveChunks,
                                profile.proxyFadeStartBlocks());
        if (!currentViewSectionsCompiled) {
            streamingCoverageReadyObservations = 0;
            streamingCoverageTransferPending = false;
            streamingCoverageComplete = false;
            return false;
        }
        int loadedChunkCount = client.level.getChunkSource().getLoadedChunksCount();
        long gameTime = client.level.getGameTime();
        int previousCameraChunkX = streamingCoverageCameraChunkX;
        int previousCameraChunkZ = streamingCoverageCameraChunkZ;
        boolean sameLevelRadius = streamingCoverageLevel == client.level
                && streamingCoverageEffectiveChunks == effectiveChunks;
        boolean sameChart = sameLevelRadius
                && previousCameraChunkX == cameraChunkX
                && previousCameraChunkZ == cameraChunkZ;
        boolean adjacentChart = sameLevelRadius && !sameChart
                && Math.abs(previousCameraChunkX - cameraChunkX) <= 1
                && Math.abs(previousCameraChunkZ - cameraChunkZ) <= 1;
        boolean sameWindow = sameChart
                && streamingCoverageLoadedChunkCount == loadedChunkCount;
        // Render can run several times per client tick. Scan the complete
        // vanilla-shaped finite-band window at most once per tick. A count
        // change forces an immediate scan; a later tick also compares every
        // required LevelChunk identity, so a balanced unload/replacement
        // cannot inherit proof merely because the count stayed unchanged.
        if (sameWindow && streamingCoverageEvaluatedGameTime == gameTime) {
            return streamingCoverageComplete;
        }
        int diameter = effectiveChunks * 2 + 1;
        int identitySlots = diameter * diameter;
        if (streamingCoverageChunkIdentities.length != identitySlots
                || streamingCoverageObservedChunkIdentities.length != identitySlots) {
            streamingCoverageChunkIdentities = new LevelChunk[identitySlots];
            streamingCoverageObservedChunkIdentities = new LevelChunk[identitySlots];
            sameLevelRadius = false;
            sameChart = false;
            adjacentChart = false;
        }
        boolean identitiesMatch = sameChart;
        boolean intersectionIdentitiesMatch = adjacentChart;
        boolean loadedWindowComplete = true;
        int identityIndex = 0;
        for (int chunkX = cameraChunkX - effectiveChunks;
             chunkX <= cameraChunkX + effectiveChunks; chunkX++) {
            for (int chunkZ = cameraChunkZ - effectiveChunks;
                 chunkZ <= cameraChunkZ + effectiveChunks; chunkZ++) {
                boolean required = ChunkTrackingView.isInViewDistance(
                        cameraChunkX, cameraChunkZ, effectiveChunks, chunkX, chunkZ)
                        && !geometry.isExteriorChunkZ(chunkZ);
                LevelChunk chunk = required
                        ? client.level.getChunkSource().getChunk(
                                chunkX, chunkZ, ChunkStatus.FULL, false)
                        : null;
                streamingCoverageObservedChunkIdentities[identityIndex] = chunk;
                if (required && chunk == null) {
                    loadedWindowComplete = false;
                }
                if (identitiesMatch
                        && streamingCoverageChunkIdentities[identityIndex] != chunk) {
                    identitiesMatch = false;
                }
                if (intersectionIdentitiesMatch && required
                        && ChunkTrackingView.isInViewDistance(
                                previousCameraChunkX, previousCameraChunkZ,
                                effectiveChunks, chunkX, chunkZ)
                        && previousStreamingChunkIdentity(
                                chunkX, chunkZ, previousCameraChunkX,
                                previousCameraChunkZ, effectiveChunks) != chunk) {
                    intersectionIdentitiesMatch = false;
                }
                identityIndex++;
            }
        }
        // First ownership still needs two distinct client-tick observations
        // of the same exact chunks with an empty compile queue. An asynchronous
        // graph reset can temporarily expose an empty drawable list before its
        // replacement is installed, and that moment must not make proof sticky.
        // Once proven, an ordinary dirty-section rebuild retains its previous
        // vertex buffer and must not flash the emergency Atlas underlay through
        // foliage/water.
        boolean retainedProof = sameChart && identitiesMatch
                && streamingCoverageComplete;
        boolean adjacentTransfer = streamingCoverageComplete
                && !streamingCoverageTransferPending
                && adjacentChart && intersectionIdentitiesMatch
                && loadedWindowComplete
                && RingStreamingProxyCoverage.coversAdjacentNewFringe(
                        effectiveChunks, profile.proxyFadeStartBlocks());
        int readyObservations;
        boolean transferPending;
        boolean complete;
        if (retainedProof) {
            readyObservations = streamingCoverageReadyObservations;
            transferPending = streamingCoverageTransferPending;
            complete = true;
            if (transferPending) {
                if (loadedWindowComplete
                        && client.levelRenderer.hasRenderedAllSections()) {
                    readyObservations = streamingCoverageEvaluatedGameTime == gameTime
                            ? readyObservations
                            : Math.min(2, readyObservations + 1);
                } else {
                    readyObservations = 0;
                }
                transferPending = readyObservations < 2;
            } else {
                readyObservations = 2;
            }
        } else if (adjacentTransfer) {
            // Only the new outer fringe lacks the old proof. Experiment 19 is
            // already opaque there, so keep the near no-op while post-render
            // discovery earns a fresh exact-window proof. Do not transfer a
            // second time until two queue-empty ticks confirm that fringe.
            readyObservations = 0;
            transferPending = true;
            complete = true;
        } else if (loadedWindowComplete
                && client.levelRenderer.hasRenderedAllSections()) {
            readyObservations = sameChart && identitiesMatch
                    && streamingCoverageReadyObservations > 0
                    ? streamingCoverageEvaluatedGameTime == gameTime
                            ? streamingCoverageReadyObservations
                            : Math.min(2, streamingCoverageReadyObservations + 1)
                    : 1;
            transferPending = false;
            complete = readyObservations >= 2;
        } else {
            readyObservations = 0;
            transferPending = false;
            complete = false;
        }
        LevelChunk[] previousIdentities = streamingCoverageChunkIdentities;
        streamingCoverageChunkIdentities = streamingCoverageObservedChunkIdentities;
        streamingCoverageObservedChunkIdentities = previousIdentities;
        streamingCoverageLevel = client.level;
        streamingCoverageCameraChunkX = cameraChunkX;
        streamingCoverageCameraChunkZ = cameraChunkZ;
        streamingCoverageEffectiveChunks = effectiveChunks;
        streamingCoverageLoadedChunkCount = loadedChunkCount;
        streamingCoverageEvaluatedGameTime = gameTime;
        streamingCoverageReadyObservations = readyObservations;
        streamingCoverageTransferPending = transferPending;
        streamingCoverageComplete = complete;
        return complete;
    }

    private static LevelChunk previousStreamingChunkIdentity(
            int chunkX, int chunkZ, int cameraChunkX, int cameraChunkZ,
            int effectiveChunks) {
        int diameter = effectiveChunks * 2 + 1;
        int localX = chunkX - (cameraChunkX - effectiveChunks);
        int localZ = chunkZ - (cameraChunkZ - effectiveChunks);
        if (localX < 0 || localX >= diameter || localZ < 0 || localZ >= diameter) {
            return null;
        }
        return streamingCoverageChunkIdentities[localX * diameter + localZ];
    }

    private static void invalidateStreamingCoverageProof() {
        streamingCoverageLevel = null;
        streamingCoverageCameraChunkX = Integer.MIN_VALUE;
        streamingCoverageCameraChunkZ = Integer.MIN_VALUE;
        streamingCoverageEffectiveChunks = -1;
        streamingCoverageLoadedChunkCount = -1;
        streamingCoverageChunkIdentities = new LevelChunk[0];
        streamingCoverageObservedChunkIdentities = new LevelChunk[0];
        streamingCoverageEvaluatedGameTime = Long.MIN_VALUE;
        streamingCoverageReadyObservations = 0;
        streamingCoverageTransferPending = false;
        streamingCoverageComplete = false;
    }

    private static float updateTextureMorph(long nowNanos) {
        if (previousSurfaceTexture == null || textureMorphStartedNanos == 0L) return 1.0F;
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
                                RingWallShaderStyle.Encoded wallStyle, NativeImage[] wallImages, RingWallTexture.ContentKey wallKey) implements AutoCloseable {
        @Override
        public void close() {
            try { images.close(); } finally {
                try { if (mesh != null) mesh.close(); } finally { if (wallImages != null) for (NativeImage image : wallImages) image.close(); }
            }
        }
    }

    private record MeshInputs(int referenceY, int wallTopY, int wallThickness,
                              RingWallShaderStyle.Encoded wallStyle, boolean sameAtlas,
                              boolean hasMesh, boolean detailed, long fingerprint,
                              dev.ringworld.world.RingWallStyle savedStyle, long seed, int bottomY, int[] palette, RingWallTexture.ContentKey previousWallKey) { }

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

    private static float presentHeightOr(float[] heights, int index, float fallback) {
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
        legacyProxyRevealScale = 1.0F;
        legacyProxyVisibleCompletion = 1.0F;
        legacyProxyGenerationFog = 0.0F;
        legacyProxyDrawnThisFrame = false;
        legacyStreamingFadeStartBlocks = 0.0F;
        legacyStreamingOpaqueFromBlocks = 0.0F;
        legacyStreamingWindowComplete = false;
        invalidateStreamingCoverageProof();
    }

    /** 1.21.1-only bridge; shared profile and wire fields remain unchanged. */
    public static float legacyProxyRevealScale() {
        return legacyProxyRevealScale;
    }

    public static float legacyProxyVisibleCompletion() {
        return legacyProxyVisibleCompletion;
    }

    public static float legacyProxyGenerationFog() {
        return legacyProxyGenerationFog;
    }

    public static float legacyProxyDrawnThisFrame() {
        return legacyProxyDrawnThisFrame ? 1.0F : 0.0F;
    }

    public static float legacyStreamingFadeStartBlocks() {
        return legacyStreamingFadeStartBlocks;
    }

    public static float legacyStreamingOpaqueFromBlocks() {
        return legacyStreamingOpaqueFromBlocks;
    }

    public static boolean legacyStreamingWindowComplete() {
        return legacyStreamingWindowComplete;
    }

    /** Testable raw teardown state, independent of whether a client level is open. */
    public static boolean sessionCleared() {
        return vertexBuffer == null && vertexCount == 0
                && surfaceTexture == null && previousSurfaceTexture == null
                && wallTexture == null && bufferedWallKey == null
                && bufferedGeometry == null && bufferedWorldHash == 0L
                && pendingTextureBuild == null && legacyProxyRevealScale == 1.0F
                && legacyProxyVisibleCompletion == 1.0F
                && legacyProxyGenerationFog == 0.0F
                && !legacyProxyDrawnThisFrame
                && legacyStreamingFadeStartBlocks == 0.0F
                && legacyStreamingOpaqueFromBlocks == 0.0F
                && !legacyStreamingWindowComplete
                && streamingCoverageLevel == null
                && streamingCoverageCameraChunkX == Integer.MIN_VALUE
                && streamingCoverageCameraChunkZ == Integer.MIN_VALUE
                && streamingCoverageEffectiveChunks == -1
                && streamingCoverageLoadedChunkCount == -1
                && streamingCoverageChunkIdentities.length == 0
                && streamingCoverageObservedChunkIdentities.length == 0
                && streamingCoverageEvaluatedGameTime == Long.MIN_VALUE
                && streamingCoverageReadyObservations == 0
                && !streamingCoverageTransferPending
                && !streamingCoverageComplete;
    }

    private static void destroySurfaceTexture() {
        if (wallTexture != null) wallTexture.close();
        wallTexture = null;
        bufferedWallKey = null;
        destroyPreviousSurfaceTexture();
        if (surfaceTexture != null) surfaceTexture.close();
        surfaceTexture = null;
        surfaceCompletion = 0.0F;
        previousSurfaceCompletion = 0.0F;
        textureMorphStartedNanos = 0L;
    }

    private static void destroyPreviousSurfaceTexture() {
        if (previousSurfaceTexture != null) previousSurfaceTexture.close();
        previousSurfaceTexture = null;
    }
}
