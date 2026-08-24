package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRenderProfile;
import dev.ringworld.world.RingTerrainAtlas;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GraphicsStatus;
import dev.ringworld.client.compat.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.server.level.ChunkTrackingView;

/**
 * Opt-in, non-destructive visual probe for the projection directions and
 * live/proxy handoff that expose complete-ring regressions on large layouts.
 */
public final class RingProjectionCaptureClient {
    private static final String ENABLE_PROPERTY = "ringworld.captureRingProjection";
    private static final String WORLD_PROPERTY = "ringworld.projectionWorld";
    private static final String VIEW_DISTANCE_PROPERTY =
            "ringworld.projectionViewDistanceChunks";
    private static final String ENVIRONMENT_PROPERTY =
            "ringworld.projectionEnvironment";
    private static final String GRAPHICS_MODE_PROPERTY =
            "ringworld.projectionGraphicsMode";
    private static final String STREAMING_EDGE_PROPERTY =
            "ringworld.projectionStreamingEdge";
    private static final int DEFAULT_VIEW_DISTANCE_CHUNKS = 16;
    private static final int MIN_VIEW_DISTANCE_CHUNKS = 2;
    private static final int MAX_VIEW_DISTANCE_CHUNKS = 32;
    private static final int WORLD_OPEN_TIMEOUT_TICKS = 2_400;
    private static final int POSITION_TIMEOUT_TICKS = 600;
    private static final int RENDER_TIMEOUT_TICKS = 1_200;
    private static final int STREAMING_DRAIN_TIMEOUT_TICKS = 2_400;
    // Let asynchronous terrain and lightmap work reach the same steady state
    // before comparing the first capture across loader render pipelines.
    private static final int CAPTURE_SETTLE_TICKS = 200;
    private static final double CAPTURE_CAMERA_Y = 120.0;
    private static final int CAPTURE_FOV = 70;
    private static final int CAPTURE_WIDTH = 1_280;
    private static final int CAPTURE_HEIGHT = 720;
    private int stage;
    private int settleTicks;
    private int atlasWaitTicks;
    private boolean waitingLogged;
    private int worldOpenTicks;
    private boolean worldOpenRequested;
    private boolean worldReadyLogged;
    private int completionTicks;
    private boolean focusPolicyApplied;
    private boolean captureSetupRequested;
    private volatile boolean captureSetupComplete;
    private volatile String captureSetupFailure;
    private int capturePoseWaitTicks;
    private int renderReadyWaitTicks;
    private CaptureEnvironment selectedEnvironment;
    private GraphicsStatus selectedGraphicsStatus;
    private boolean captureStageArmed;
    private float capturePitch;
    private long lastFrameNanos;
    private long totalFrameNanos;
    private long maxFrameNanos;
    private int frameSamples;
    private int slowFrames;
    private int streamingStableTicks;

    public boolean tick(Minecraft client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return false;
        applyFocusPolicy(client);
        if (stage >= 4) return true;
        if (!ensureWorldOpen(client)) return true;
        if (stage == 3) {
            if (Boolean.getBoolean(STREAMING_EDGE_PROPERTY)) {
                return drainStreamingCapture(client);
            }
            if (++completionTicks >= 20) finish(client, true,
                    "captures complete");
            return true;
        }
        if (client.screen instanceof PauseScreen) client.setScreen(null);
        if (client.screen != null) return true;

        RingGeometry geometry = ClientRingState.geometry();
        var atlas = ClientRingState.terrainAtlas();
        if (geometry == null || atlas == null || !atlas.isComplete()) {
            if (!waitingLogged) {
                waitingLogged = true;
                RingWorldMod.LOGGER.info(
                        "[projection-capture] waiting for complete terrain atlas");
            }
            if (++atlasWaitTicks % 600 == 0) {
                RingWorldMod.LOGGER.info(
                        "[projection-capture] atlas progress {}/{} cells",
                        atlas == null ? 0 : atlas.presentCount(),
                        atlas == null ? 0 : atlas.cellCount());
            }
            return true;
        }

        if (!ensureCaptureSetup(client, geometry)) return true;
        if (Boolean.getBoolean(STREAMING_EDGE_PROPERTY)) {
            return tickStreamingEdgeCapture(client, geometry, atlas);
        }
        int effectiveViewDistanceChunks = client.options.getEffectiveRenderDistance();
        int cameraChunkX = (int)Math.floor(client.player.getX()) >> 4;
        int cameraChunkZ = (int)Math.floor(client.player.getZ()) >> 4;
        int loadedPositiveX = contiguousLoadedChunks(
                client, cameraChunkX, cameraChunkZ, 1, effectiveViewDistanceChunks);
        int loadedNegativeX = contiguousLoadedChunks(
                client, cameraChunkX, cameraChunkZ, -1, effectiveViewDistanceChunks);
        if (loadedPositiveX < effectiveViewDistanceChunks
                || loadedNegativeX < effectiveViewDistanceChunks) {
            settleTicks = 0;
            if (++renderReadyWaitTicks % 200 == 1) {
                RingWorldMod.LOGGER.info(
                        "[projection-capture] waiting for full handoff radius: "
                                + "effectiveChunks={}, loadedX=+{}/-{}",
                        effectiveViewDistanceChunks,
                        loadedPositiveX, loadedNegativeX);
            }
            if (renderReadyWaitTicks > RENDER_TIMEOUT_TICKS) {
                finish(client, false,
                        "timed out loading the full contiguous handoff radius");
            }
            return true;
        }
        boolean streamingWindowComplete =
                RingSurfaceTextureRenderer.legacyStreamingWindowComplete();
        if (!client.levelRenderer.hasRenderedAllSections() || !streamingWindowComplete) {
            settleTicks = 0;
            if (renderReadyWaitTicks % 200 == 0) {
                RingWorldMod.LOGGER.info(
                        "[projection-capture] waiting for complete 2-D drawable window: "
                                + "renderedAllSections={}, streamingWindowComplete={}",
                        client.levelRenderer.hasRenderedAllSections(),
                        streamingWindowComplete);
            }
            if (++renderReadyWaitTicks > RENDER_TIMEOUT_TICKS) {
                finish(client, false,
                        "timed out rendering the complete 2-D capture window");
            }
            return true;
        }
        renderReadyWaitTicks = 0;
        if (!captureStageArmed) {
            armCaptureStage(client, geometry, atlas);
            return true;
        }
        client.player.setYRot(90.0F);
        client.player.setXRot(capturePitch);
        if (++settleTicks < CAPTURE_SETTLE_TICKS) return true;
        settleTicks = 0;

        if (stage == 0) {
            Screenshot.grab(
                    client.gameDirectory, screenshotName("tangent"),
                    client.getMainRenderTarget(), 1,
                    message -> RingWorldMod.LOGGER.info(
                            "[projection-capture] tangent screenshot: {}",
                            message.getString()));
            RingWorldMod.LOGGER.info(
                    "[projection-capture] tangent/along-ring view captured at C={}, R={}",
                    geometry.circumferenceBlocks(), geometry.radius());
            completeCaptureStage("tangent");
            return true;
        }

        if (stage == 1) {
            Screenshot.grab(
                    client.gameDirectory, screenshotName("handoff"),
                    client.getMainRenderTarget(), 1,
                    message -> RingWorldMod.LOGGER.info(
                            "[projection-capture] live/proxy handoff screenshot: {}",
                            message.getString()));
            RingWorldMod.LOGGER.info(
                    "[projection-capture] live/proxy handoff captured at pitch={}",
                    capturePitch);
            completeCaptureStage("handoff");
            return true;
        }

        Screenshot.grab(
                client.gameDirectory, screenshotName("up"),
                client.getMainRenderTarget(), 1,
                message -> RingWorldMod.LOGGER.info(
                        "[projection-capture] radial-up screenshot: {}",
                        message.getString()));
        RingWorldMod.LOGGER.info(
                "[projection-capture] radial/up view captured at C={}, diameter={}; complete",
                geometry.circumferenceBlocks(), geometry.radius() * 2.0);
        completeCaptureStage("radial-up");
        return true;
    }

    /**
     * Captures the backport's temporary complete-Atlas coverage floor while
     * the production world's real chunks are still arriving. This mode is
     * deliberately separate from the settled three-view projection gate: it
     * must observe a contiguous radius before the fixed 0.58V proxy onset,
     * bind the renderer's published fallback to an incomplete vanilla 2-D
     * chunk/compiled-section window, and stop before a settled view can make
     * the probe vacuous.
     */
    private boolean tickStreamingEdgeCapture(Minecraft client, RingGeometry geometry,
                                             RingTerrainAtlas atlas) {
        if (selectedEnvironment() != CaptureEnvironment.NOON) {
            finish(client, false, "streaming fallback requires the noon environment");
            return true;
        }
        if (client.options.graphicsMode().get() != selectedGraphicsStatus()
                || client.options.cloudStatus().get() != CloudStatus.OFF
                || client.options.fov().get() != CAPTURE_FOV
                || client.getWindow().getWidth() != CAPTURE_WIDTH
                || client.getWindow().getHeight() != CAPTURE_HEIGHT
                || !client.options.hideGui) {
            finish(client, false,
                    "streaming fallback capture policy changed: graphics="
                            + client.options.graphicsMode().get() + ", clouds="
                            + client.options.cloudStatus().get() + ", fov="
                            + client.options.fov().get() + ", hudHidden="
                            + client.options.hideGui + ", framebuffer="
                            + client.getWindow().getWidth() + "x"
                            + client.getWindow().getHeight());
            return true;
        }
        // This capture intentionally happens before ordinary weather
        // interpolation can settle. The authoritative integrated server has
        // already been normalized to clear; make the disposable client view
        // exact in the same early frame so rain cannot smooth the handoff ROI.
        client.level.setRainLevel(0.0F);
        client.level.setThunderLevel(0.0F);
        long dayTime = Math.floorMod(client.level.getDayTime(), 24_000L);
        float rainLevel = client.level.getRainLevel(1.0F);
        float thunderLevel = client.level.getThunderLevel(1.0F);
        if (dayTime != CaptureEnvironment.NOON.timeTicks
                || rainLevel > 0.001F || thunderLevel > 0.001F) {
            streamingStableTicks = 0;
            if (++renderReadyWaitTicks > RENDER_TIMEOUT_TICKS) {
                finish(client, false,
                        "timed out normalizing the early-streaming environment");
            }
            return true;
        }
        int requestedChunks = projectionViewDistanceChunks();
        int effectiveChunks = client.options.getEffectiveRenderDistance();
        if (effectiveChunks != requestedChunks) {
            finish(client, false,
                    "streaming capture effective radius did not match requested radius: "
                            + effectiveChunks + " != " + requestedChunks);
            return true;
        }

        int cameraChunkX = (int)Math.floor(client.player.getX()) >> 4;
        int cameraChunkZ = (int)Math.floor(client.player.getZ()) >> 4;
        int loadedPositiveX = contiguousLoadedChunks(
                client, cameraChunkX, cameraChunkZ, 1, effectiveChunks);
        int loadedNegativeX = contiguousLoadedChunks(
                client, cameraChunkX, cameraChunkZ, -1, effectiveChunks);
        int minimumLoadedChunks = Math.min(loadedPositiveX, loadedNegativeX);
        RingRenderProfile profile = RingRenderProfile.create(
                geometry, effectiveChunks * 16.0);
        double fixedProxyOnsetBlocks = 2.0 * profile.proxyFadeStartBlocks()
                - profile.liveFadeStartBlocks();

        if (minimumLoadedChunks * 16.0 >= fixedProxyOnsetBlocks) {
            finish(client, false,
                    "real chunks reached the fixed proxy onset before streaming capture: "
                            + "loadedX=+" + loadedPositiveX + "/-" + loadedNegativeX
                            + ", onsetBlocks=" + fixedProxyOnsetBlocks);
            return true;
        }
        if (minimumLoadedChunks < 2) {
            streamingStableTicks = 0;
            if (++renderReadyWaitTicks > RENDER_TIMEOUT_TICKS) {
                finish(client, false,
                        "timed out waiting for a bounded early streaming radius");
            }
            return true;
        }

        StreamingWindowProbe window = probeStreamingWindow(
                client, geometry, cameraChunkX, cameraChunkZ, effectiveChunks);
        double publishedStart = RingSurfaceTextureRenderer
                .legacyStreamingFadeStartBlocks();
        double publishedEnd = RingSurfaceTextureRenderer
                .legacyStreamingOpaqueFromBlocks();
        boolean publishedComplete = RingSurfaceTextureRenderer
                .legacyStreamingWindowComplete();
        double proxyDrawn = RingSurfaceTextureRenderer.legacyProxyDrawnThisFrame();
        if (window.complete || publishedComplete
                || !approximatelyEqual(0.0, publishedStart)
                || !approximatelyEqual(0.0, publishedEnd)
                || !approximatelyEqual(1.0, proxyDrawn)) {
            streamingStableTicks = 0;
            if (++renderReadyWaitTicks > RENDER_TIMEOUT_TICKS) {
                finish(client, false,
                        "renderer did not publish opaque fallback for an incomplete window");
            }
            return true;
        }
        if (window.renderedAllSections && !window.missingNonXChunk) {
            finish(client, false,
                    "streaming fixture did not observe a missing non-X chunk or compile lag");
            return true;
        }

        double targetDistance = Math.max(32.0, minimumLoadedChunks * 16.0);
        double targetHeight = atlas.sample(
                client.player.getX() - targetDistance,
                client.player.getZ()).height();
        capturePitch = (float)geometry.pitchDegreesToIntrinsic(
                client.player.getY(), targetHeight, targetDistance, 0.0);
        client.player.setYRot(90.0F);
        client.player.setXRot(capturePitch);
        if (!captureStageArmed) {
            captureStageArmed = true;
            resetFrameMetrics();
        }
        renderReadyWaitTicks = 0;
        if (++streamingStableTicks < 4) return true;

        RingWorldMod.LOGGER.info(
                "[projection-capture] streaming fallback armed at requestedChunks={}, "
                        + "effectiveChunks={}, loadedX=+{}/-{}, incompleteWindow={}, "
                        + "renderedAllSections={}, missingChunks={}, missingNonXChunk={}, "
                        + "firstMissingChunk=({},{}), fixedProxyOnsetBlocks={}, "
                        + "fallbackStartBlocks={}, fallbackOpaqueFromBlocks={}, "
                        + "proxyDrawn={}, targetDistance={}, pitch={}, surfaceY={}, graphics={}, "
                        + "clouds=off, hudHidden=true, fov={}, time={}, rain={}, "
                        + "thunder={}, framebuffer={}x{}",
                requestedChunks, effectiveChunks, loadedPositiveX, loadedNegativeX,
                !window.complete, window.renderedAllSections, window.missingChunks,
                window.missingNonXChunk, window.firstMissingChunkX,
                window.firstMissingChunkZ, fixedProxyOnsetBlocks, publishedStart,
                publishedEnd, proxyDrawn, targetDistance, capturePitch, targetHeight,
                selectedGraphicsStatus().name().toLowerCase(java.util.Locale.ROOT),
                CAPTURE_FOV, dayTime, rainLevel, thunderLevel,
                client.getWindow().getWidth(), client.getWindow().getHeight());
        Screenshot.grab(
                client.gameDirectory, "ringworld-projection-streaming-fallback.png",
                client.getMainRenderTarget(), 1,
                message -> RingWorldMod.LOGGER.info(
                        "[projection-capture] streaming fallback screenshot: {}",
                        message.getString()));
        completeStreamingCapture();
        return true;
    }

    private boolean drainStreamingCapture(Minecraft client) {
        RingGeometry geometry = ClientRingState.geometry();
        if (client.level == null || client.player == null || geometry == null) {
            finish(client, false,
                    "streaming capture lost its world before the delivery queue drained");
            return true;
        }
        int requestedChunks = projectionViewDistanceChunks();
        int effectiveChunks = client.options.getEffectiveRenderDistance();
        int cameraChunkX = (int)Math.floor(client.player.getX()) >> 4;
        int cameraChunkZ = (int)Math.floor(client.player.getZ()) >> 4;
        StreamingWindowProbe window = probeStreamingWindow(
                client, geometry, cameraChunkX, cameraChunkZ, effectiveChunks);
        boolean rendererComplete = RingSurfaceTextureRenderer
                .legacyStreamingWindowComplete();
        if (effectiveChunks != requestedChunks || !window.complete || !rendererComplete) {
            completionTicks = 0;
            if (++renderReadyWaitTicks % 200 == 1) {
                RingWorldMod.LOGGER.info(
                        "[projection-capture] waiting for post-capture streaming drain: "
                                + "requestedChunks={}, effectiveChunks={}, missingChunks={}, "
                                + "renderedAllSections={}, streamingWindowComplete={}",
                        requestedChunks, effectiveChunks, window.missingChunks,
                        window.renderedAllSections, rendererComplete);
            }
            if (renderReadyWaitTicks > STREAMING_DRAIN_TIMEOUT_TICKS) {
                finish(client, false,
                        "timed out draining the post-capture chunk/render queue");
            }
            return true;
        }
        renderReadyWaitTicks = 0;
        if (++completionTicks < 20) return true;
        RingWorldMod.LOGGER.info(
                "[projection-capture] streaming drain complete requestedChunks={}, "
                        + "missingChunks=0, renderedAllSections=true, "
                        + "streamingWindowComplete=true, stableTicks={}",
                requestedChunks, completionTicks);
        finish(client, true, "streaming fallback capture complete");
        return true;
    }

    private static boolean approximatelyEqual(double expected, double actual) {
        return Double.isFinite(actual) && Math.abs(expected - actual) <= 0.05;
    }

    private static StreamingWindowProbe probeStreamingWindow(
            Minecraft client, RingGeometry geometry, int cameraChunkX,
            int cameraChunkZ, int effectiveChunks) {
        boolean renderedAllSections = client.levelRenderer.hasRenderedAllSections();
        int missingChunks = 0;
        boolean missingNonXChunk = false;
        int firstMissingChunkX = cameraChunkX;
        int firstMissingChunkZ = cameraChunkZ;
        boolean firstMissingRecorded = false;
        for (int chunkX = cameraChunkX - effectiveChunks;
             chunkX <= cameraChunkX + effectiveChunks; chunkX++) {
            for (int chunkZ = cameraChunkZ - effectiveChunks;
                 chunkZ <= cameraChunkZ + effectiveChunks; chunkZ++) {
                if (!ChunkTrackingView.isInViewDistance(
                        cameraChunkX, cameraChunkZ, effectiveChunks, chunkX, chunkZ)
                        || geometry.isExteriorChunkZ(chunkZ)
                        || client.level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                missingChunks++;
                if (!firstMissingRecorded) {
                    firstMissingChunkX = chunkX;
                    firstMissingChunkZ = chunkZ;
                    firstMissingRecorded = true;
                }
                if (chunkZ != cameraChunkZ) missingNonXChunk = true;
            }
        }
        return new StreamingWindowProbe(
                renderedAllSections && missingChunks == 0,
                renderedAllSections, missingChunks, missingNonXChunk,
                firstMissingChunkX, firstMissingChunkZ);
    }

    public void frameRendered() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || !captureStageArmed) return;
        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            long elapsed = now - lastFrameNanos;
            totalFrameNanos += elapsed;
            maxFrameNanos = Math.max(maxFrameNanos, elapsed);
            frameSamples++;
            if (elapsed > 50_000_000L) slowFrames++;
        }
        lastFrameNanos = now;
    }

    private boolean ensureWorldOpen(Minecraft client) {
        if (client.player != null && client.level != null) {
            if (!worldReadyLogged) {
                RingWorldMod.LOGGER.info("[projection-capture] world '{}' ready", projectionWorld());
                worldReadyLogged = true;
            }
            return true;
        }
        if (++worldOpenTicks > WORLD_OPEN_TIMEOUT_TICKS) {
            finish(client, false, "timed out opening save '" + projectionWorld() + "'");
            return false;
        }
        if (!worldOpenRequested && client.isGameLoadFinished()
                && client.getSingleplayerServer() == null) {
            worldOpenRequested = true;
            RingWorldMod.LOGGER.info("[projection-capture] opening copied save '{}' in-process",
                    projectionWorld());
            client.createWorldOpenFlows().openWorld(projectionWorld(),
                    () -> finish(client, false,
                            "save load cancelled for '" + projectionWorld() + "'"));
        }
        return false;
    }

    /**
     * Projection capture is an unattended harness run. Gradle and the desktop
     * can move its window behind another application while atlas work is still
     * server-driven, so do not pause the integrated server merely for losing
     * focus. Keep the same inactive-frame policy as the other test clients.
     */
    private void applyFocusPolicy(Minecraft client) {
        client.options.pauseOnLostFocus = false;
        // Set the requested radius before opening the world. In 1.21.1 the
        // render-distance option callback only rebuilds the client renderer;
        // changing it after login does not send new ClientInformation to the
        // integrated server. That made nominal 16-chunk captures retain the
        // default 12-chunk server radius and falsely exercise a missing-chunk
        // shelf instead of the configured handoff.
        client.options.renderDistance().set(projectionViewDistanceChunks());
        client.options.graphicsMode().set(selectedGraphicsStatus());
        client.options.cloudStatus().set(CloudStatus.OFF);
        client.options.fov().set(CAPTURE_FOV);
        client.options.hideGui = true;
        if (client.getWindow().getWidth() != CAPTURE_WIDTH
                || client.getWindow().getHeight() != CAPTURE_HEIGHT) {
            resizeFramebuffer(client, CAPTURE_WIDTH, CAPTURE_HEIGHT);
        }
        if (focusPolicyApplied) return;
        focusPolicyApplied = true;
        RingWorldMod.LOGGER.info(
                "[projection-capture] applied unattended focus policy and pre-login "
                        + "view distance={} chunks, graphics={}, clouds=off, "
                        + "hudHidden=true, fov={}",
                projectionViewDistanceChunks(),
                selectedGraphicsStatus().name().toLowerCase(java.util.Locale.ROOT),
                CAPTURE_FOV);
    }

    private static void resizeFramebuffer(Minecraft client, int targetWidth,
                                          int targetHeight) {
        int framebufferWidth = client.getWindow().getWidth();
        int framebufferHeight = client.getWindow().getHeight();
        int screenWidth = client.getWindow().getScreenWidth();
        int screenHeight = client.getWindow().getScreenHeight();
        double pixelRatioX = screenWidth > 0
                ? (double)framebufferWidth / screenWidth : 1.0;
        double pixelRatioY = screenHeight > 0
                ? (double)framebufferHeight / screenHeight : 1.0;
        client.getWindow().setWindowed(
                Math.max(1, (int)Math.round(targetWidth / pixelRatioX)),
                Math.max(1, (int)Math.round(targetHeight / pixelRatioY)));
    }

    private boolean ensureCaptureSetup(Minecraft client, RingGeometry geometry) {
        String failure = captureSetupFailure;
        if (failure != null) {
            finish(client, false, failure);
            return false;
        }
        int viewDistance = projectionViewDistanceChunks();
        CaptureEnvironment environment = selectedEnvironment();
        double targetX = geometry.circumferenceBlocks() / 4.0;
        double targetZ = 0.5;
        if (!captureSetupRequested) {
            captureSetupRequested = true;
            RingIntegratedCaptureControl.execute(client, "projection setup",
                    context -> {
                        RingIntegratedCaptureControl.normalizeEnvironment(
                                context, environment.timeTicks, environment.raining);
                        RingIntegratedCaptureControl.teleport(
                                context, targetX, CAPTURE_CAMERA_Y, targetZ);
                    },
                    () -> captureSetupComplete = true,
                    detail -> captureSetupFailure = detail);
            RingWorldMod.LOGGER.info(
                    "[projection-capture] requested integrated-server environment={} and "
                            + "centered pose x={}, y={}, z={} at {} chunks",
                    environment.id, targetX, CAPTURE_CAMERA_Y, targetZ, viewDistance);
            return false;
        }
        if (!captureSetupComplete) return false;
        boolean atPosition = Math.abs(geometry.shortestCircumferenceDelta(
                targetX, client.player.getX())) < 1.5
                && Math.abs(client.player.getY() - CAPTURE_CAMERA_Y) < 1.5
                && Math.abs(client.player.getZ() - targetZ) < 1.5;
        if (!atPosition && ++capturePoseWaitTicks > POSITION_TIMEOUT_TICKS) {
            finish(client, false, "timed out reaching centered capture pose");
        }
        return atPosition;
    }

    private void armCaptureStage(Minecraft client, RingGeometry geometry,
                                 RingTerrainAtlas atlas) {
        if (stage == 0) {
            capturePitch = 0.0F;
            RingWorldMod.LOGGER.info("[projection-capture] tangent capture armed");
        } else if (stage == 1) {
            int effectiveViewDistanceChunks =
                    client.options.getEffectiveRenderDistance();
            int cameraChunkX = (int)Math.floor(client.player.getX()) >> 4;
            int cameraChunkZ = (int)Math.floor(client.player.getZ()) >> 4;
            int loadedPositiveX = contiguousLoadedChunks(
                    client, cameraChunkX, cameraChunkZ, 1, effectiveViewDistanceChunks);
            int loadedNegativeX = contiguousLoadedChunks(
                    client, cameraChunkX, cameraChunkZ, -1, effectiveViewDistanceChunks);
            double handoffViewDistanceBlocks = effectiveViewDistanceChunks * 16.0;
            RingRenderProfile profile = RingRenderProfile.create(
                    geometry, handoffViewDistanceBlocks);
            double targetDistance = profile.effectiveViewDistanceBlocks();
            double targetHeight = atlas.sample(
                    client.player.getX() - targetDistance,
                    client.player.getZ()).height();
            capturePitch = (float)geometry.pitchDegreesToIntrinsic(
                    client.player.getY(), targetHeight, targetDistance, 0.0);
            RingWorldMod.LOGGER.info(
                    "[projection-capture] handoff capture armed at requestedChunks={}, "
                            + "effectiveChunks={}, loadedX=+{}/-{}, handoffBlocks={}, "
                            + "pitch={}, surfaceY={}, proxyRevealScale={}, "
                            + "streamingWindowComplete={}",
                    projectionViewDistanceChunks(), effectiveViewDistanceChunks,
                    loadedPositiveX, loadedNegativeX, targetDistance,
                    capturePitch, targetHeight,
                    RingSurfaceTextureRenderer.legacyProxyRevealScale(),
                    RingSurfaceTextureRenderer.legacyStreamingWindowComplete());
        } else {
            capturePitch = -90.0F;
            RingWorldMod.LOGGER.info("[projection-capture] radial-up capture armed");
        }
        resetFrameMetrics();
        captureStageArmed = true;
    }

    private static int contiguousLoadedChunks(Minecraft client, int cameraChunkX,
                                              int cameraChunkZ, int stepX, int limit) {
        int loaded = 0;
        while (loaded < limit && client.level.getChunkSource().hasChunk(
                cameraChunkX + stepX * (loaded + 1), cameraChunkZ)) {
            loaded++;
        }
        return loaded;
    }

    private void completeCaptureStage(String label) {
        double averageMillis = frameSamples == 0
                ? 0.0 : totalFrameNanos / 1_000_000.0 / frameSamples;
        RingWorldMod.LOGGER.info(
                "[projection-capture] {} frame metrics: samples={}, average={} ms, max={} ms, over50ms={}",
                label, frameSamples, averageMillis,
                maxFrameNanos / 1_000_000.0, slowFrames);
        stage++;
        captureStageArmed = false;
    }

    private void completeStreamingCapture() {
        double averageMillis = frameSamples == 0
                ? 0.0 : totalFrameNanos / 1_000_000.0 / frameSamples;
        RingWorldMod.LOGGER.info(
                "[projection-capture] streaming-fallback frame metrics: samples={}, "
                        + "average={} ms, max={} ms, over50ms={}",
                frameSamples, averageMillis, maxFrameNanos / 1_000_000.0, slowFrames);
        stage = 3;
        captureStageArmed = false;
    }

    private void resetFrameMetrics() {
        lastFrameNanos = 0L;
        totalFrameNanos = 0L;
        maxFrameNanos = 0L;
        frameSamples = 0;
        slowFrames = 0;
    }

    private int projectionViewDistanceChunks() {
        String configured = System.getProperty(VIEW_DISTANCE_PROPERTY,
                Integer.toString(DEFAULT_VIEW_DISTANCE_CHUNKS)).trim();
        try {
            return Math.clamp(Integer.parseInt(configured),
                    MIN_VIEW_DISTANCE_CHUNKS, MAX_VIEW_DISTANCE_CHUNKS);
        } catch (NumberFormatException exception) {
            RingWorldMod.LOGGER.warn(
                    "[projection-capture] invalid {}='{}'; using {} chunks",
                    VIEW_DISTANCE_PROPERTY, configured, DEFAULT_VIEW_DISTANCE_CHUNKS);
            return DEFAULT_VIEW_DISTANCE_CHUNKS;
        }
    }

    private CaptureEnvironment selectedEnvironment() {
        if (selectedEnvironment != null) return selectedEnvironment;
        String configured = System.getProperty(
                ENVIRONMENT_PROPERTY, CaptureEnvironment.NOON.id).trim();
        for (CaptureEnvironment candidate : CaptureEnvironment.values()) {
            if (candidate.id.equalsIgnoreCase(configured)) {
                selectedEnvironment = candidate;
                return candidate;
            }
        }
        RingWorldMod.LOGGER.warn(
                "[projection-capture] invalid {}='{}'; using {}",
                ENVIRONMENT_PROPERTY, configured, CaptureEnvironment.NOON.id);
        selectedEnvironment = CaptureEnvironment.NOON;
        return selectedEnvironment;
    }

    private GraphicsStatus selectedGraphicsStatus() {
        if (selectedGraphicsStatus != null) return selectedGraphicsStatus;
        String configured = System.getProperty(
                GRAPHICS_MODE_PROPERTY, "fancy").trim();
        selectedGraphicsStatus = switch (configured.toLowerCase(java.util.Locale.ROOT)) {
            case "fast" -> GraphicsStatus.FAST;
            case "fabulous" -> GraphicsStatus.FABULOUS;
            case "fancy" -> GraphicsStatus.FANCY;
            default -> {
                RingWorldMod.LOGGER.warn(
                        "[projection-capture] invalid {}='{}'; using fancy",
                        GRAPHICS_MODE_PROPERTY, configured);
                yield GraphicsStatus.FANCY;
            }
        };
        return selectedGraphicsStatus;
    }

    private String screenshotName(String view) {
        CaptureEnvironment environment = selectedEnvironment();
        String environmentPart = environment == CaptureEnvironment.NOON
                ? "" : environment.id + "-";
        return "ringworld-projection-" + environmentPart + view + ".png";
    }

    private String projectionWorld() {
        return System.getProperty(WORLD_PROPERTY, "").trim();
    }

    private void finish(Minecraft client, boolean passed, String detail) {
        if (stage >= 4) return;
        stage = 4;
        RingWorldMod.LOGGER.info("[projection-capture] result={}, {}", passed, detail);
        client.stop();
    }

    private record StreamingWindowProbe(
            boolean complete,
            boolean renderedAllSections,
            int missingChunks,
            boolean missingNonXChunk,
            int firstMissingChunkX,
            int firstMissingChunkZ) { }

    private enum CaptureEnvironment {
        NOON("noon", 6_000, false),
        DUSK("dusk", 12_000, false),
        NIGHT("night", 18_000, false),
        RAIN("rain", 6_000, true);

        private final String id;
        private final int timeTicks;
        private final boolean raining;

        CaptureEnvironment(String id, int timeTicks, boolean raining) {
            this.id = id;
            this.timeTicks = timeTicks;
            this.raining = raining;
        }
    }
}
