package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.compat.Screenshot;
import dev.ringworld.client.render.RingDrawableSectionView;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRenderProfile;
import dev.ringworld.world.RingTerrainAtlas;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.CameraType;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Disposable production-world probe for camera-dependent accumulation in the
 * widened 1.21.1 live/Atlas handoff. Seven sparse, overlapping leaf planes
 * straddle the whole transition while the camera traverses them laterally and
 * returns to one exact pose from the opposite direction.
 */
public final class RingHandoffFoliageCaptureClient {
    public static final String ENABLE_PROPERTY =
            "ringworld.captureRingHandoffFoliage";
    private static final String WORLD_PROPERTY = "ringworld.handoffFoliageWorld";
    private static final int EXPECTED_CIRCUMFERENCE = 16_384;
    private static final int EXPECTED_WIDTH = 256;
    private static final long EXPECTED_WORLD_HASH = 0xC4F99D1076B39DE3L;
    private static final int EXPECTED_SAMPLE_STEP = 8;
    private static final int EXPECTED_ATLAS_CELLS = 65_536;
    private static final int VIEW_DISTANCE_CHUNKS = 16;
    private static final int CAPTURE_FOV = 70;
    private static final int CAPTURE_WIDTH = 1_280;
    private static final int CAPTURE_HEIGHT = 720;
    private static final double CAMERA_Y = 120.0;
    private static final double INITIAL_Z = -17.5;
    private static final double NEGATIVE_CAPTURE_Z = -8.5;
    private static final double CENTER_Z = 0.5;
    private static final double POSITIVE_START_Z = 18.5;
    private static final double POSITIVE_CAPTURE_Z = 9.5;
    private static final float CAMERA_YAW = 90.0F;
    private static final double TARGET_DISTANCE_BLOCKS = 256.0;
    // Keep every diagnostic layer inside Experiment 19's 0.68V-1.02V
    // live/Atlas overlap. Fully-live foreground foliage must not be able to
    // satisfy the visibility mask on behalf of the actual transition.
    private static final int[] PLANE_DISTANCES = {184, 192, 200, 208, 216, 232, 248};
    private static final int MIN_PLANE_Z = -32;
    private static final int MAX_PLANE_Z = 32;
    private static final int PLANE_BELOW_CENTER = 4;
    private static final int PLANE_ABOVE_CENTER = 28;
    private static final int EXPECTED_FIXTURE_BLOCKS = 7_508;
    private static final int EXPECTED_SENTINELS = PLANE_DISTANCES.length * 2;
    private static final int EXPECTED_NATURAL_STEPS = 144;
    private static final int EXPECTED_SCREENSHOTS = 9;
    private static final int MAX_STREAMING_COVERAGE_DROP_FRAMES = 64;
    private static final int MAX_STREAMING_COVERAGE_DROP_RUN = 32;
    private static final double NATURAL_STEP = 0.25;
    private static final double MAX_NATURAL_STEP = 0.26;
    private static final int SHORT_SETTLE_TICKS = 30;
    private static final int CENTER_SETTLE_TICKS = 120;
    private static final int INITIAL_SETTLE_TICKS = 60;
    private static final int ATLAS_QUIESCENCE_TICKS = 200;
    private static final int WORLD_OPEN_TIMEOUT_TICKS = 2_400;
    private static final int ATLAS_READY_TIMEOUT_TICKS = 2_400;
    private static final int RENDER_TIMEOUT_TICKS = 1_800;
    private static final int TOTAL_TIMEOUT_TICKS = 7_200;
    private static final float REVEAL_VISIBLE_MINIMUM = 0.95F;
    private static final float REVEAL_BLOCKED_MAXIMUM = 0.001F;

    private Stage stage = Stage.WAITING_FOR_WORLD;
    private int totalTicks;
    private int worldOpenTicks;
    private int atlasReadyTicks;
    private int captureGeometryWaitTicks;
    private int renderWaitTicks;
    private int settleTicks;
    private int completionTicks;
    private boolean focusPolicyApplied;
    private boolean worldOpenRequested;
    private boolean fixtureRequested;
    private volatile boolean initialPlacementComplete;
    private boolean fixtureBuildRequested;
    private volatile boolean fixtureCreated;
    private volatile int createdFixtureBlocks;
    private volatile String serverControlFailure;
    private boolean fixtureConfirmed;
    private boolean positiveTeleportRequested;
    private volatile boolean positiveTeleportComplete;
    private boolean blindnessRequested;
    private volatile boolean blindnessApplied;
    private boolean blindnessClearRequested;
    private volatile boolean blindnessCleared;
    private boolean fixtureClearRequested;
    private volatile boolean fixtureCleared;
    private volatile int clearedFixtureBlocks;
    private long envelopeFrameBaseline = -1L;
    private long controlFrameBaseline = -1L;
    private long sectionRebuildFrameBaseline = -1L;
    private long atlasRevision;
    private long observedAtlasRevision = Long.MIN_VALUE;
    private int atlasStableTicks;
    private boolean atlasRevisionArmed;
    private double cameraX;
    private double targetAtlasHeight;
    private float capturePitch;
    private int[] planeCenterYs;
    private int naturalSteps;
    private double maximumNaturalStep;
    private long renderedFrames;
    private boolean frameMetricsActive;
    private long lastFrameNanos;
    private long totalFrameNanos;
    private long maximumFrameNanos;
    private int frameSamples;
    private int slowFrames;
    private int streamingCoverageDropFrames;
    private int streamingCoverageDropRun;
    private int maximumStreamingCoverageDropRun;
    private int unsafeStreamingCoverageFrames;
    private float blockedRevealScale;
    private float blockedProxyDrawn;
    private final AtomicInteger successfulScreenshotWrites = new AtomicInteger();
    private final AtomicReference<String> screenshotFailure = new AtomicReference<>();

    public boolean tick(Minecraft client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return false;
        applyCapturePolicy(client);
        if (stage == Stage.DONE) return true;
        if (++totalTicks > TOTAL_TIMEOUT_TICKS) {
            return fail(client, "timed out in stage " + stage.id);
        }
        if (!ensureWorldOpen(client)) return true;
        if (client.screen instanceof PauseScreen) client.setScreen(null);
        if (client.screen != null || client.player == null || client.level == null) return true;

        String failure = serverControlFailure;
        if (failure != null) return fail(client, failure);

        RingGeometry geometry = ClientRingState.geometry();
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        if (geometry == null || atlas == null || !atlas.isComplete()) {
            if (++atlasReadyTicks > ATLAS_READY_TIMEOUT_TICKS) {
                return fail(client, "timed out waiting for a complete terrain Atlas");
            }
            return true;
        }
        if (geometry.circumferenceBlocks() != EXPECTED_CIRCUMFERENCE
                || geometry.widthBlocks() != EXPECTED_WIDTH) {
            return fail(client, "fixture requires a 16384x256 RingWorld, got "
                    + geometry.circumferenceBlocks() + "x" + geometry.widthBlocks());
        }
        if (atlas.worldHash() != EXPECTED_WORLD_HASH
                || atlas.sampleStep() != EXPECTED_SAMPLE_STEP
                || atlas.presentCount() != EXPECTED_ATLAS_CELLS
                || atlas.cellCount() != EXPECTED_ATLAS_CELLS) {
            return fail(client, "fixture requires the immutable complete production Atlas; got "
                    + "worldHash=" + Long.toUnsignedString(atlas.worldHash(), 16)
                    + ", sampleStep=" + atlas.sampleStep()
                    + ", cells=" + atlas.presentCount() + "/" + atlas.cellCount());
        }
        if (client.getWindow().getWidth() != CAPTURE_WIDTH
                || client.getWindow().getHeight() != CAPTURE_HEIGHT
                || client.options.fov().get() != CAPTURE_FOV) {
            if (++captureGeometryWaitTicks > RENDER_TIMEOUT_TICKS) {
                return fail(client, "capture geometry requires framebuffer="
                        + CAPTURE_WIDTH + "x" + CAPTURE_HEIGHT + " and fov=" + CAPTURE_FOV
                        + "; got framebuffer=" + client.getWindow().getWidth() + "x"
                        + client.getWindow().getHeight() + " and fov="
                        + client.options.fov().get());
            }
            return true;
        }
        captureGeometryWaitTicks = 0;

        if (!fixtureRequested) {
            requestFixture(client, geometry, atlas);
            return true;
        }
        if (atlasRevisionArmed && atlas.revision() != atlasRevision) {
            return fail(client, "terrain Atlas revision changed from " + atlasRevision
                    + " to " + atlas.revision());
        }

        return switch (stage) {
            case WAITING_FOR_WORLD -> true;
            case WAITING_FOR_FIXTURE -> waitForFixture(client, geometry, atlas);
            case REQUEST_SECTION_REBUILD -> requestSectionRebuild(client, geometry);
            case WAIT_SECTION_REBUILD_FALLBACK -> waitForSectionRebuildFallback(
                    client, atlas);
            case WAIT_SECTION_REBUILD_RECOVERY -> waitForSectionRebuildRecovery(
                    client, atlas);
            case MOVE_TO_NEGATIVE -> moveThenSettle(
                    client, NEGATIVE_CAPTURE_Z, Stage.SETTLE_NEGATIVE);
            case SETTLE_NEGATIVE -> settleAndCapture(
                    client, geometry, atlas, NEGATIVE_CAPTURE_Z,
                    SHORT_SETTLE_TICKS, "negative-z",
                    "ringworld-handoff-foliage-negative-z.png", Stage.MOVE_TO_CENTER_A);
            case MOVE_TO_CENTER_A -> moveThenSettle(client, CENTER_Z, Stage.SETTLE_CENTER_A);
            case SETTLE_CENTER_A -> settleAndCapture(
                    client, geometry, atlas, CENTER_Z, CENTER_SETTLE_TICKS,
                    "center-a", "ringworld-handoff-foliage-center-a.png",
                    Stage.CAPTURE_CENTER_A_CONTROL);
            case CAPTURE_CENTER_A_CONTROL -> captureControl(
                    client, geometry, atlas, CENTER_Z, "center-a-control",
                    "ringworld-handoff-foliage-center-a-control.png",
                    Stage.REQUEST_POSITIVE_TELEPORT);
            case REQUEST_POSITIVE_TELEPORT -> requestPositiveTeleport(client);
            case WAIT_POSITIVE_TELEPORT -> waitForPositiveTeleport(client, geometry);
            case MOVE_TO_POSITIVE -> moveThenSettle(
                    client, POSITIVE_CAPTURE_Z, Stage.SETTLE_POSITIVE);
            case SETTLE_POSITIVE -> settleAndCapture(
                    client, geometry, atlas, POSITIVE_CAPTURE_Z,
                    SHORT_SETTLE_TICKS, "positive-z",
                    "ringworld-handoff-foliage-positive-z.png", Stage.MOVE_TO_CENTER_B);
            case MOVE_TO_CENTER_B -> moveThenSettle(client, CENTER_Z, Stage.SETTLE_CENTER_B);
            case SETTLE_CENTER_B -> settleAndCapture(
                    client, geometry, atlas, CENTER_Z, CENTER_SETTLE_TICKS,
                    "center-b", "ringworld-handoff-foliage-center-b.png",
                    Stage.CAPTURE_CENTER_B_CONTROL);
            case CAPTURE_CENTER_B_CONTROL -> captureControl(
                    client, geometry, atlas, CENTER_Z, "center-b-control",
                    "ringworld-handoff-foliage-center-b-control.png",
                    Stage.REQUEST_BLINDNESS);
            case REQUEST_BLINDNESS -> requestBlindness(client);
            case WAIT_BLINDNESS -> waitForBlockedSkyEnvelope(client);
            case REQUEST_BLINDNESS_CLEAR -> requestBlindnessClear(client);
            case WAIT_BLINDNESS_CLEAR -> waitForRecoveredSkyEnvelope(client, atlas);
            case REQUEST_FIXTURE_CLEAR -> requestFixtureClear(client, geometry);
            case WAIT_FIXTURE_CLEAR -> waitForFixtureClear(client, geometry, atlas);
            case CAPTURE_EMPTY_CONTROL -> captureEmptyControl(
                    client, geometry, atlas);
            case COMPLETE -> complete(client);
            case DONE -> true;
        };
    }

    /** Called from each loader's existing end-of-level render callback. */
    public void frameRendered() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        renderedFrames++;
        if (!frameMetricsActive) return;
        if (!RingSurfaceTextureRenderer.legacyStreamingWindowComplete()) {
            streamingCoverageDropFrames++;
            streamingCoverageDropRun++;
            maximumStreamingCoverageDropRun = Math.max(
                    maximumStreamingCoverageDropRun, streamingCoverageDropRun);
            if (Math.abs(RingSurfaceTextureRenderer.legacyProxyDrawnThisFrame()
                            - 1.0F) > 0.001F
                    || Math.abs(RingSurfaceTextureRenderer
                            .legacyStreamingFadeStartBlocks()) > 0.001F
                    || Math.abs(RingSurfaceTextureRenderer
                            .legacyStreamingOpaqueFromBlocks()) > 0.001F) {
                unsafeStreamingCoverageFrames++;
            }
        } else {
            streamingCoverageDropRun = 0;
        }
        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            long elapsed = now - lastFrameNanos;
            totalFrameNanos += elapsed;
            maximumFrameNanos = Math.max(maximumFrameNanos, elapsed);
            frameSamples++;
            if (elapsed > 50_000_000L) slowFrames++;
        }
        lastFrameNanos = now;
    }

    private boolean ensureWorldOpen(Minecraft client) {
        if (client.player != null && client.level != null) {
            if (stage == Stage.WAITING_FOR_WORLD) stage = Stage.WAITING_FOR_FIXTURE;
            return true;
        }
        if (++worldOpenTicks > WORLD_OPEN_TIMEOUT_TICKS) {
            fail(client, "timed out opening copied save '" + worldName() + "'");
            return false;
        }
        if (!worldOpenRequested && client.isGameLoadFinished()
                && client.getSingleplayerServer() == null) {
            worldOpenRequested = true;
            RingWorldMod.LOGGER.info(
                    "[handoff-foliage-capture] opening copied save '{}' in-process",
                    worldName());
            client.createWorldOpenFlows().openWorld(worldName(),
                    () -> fail(client, "save load cancelled for '" + worldName() + "'"));
        }
        return false;
    }

    private void applyCapturePolicy(Minecraft client) {
        client.options.pauseOnLostFocus = false;
        client.options.renderDistance().set(VIEW_DISTANCE_CHUNKS);
        client.options.graphicsMode().set(GraphicsStatus.FANCY);
        client.options.cloudStatus().set(CloudStatus.OFF);
        client.options.setCameraType(CameraType.FIRST_PERSON);
        client.options.fov().set(CAPTURE_FOV);
        client.options.hideGui = true;
        if (client.getWindow().getWidth() != CAPTURE_WIDTH
                || client.getWindow().getHeight() != CAPTURE_HEIGHT) {
            resizeFramebuffer(client, CAPTURE_WIDTH, CAPTURE_HEIGHT);
        }
        if (client.level != null) {
            client.level.setRainLevel(0.0F);
            client.level.setThunderLevel(0.0F);
        }
        if (focusPolicyApplied) return;
        focusPolicyApplied = true;
        RingWorldMod.LOGGER.info(
                "[handoff-foliage-capture] applied pre-login view distance={}, "
                        + "graphics=fancy, clouds=off, hudHidden=true, "
                        + "camera=first_person, fov={}",
                VIEW_DISTANCE_CHUNKS, CAPTURE_FOV);
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

    private String capturePolicyFailure(Minecraft client) {
        long dayTime = Math.floorMod(client.level.getDayTime(), 24_000L);
        float rainLevel = client.level.getRainLevel(1.0F);
        float thunderLevel = client.level.getThunderLevel(1.0F);
        if (client.getWindow().getWidth() == CAPTURE_WIDTH
                && client.getWindow().getHeight() == CAPTURE_HEIGHT
                && client.options.graphicsMode().get() == GraphicsStatus.FANCY
                && client.options.cloudStatus().get() == CloudStatus.OFF
                && client.options.getCameraType() == CameraType.FIRST_PERSON
                && client.options.fov().get() == CAPTURE_FOV
                && client.options.hideGui
                && dayTime == 6_000L
                && rainLevel <= 0.001F
                && thunderLevel <= 0.001F) {
            return null;
        }
        return "capture policy changed: framebuffer="
                + client.getWindow().getWidth() + "x" + client.getWindow().getHeight()
                + ", graphics=" + client.options.graphicsMode().get()
                + ", clouds=" + client.options.cloudStatus().get()
                + ", camera=" + client.options.getCameraType()
                + ", hudHidden=" + client.options.hideGui
                + ", fov=" + client.options.fov().get()
                + ", time=" + dayTime + ", rain=" + rainLevel
                + ", thunder=" + thunderLevel;
    }

    private void requestFixture(Minecraft client, RingGeometry geometry,
                                RingTerrainAtlas atlas) {
        fixtureRequested = true;
        cameraX = geometry.circumferenceBlocks() / 4.0 + 0.5;
        RingIntegratedCaptureControl.execute(client, "handoff foliage initial placement",
                context -> {
                    RingIntegratedCaptureControl.normalizeEnvironment(context, 6_000, false);
                    context.player().teleportTo(context.world(), cameraX, CAMERA_Y, INITIAL_Z,
                            Set.<RelativeMovement>of(), CAMERA_YAW, 0.0F);
                    initialPlacementComplete = true;
                },
                () -> { },
                detail -> serverControlFailure = detail);
        RingWorldMod.LOGGER.info(
                "[handoff-foliage-capture] requested initial placement at x={}, y={}, z={}, "
                        + "worldHash={}, sampleStep={}, presentCells={}/{}, framebuffer={}x{}",
                cameraX, CAMERA_Y, INITIAL_Z,
                Long.toUnsignedString(atlas.worldHash(), 16), atlas.sampleStep(),
                atlas.presentCount(), atlas.cellCount(),
                client.getWindow().getWidth(), client.getWindow().getHeight());
    }

    private boolean waitForFixture(Minecraft client, RingGeometry geometry,
                                   RingTerrainAtlas atlas) {
        setCameraOrientation(client);
        if (!initialPlacementComplete || !atPose(client, INITIAL_Z)
                || !hasFullRadius(client)
                || !client.levelRenderer.hasRenderedAllSections()) {
            if (++renderWaitTicks > RENDER_TIMEOUT_TICKS) {
                return fail(client, "initial placement did not reach its exact loaded/rendered state");
            }
            return true;
        }
        if (!fixtureBuildRequested) {
            if (!atlasIsQuiescent(atlas, "pre-fixture")) return true;
            captureFinalAtlasGeometry(client, geometry, atlas);
            fixtureBuildRequested = true;
            int[] fixtureCenters = planeCenterYs.clone();
            RingIntegratedCaptureControl.execute(client, "handoff foliage fixture setup",
                    context -> {
                        int blocks = createFixture(context.world(), geometry, fixtureCenters);
                        if (blocks != EXPECTED_FIXTURE_BLOCKS) {
                            throw new IllegalStateException(
                                    "created " + blocks + " foliage blocks instead of "
                                            + EXPECTED_FIXTURE_BLOCKS);
                        }
                        createdFixtureBlocks = blocks;
                        context.player().teleportTo(context.world(), cameraX, CAMERA_Y, INITIAL_Z,
                                Set.<RelativeMovement>of(), CAMERA_YAW, capturePitch);
                        fixtureCreated = true;
                    },
                    () -> { },
                    detail -> serverControlFailure = detail);
            RingWorldMod.LOGGER.info(
                    "[handoff-foliage-capture] requested fixture at x={}, targetDistance={}, "
                            + "targetAtlasHeight={}, pitch={}, sourceRevision={}, centers={}",
                    cameraX, TARGET_DISTANCE_BLOCKS, targetAtlasHeight, capturePitch,
                    atlas.revision(), Arrays.toString(planeCenterYs));
            resetAtlasQuiescence();
            renderWaitTicks = 0;
            return true;
        }
        boolean exactPose = atPose(client, INITIAL_Z);
        boolean fullRadius = hasFullRadius(client);
        boolean renderedAllSections = client.levelRenderer.hasRenderedAllSections();
        boolean streamingWindowComplete =
                RingSurfaceTextureRenderer.legacyStreamingWindowComplete();
        boolean exactFixture = fixtureMatches(client);
        if (!fixtureCreated || !exactPose || !fullRadius || !renderedAllSections
                || !streamingWindowComplete || !exactFixture) {
            if (++renderWaitTicks % 200 == 1) {
                RingWorldMod.LOGGER.info(
                        "[handoff-foliage-capture] waiting post-fixture readiness "
                                + "created={}, pose={}, fullRadius={}, renderedAllSections={}, "
                                + "streamingWindowComplete={}, fixtureMatches={}",
                        fixtureCreated, exactPose, fullRadius, renderedAllSections,
                        streamingWindowComplete, exactFixture);
            }
            if (renderWaitTicks > RENDER_TIMEOUT_TICKS) {
                return fail(client, "fixture did not reach its exact loaded/rendered state");
            }
            return true;
        }
        if (!atlasIsQuiescent(atlas, "post-fixture")) return true;
        if (!fixtureConfirmed) {
            String geometryFailure = finalAtlasGeometryFailure(client, geometry, atlas);
            if (geometryFailure != null) return fail(client, geometryFailure);
            atlasRevision = atlas.revision();
            atlasRevisionArmed = true;
            fixtureConfirmed = true;
            RingWorldMod.LOGGER.info(
                    "[handoff-foliage-capture] fixture ready planes={}, blocks={}, "
                            + "sentinels={}, atlasRevision={}, targetDistance={}, "
                            + "targetAtlasHeight={}, pitch={}, centers={}",
                    PLANE_DISTANCES.length, createdFixtureBlocks, EXPECTED_SENTINELS,
                    atlasRevision, TARGET_DISTANCE_BLOCKS, targetAtlasHeight, capturePitch,
                    Arrays.toString(planeCenterYs));
        }
        float revealScale = RingSurfaceTextureRenderer.legacyProxyRevealScale();
        if (revealScale < REVEAL_VISIBLE_MINIMUM) {
            if (++renderWaitTicks > RENDER_TIMEOUT_TICKS) {
                return fail(client, "clear complete-Atlas proxy reveal scale did not recover: "
                        + revealScale);
            }
            return true;
        }
        if (++settleTicks < INITIAL_SETTLE_TICKS) return true;
        if (!RingSurfaceTextureRenderer.legacyStreamingWindowComplete()) {
            settleTicks = 0;
            return true;
        }
        String policyFailure = capturePolicyFailure(client);
        if (policyFailure != null) return fail(client, policyFailure);
        int effective = client.options.getEffectiveRenderDistance();
        int cameraChunkX = (int)Math.floor(client.player.getX()) >> 4;
        int cameraChunkZ = (int)Math.floor(client.player.getZ()) >> 4;
        int loadedPositive = contiguousLoadedChunks(
                client, cameraChunkX, cameraChunkZ, 1, effective);
        int loadedNegative = contiguousLoadedChunks(
                client, cameraChunkX, cameraChunkZ, -1, effective);
        RingWorldMod.LOGGER.info(
                "[handoff-foliage-capture] settled requestedChunks={}, effectiveChunks={}, "
                        + "loadedX=+{}/-{}, atlasRevision={}, proxyRevealScale={}, "
                        + "streamingWindowComplete={}, camera=first_person, "
                        + "graphics=fancy, clouds=off, "
                        + "hudHidden=true, fov={}, time={}, rain={}, thunder={}, "
                        + "framebuffer={}x{}",
                VIEW_DISTANCE_CHUNKS, effective, loadedPositive, loadedNegative,
                atlas.revision(), revealScale, true, CAPTURE_FOV,
                Math.floorMod(client.level.getDayTime(), 24_000L),
                client.level.getRainLevel(1.0F), client.level.getThunderLevel(1.0F),
                client.getWindow().getWidth(), client.getWindow().getHeight());
        settleTicks = 0;
        renderWaitTicks = 0;
        stage = Stage.REQUEST_SECTION_REBUILD;
        return true;
    }

    private boolean requestSectionRebuild(Minecraft client, RingGeometry geometry) {
        setCameraOrientation(client);
        if (!atPose(client, INITIAL_Z) || !hasFullRadius(client)
                || !fixtureMatches(client)) {
            return fail(client, "section-rebuild probe lost its exact fixture state");
        }
        client.levelRenderer.allChanged();
        boolean queueEmptyImmediately = client.levelRenderer.hasRenderedAllSections();
        boolean bridgeCompleteImmediately = client.levelRenderer instanceof RingDrawableSectionView view
                && view.ringworld$hasCompiledSectionsInsideProxyHole(
                        geometry, client.gameRenderer.getMainCamera().getPosition(),
                        client.options.getEffectiveRenderDistance(),
                        RingRenderProfile.create(geometry,
                                client.options.getEffectiveRenderDistance() * 16.0)
                                .proxyFadeStartBlocks());
        RingWorldMod.LOGGER.info(
                "[handoff-foliage-capture] reset-view requested "
                        + "queueEmptyImmediately={}, bridgeCompleteImmediately={}, "
                        + "camera=first_person",
                queueEmptyImmediately, bridgeCompleteImmediately);
        if (!queueEmptyImmediately || bridgeCompleteImmediately) {
            return fail(client, "allChanged regression did not create the required "
                    + "queue-empty/uncompiled state: queueEmptyImmediately="
                    + queueEmptyImmediately + ", bridgeCompleteImmediately="
                    + bridgeCompleteImmediately);
        }
        sectionRebuildFrameBaseline = renderedFrames;
        renderWaitTicks = 0;
        stage = Stage.WAIT_SECTION_REBUILD_FALLBACK;
        return true;
    }

    private boolean waitForSectionRebuildFallback(Minecraft client,
                                                   RingTerrainAtlas atlas) {
        setCameraOrientation(client);
        if (renderedFrames <= sectionRebuildFrameBaseline) {
            if (++renderWaitTicks > RENDER_TIMEOUT_TICKS) {
                return fail(client, "section-rebuild probe did not render a fallback frame");
            }
            return true;
        }
        boolean windowComplete = RingSurfaceTextureRenderer.legacyStreamingWindowComplete();
        float fallbackStart = RingSurfaceTextureRenderer.legacyStreamingFadeStartBlocks();
        float fallbackOpaqueFrom =
                RingSurfaceTextureRenderer.legacyStreamingOpaqueFromBlocks();
        float proxyDrawn = RingSurfaceTextureRenderer.legacyProxyDrawnThisFrame();
        if (windowComplete || Math.abs(fallbackStart) > 0.001F
                || Math.abs(fallbackOpaqueFrom) > 0.001F
                || Math.abs(proxyDrawn - 1.0F) > 0.001F) {
            return fail(client, "section-rebuild fallback frame was not Atlas-owned: "
                    + "streamingWindowComplete=" + windowComplete
                    + ", fallbackStart=" + fallbackStart
                    + ", fallbackOpaqueFrom=" + fallbackOpaqueFrom
                    + ", proxyDrawn=" + proxyDrawn);
        }
        Screenshot.grab(client.gameDirectory,
                "ringworld-handoff-foliage-reset-fallback.png",
                client.getMainRenderTarget(), 1,
                message -> {
                    if (message.getContents() instanceof TranslatableContents translated
                            && "screenshot.success".equals(translated.getKey())) {
                        int completed = successfulScreenshotWrites.incrementAndGet();
                        RingWorldMod.LOGGER.info(
                                "[handoff-foliage-capture] reset-view screenshot: {}; "
                                        + "writes={}/{}",
                                message.getString(), completed, EXPECTED_SCREENSHOTS);
                    } else {
                        screenshotFailure.compareAndSet(null,
                                "reset-view screenshot failed: " + message.getString());
                        RingWorldMod.LOGGER.error(
                                "[handoff-foliage-capture] reset-view screenshot failure: {}",
                                message.getString());
                    }
                });
        RingWorldMod.LOGGER.info(
                "[handoff-foliage-capture] reset-view fallback "
                        + "queueEmptyImmediately=true, bridgeCompleteImmediately=false, "
                        + "streamingWindowComplete={}, fallbackStart={}, "
                        + "fallbackOpaqueFrom={}, proxyDrawn={}, camera=first_person, "
                        + "atlasRevision={}",
                windowComplete, fallbackStart, fallbackOpaqueFrom, proxyDrawn,
                atlas.revision());
        sectionRebuildFrameBaseline = -1L;
        settleTicks = 0;
        renderWaitTicks = 0;
        stage = Stage.WAIT_SECTION_REBUILD_RECOVERY;
        return true;
    }

    private boolean waitForSectionRebuildRecovery(Minecraft client,
                                                   RingTerrainAtlas atlas) {
        setCameraOrientation(client);
        int viewDistanceBlocks = client.options.getEffectiveRenderDistance() * 16;
        boolean recovered = atPose(client, INITIAL_Z) && hasFullRadius(client)
                && client.levelRenderer.hasRenderedAllSections()
                && RingSurfaceTextureRenderer.legacyStreamingWindowComplete()
                && Math.abs(RingSurfaceTextureRenderer.legacyStreamingFadeStartBlocks()
                        - viewDistanceBlocks) <= 0.001F
                && Math.abs(RingSurfaceTextureRenderer.legacyStreamingOpaqueFromBlocks()
                        - viewDistanceBlocks) <= 0.001F
                && Math.abs(RingSurfaceTextureRenderer.legacyProxyDrawnThisFrame()
                        - 1.0F) <= 0.001F
                && fixtureMatches(client);
        if (!recovered) {
            sectionRebuildFrameBaseline = -1L;
            if (++renderWaitTicks > RENDER_TIMEOUT_TICKS) {
                return fail(client, "section-rebuild probe did not recover complete coverage");
            }
            return true;
        }
        if (sectionRebuildFrameBaseline < 0L) {
            sectionRebuildFrameBaseline = renderedFrames;
            return true;
        }
        if (renderedFrames - sectionRebuildFrameBaseline < 2L) return true;
        RingWorldMod.LOGGER.info(
                "[handoff-foliage-capture] reset-view recovery "
                        + "streamingWindowComplete=true, fadeStart={}, opaqueFrom={}, "
                        + "proxyDrawn={}, camera=first_person, atlasRevision={}",
                RingSurfaceTextureRenderer.legacyStreamingFadeStartBlocks(),
                RingSurfaceTextureRenderer.legacyStreamingOpaqueFromBlocks(),
                RingSurfaceTextureRenderer.legacyProxyDrawnThisFrame(),
                atlas.revision());
        startFrameMetrics();
        sectionRebuildFrameBaseline = -1L;
        settleTicks = 0;
        renderWaitTicks = 0;
        stage = Stage.MOVE_TO_NEGATIVE;
        return true;
    }

    private boolean atlasIsQuiescent(RingTerrainAtlas atlas, String phase) {
        long revision = atlas.revision();
        if (revision != observedAtlasRevision) {
            observedAtlasRevision = revision;
            atlasStableTicks = 0;
            return false;
        }
        atlasStableTicks++;
        if (atlasStableTicks == ATLAS_QUIESCENCE_TICKS) {
            RingWorldMod.LOGGER.info(
                    "[handoff-foliage-capture] Atlas quiescent phase={}, revision={}, "
                            + "stableTicks={}",
                    phase, revision, atlasStableTicks);
        }
        return atlasStableTicks >= ATLAS_QUIESCENCE_TICKS;
    }

    private void resetAtlasQuiescence() {
        observedAtlasRevision = Long.MIN_VALUE;
        atlasStableTicks = 0;
    }

    private void captureFinalAtlasGeometry(Minecraft client, RingGeometry geometry,
                                           RingTerrainAtlas atlas) {
        targetAtlasHeight = atlas.sample(
                cameraX - TARGET_DISTANCE_BLOCKS, CENTER_Z).height();
        capturePitch = (float)geometry.pitchDegreesToIntrinsic(
                CAMERA_Y, targetAtlasHeight, TARGET_DISTANCE_BLOCKS, 0.0);
        planeCenterYs = projectedPlaneCenters(client, geometry, capturePitch);
    }

    private String finalAtlasGeometryFailure(Minecraft client, RingGeometry geometry,
                                             RingTerrainAtlas atlas) {
        double finalHeight = atlas.sample(
                cameraX - TARGET_DISTANCE_BLOCKS, CENTER_Z).height();
        float finalPitch = (float)geometry.pitchDegreesToIntrinsic(
                CAMERA_Y, finalHeight, TARGET_DISTANCE_BLOCKS, 0.0);
        int[] finalCenters = projectedPlaneCenters(client, geometry, finalPitch);
        if (Math.abs(finalHeight - targetAtlasHeight) > 1.0E-6
                || Math.abs(finalPitch - capturePitch) > 1.0E-6F
                || !Arrays.equals(finalCenters, planeCenterYs)) {
            return "final Atlas geometry changed after fixture setup: height="
                    + targetAtlasHeight + "->" + finalHeight + ", pitch="
                    + capturePitch + "->" + finalPitch + ", centers="
                    + Arrays.toString(planeCenterYs) + "->" + Arrays.toString(finalCenters);
        }
        return null;
    }

    private int[] projectedPlaneCenters(Minecraft client, RingGeometry geometry, float pitch) {
        int[] centers = new int[PLANE_DISTANCES.length];
        for (int index = 0; index < PLANE_DISTANCES.length; index++) {
            centers[index] = nearestRayBlockY(
                    geometry, pitch, PLANE_DISTANCES[index],
                    client.level.getMinBuildHeight(), client.level.getMaxBuildHeight());
        }
        return centers;
    }

    private boolean moveThenSettle(Minecraft client, double targetZ, Stage nextStage) {
        setCameraOrientation(client);
        if (!atFixedXY(client)) {
            return fail(client, "camera left the fixed foliage traverse plane at stage " + stage.id);
        }
        double currentZ = client.player.getZ();
        double remaining = targetZ - currentZ;
        if (Math.abs(remaining) <= 1.0E-9) {
            stage = nextStage;
            settleTicks = 0;
            renderWaitTicks = 0;
            return true;
        }
        double nextZ = currentZ + Math.clamp(remaining, -NATURAL_STEP, NATURAL_STEP);
        double step = Math.abs(nextZ - currentZ);
        maximumNaturalStep = Math.max(maximumNaturalStep, step);
        if (step > MAX_NATURAL_STEP) {
            return fail(client, "natural foliage traverse step exceeded "
                    + MAX_NATURAL_STEP + ": " + step);
        }
        naturalSteps++;
        client.player.setPos(cameraX, CAMERA_Y, nextZ);
        setCameraOrientation(client);
        client.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                cameraX, CAMERA_Y, nextZ, CAMERA_YAW, capturePitch,
                client.player.onGround()));
        return true;
    }

    private boolean settleAndCapture(Minecraft client, RingGeometry geometry,
                                     RingTerrainAtlas atlas, double expectedZ,
                                     int requiredTicks, String id,
                                     String screenshotName, Stage nextStage) {
        setCameraOrientation(client);
        if (!atPose(client, expectedZ)) {
            return fail(client, id + " did not retain its exact pose");
        }
        if (!hasFullRadius(client) || !client.levelRenderer.hasRenderedAllSections()
                || !RingSurfaceTextureRenderer.legacyStreamingWindowComplete()) {
            if (++renderWaitTicks > RENDER_TIMEOUT_TICKS) {
                return fail(client, id + " did not regain the full rendered radius");
            }
            return true;
        }
        if (++settleTicks < requiredTicks) return true;
        if (!fixtureMatches(client)) {
            return fail(client, id + " foliage fixture changed before capture");
        }
        float revealScale = RingSurfaceTextureRenderer.legacyProxyRevealScale();
        if (revealScale < REVEAL_VISIBLE_MINIMUM) {
            return fail(client, id + " complete clear Atlas reveal scale=" + revealScale);
        }
        String policyFailure = capturePolicyFailure(client);
        if (policyFailure != null) return fail(client, id + " " + policyFailure);
        Screenshot.grab(client.gameDirectory, screenshotName,
                client.getMainRenderTarget(), 1,
                message -> {
                    if (message.getContents() instanceof TranslatableContents translated
                            && "screenshot.success".equals(translated.getKey())) {
                        int completed = successfulScreenshotWrites.incrementAndGet();
                        RingWorldMod.LOGGER.info(
                                "[handoff-foliage-capture] {} screenshot: {}; writes={}/{}",
                                id, message.getString(), completed, EXPECTED_SCREENSHOTS);
                    } else {
                        screenshotFailure.compareAndSet(null,
                                id + " screenshot failed: " + message.getString());
                        RingWorldMod.LOGGER.error(
                                "[handoff-foliage-capture] {} screenshot failure: {}",
                                id, message.getString());
                    }
                });
        RingWorldMod.LOGGER.info(
                "[handoff-foliage-capture] captured {} at x={}, y={}, z={}, yaw={}, "
                        + "pitch={}, atlasRevision={}, proxyRevealScale={}, "
                        + "streamingWindowComplete={}, camera=first_person, "
                        + "graphics=fancy, clouds=off, "
                        + "hudHidden=true, fov={}, time={}, rain={}, thunder={}, "
                        + "framebuffer={}x{}",
                id, client.player.getX(), client.player.getY(), client.player.getZ(),
                client.player.getYRot(), client.player.getXRot(),
                atlas.revision(), revealScale, true, CAPTURE_FOV,
                Math.floorMod(client.level.getDayTime(), 24_000L),
                client.level.getRainLevel(1.0F), client.level.getThunderLevel(1.0F),
                client.getWindow().getWidth(), client.getWindow().getHeight());
        stage = nextStage;
        settleTicks = 0;
        renderWaitTicks = 0;
        controlFrameBaseline = -1L;
        return true;
    }

    private boolean captureControl(Minecraft client, RingGeometry geometry,
                                   RingTerrainAtlas atlas, double expectedZ,
                                   String id, String screenshotName, Stage nextStage) {
        setCameraOrientation(client);
        if (!atPose(client, expectedZ) || !hasFullRadius(client)
                || !client.levelRenderer.hasRenderedAllSections()
                || !RingSurfaceTextureRenderer.legacyStreamingWindowComplete()
                || !fixtureMatches(client)) {
            if (++renderWaitTicks > RENDER_TIMEOUT_TICKS) {
                return fail(client, id + " did not retain the exact foliage capture state");
            }
            controlFrameBaseline = -1L;
            return true;
        }
        if (controlFrameBaseline < 0L) {
            controlFrameBaseline = renderedFrames;
            return true;
        }
        if (renderedFrames - controlFrameBaseline < 2L) return true;
        return captureAtCurrentPose(client, atlas, id, screenshotName, nextStage);
    }

    private boolean captureAtCurrentPose(Minecraft client, RingTerrainAtlas atlas,
                                         String id, String screenshotName,
                                         Stage nextStage) {
        if (!RingSurfaceTextureRenderer.legacyStreamingWindowComplete()) {
            return fail(client, id + " lost its complete 2-D streaming window");
        }
        float revealScale = RingSurfaceTextureRenderer.legacyProxyRevealScale();
        if (revealScale < REVEAL_VISIBLE_MINIMUM) {
            return fail(client, id + " complete clear Atlas reveal scale=" + revealScale);
        }
        String policyFailure = capturePolicyFailure(client);
        if (policyFailure != null) return fail(client, id + " " + policyFailure);
        Screenshot.grab(client.gameDirectory, screenshotName,
                client.getMainRenderTarget(), 1,
                message -> {
                    if (message.getContents() instanceof TranslatableContents translated
                            && "screenshot.success".equals(translated.getKey())) {
                        int completed = successfulScreenshotWrites.incrementAndGet();
                        RingWorldMod.LOGGER.info(
                                "[handoff-foliage-capture] {} screenshot: {}; writes={}/{}",
                                id, message.getString(), completed, EXPECTED_SCREENSHOTS);
                    } else {
                        screenshotFailure.compareAndSet(null,
                                id + " screenshot failed: " + message.getString());
                        RingWorldMod.LOGGER.error(
                                "[handoff-foliage-capture] {} screenshot failure: {}",
                                id, message.getString());
                    }
                });
        RingWorldMod.LOGGER.info(
                "[handoff-foliage-capture] captured {} at x={}, y={}, z={}, yaw={}, "
                        + "pitch={}, atlasRevision={}, proxyRevealScale={}, "
                        + "streamingWindowComplete={}, camera=first_person, "
                        + "graphics=fancy, clouds=off, "
                        + "hudHidden=true, fov={}, time={}, rain={}, thunder={}, "
                        + "framebuffer={}x{}",
                id, client.player.getX(), client.player.getY(), client.player.getZ(),
                client.player.getYRot(), client.player.getXRot(),
                atlas.revision(), revealScale, true, CAPTURE_FOV,
                Math.floorMod(client.level.getDayTime(), 24_000L),
                client.level.getRainLevel(1.0F), client.level.getThunderLevel(1.0F),
                client.getWindow().getWidth(), client.getWindow().getHeight());
        stage = nextStage;
        settleTicks = 0;
        renderWaitTicks = 0;
        controlFrameBaseline = -1L;
        return true;
    }

    private boolean requestPositiveTeleport(Minecraft client) {
        if (!positiveTeleportRequested) {
            positiveTeleportRequested = true;
            RingIntegratedCaptureControl.execute(client, "handoff foliage positive-Z reset",
                    context -> context.player().teleportTo(
                            context.world(), cameraX, CAMERA_Y, POSITIVE_START_Z,
                            Set.<RelativeMovement>of(), CAMERA_YAW, capturePitch),
                    () -> positiveTeleportComplete = true,
                    detail -> serverControlFailure = detail);
        }
        stage = Stage.WAIT_POSITIVE_TELEPORT;
        renderWaitTicks = 0;
        return true;
    }

    private boolean waitForPositiveTeleport(Minecraft client, RingGeometry geometry) {
        setCameraOrientation(client);
        if (!positiveTeleportComplete || !atPose(client, POSITIVE_START_Z)
                || !hasFullRadius(client)
                || !client.levelRenderer.hasRenderedAllSections()) {
            if (++renderWaitTicks > RENDER_TIMEOUT_TICKS) {
                return fail(client, "positive-Z server reset did not settle");
            }
            return true;
        }
        stage = Stage.MOVE_TO_POSITIVE;
        renderWaitTicks = 0;
        return true;
    }

    private boolean requestBlindness(Minecraft client) {
        if (naturalSteps != EXPECTED_NATURAL_STEPS
                || maximumNaturalStep > MAX_NATURAL_STEP) {
            return fail(client, "natural traverse metrics were steps=" + naturalSteps
                    + ", maxStep=" + maximumNaturalStep);
        }
        finishFrameMetrics();
        if (unsafeStreamingCoverageFrames != 0
                || streamingCoverageDropFrames > MAX_STREAMING_COVERAGE_DROP_FRAMES
                || maximumStreamingCoverageDropRun > MAX_STREAMING_COVERAGE_DROP_RUN
                || streamingCoverageDropFrames * 50L > frameSamples) {
            return fail(client, "legacy streaming fallback exceeded its bounded safe "
                    + "motion envelope: drops=" + streamingCoverageDropFrames
                    + ", maxRun=" + maximumStreamingCoverageDropRun
                    + ", unsafe=" + unsafeStreamingCoverageFrames
                    + ", samples=" + frameSamples);
        }
        if (!blindnessRequested) {
            blindnessRequested = true;
            RingIntegratedCaptureControl.execute(client, "handoff foliage blocked-sky probe",
                    context -> {
                        context.player().addEffect(new MobEffectInstance(
                                MobEffects.BLINDNESS, 600, 0));
                        blindnessApplied = true;
                    },
                    () -> { },
                    detail -> serverControlFailure = detail);
        }
        envelopeFrameBaseline = -1L;
        stage = Stage.WAIT_BLINDNESS;
        return true;
    }

    private boolean waitForBlockedSkyEnvelope(Minecraft client) {
        setCameraOrientation(client);
        if (!blindnessApplied || !client.player.hasEffect(MobEffects.BLINDNESS)) return true;
        if (envelopeFrameBaseline < 0L) {
            envelopeFrameBaseline = renderedFrames;
            return true;
        }
        if (renderedFrames - envelopeFrameBaseline < 2L) return true;
        blockedRevealScale = RingSurfaceTextureRenderer.legacyProxyRevealScale();
        blockedProxyDrawn = RingSurfaceTextureRenderer.legacyProxyDrawnThisFrame();
        if (Math.abs(blockedRevealScale) > REVEAL_BLOCKED_MAXIMUM
                || Math.abs(blockedProxyDrawn) > REVEAL_BLOCKED_MAXIMUM) {
            return fail(client, "blocked sky retained stale proxy state: revealScale="
                    + blockedRevealScale + ", proxyDrawn=" + blockedProxyDrawn);
        }
        stage = Stage.REQUEST_BLINDNESS_CLEAR;
        envelopeFrameBaseline = -1L;
        return true;
    }

    private boolean requestBlindnessClear(Minecraft client) {
        if (!blindnessClearRequested) {
            blindnessClearRequested = true;
            RingIntegratedCaptureControl.execute(client, "handoff foliage blocked-sky recovery",
                    context -> {
                        context.player().removeEffect(MobEffects.BLINDNESS);
                        blindnessCleared = true;
                    },
                    () -> { },
                    detail -> serverControlFailure = detail);
        }
        stage = Stage.WAIT_BLINDNESS_CLEAR;
        return true;
    }

    private boolean waitForRecoveredSkyEnvelope(Minecraft client,
                                                 RingTerrainAtlas atlas) {
        setCameraOrientation(client);
        if (!blindnessCleared || client.player.hasEffect(MobEffects.BLINDNESS)) return true;
        if (envelopeFrameBaseline < 0L) {
            envelopeFrameBaseline = renderedFrames;
            return true;
        }
        if (renderedFrames - envelopeFrameBaseline < 2L) return true;
        float recoveredScale = RingSurfaceTextureRenderer.legacyProxyRevealScale();
        float recoveredDrawn = RingSurfaceTextureRenderer.legacyProxyDrawnThisFrame();
        if (recoveredScale < REVEAL_VISIBLE_MINIMUM || recoveredScale > 1.001F
                || Math.abs(recoveredDrawn - 1.0F) > 0.001F
                || !RingSurfaceTextureRenderer.legacyStreamingWindowComplete()) {
            return fail(client, "proxy state did not recover after blocked sky: revealScale="
                    + recoveredScale + ", proxyDrawn=" + recoveredDrawn);
        }
        RingWorldMod.LOGGER.info(
                "[handoff-foliage-capture] blocked-sky envelope blockedScale={}, "
                        + "blockedDrawn={}, recoveredScale={}, recoveredDrawn={}, "
                        + "atlasRevision={}",
                blockedRevealScale, blockedProxyDrawn, recoveredScale,
                recoveredDrawn, atlas.revision());
        stage = Stage.REQUEST_FIXTURE_CLEAR;
        return true;
    }

    private boolean requestFixtureClear(Minecraft client, RingGeometry geometry) {
        if (!fixtureClearRequested) {
            fixtureClearRequested = true;
            atlasRevisionArmed = false;
            resetAtlasQuiescence();
            RingIntegratedCaptureControl.execute(client, "handoff foliage fixture clear",
                    context -> {
                        int cleared = clearFixture(context.world(), geometry, planeCenterYs);
                        if (cleared != EXPECTED_FIXTURE_BLOCKS) {
                            throw new IllegalStateException(
                                    "cleared " + cleared + " foliage blocks instead of "
                                            + EXPECTED_FIXTURE_BLOCKS);
                        }
                        clearedFixtureBlocks = cleared;
                        context.player().teleportTo(context.world(), cameraX, CAMERA_Y, CENTER_Z,
                                Set.<RelativeMovement>of(), CAMERA_YAW, capturePitch);
                        fixtureCleared = true;
                    },
                    () -> { },
                    detail -> serverControlFailure = detail);
        }
        stage = Stage.WAIT_FIXTURE_CLEAR;
        settleTicks = 0;
        renderWaitTicks = 0;
        return true;
    }

    private boolean waitForFixtureClear(Minecraft client, RingGeometry geometry,
                                        RingTerrainAtlas atlas) {
        setCameraOrientation(client);
        if (!fixtureCleared || !atPose(client, CENTER_Z)
                || !hasFullRadius(client)
                || !client.levelRenderer.hasRenderedAllSections()
                || !RingSurfaceTextureRenderer.legacyStreamingWindowComplete()
                || !fixtureIsCleared(client)) {
            if (++renderWaitTicks > RENDER_TIMEOUT_TICKS) {
                return fail(client, "cleared fixture did not reach its exact rendered state");
            }
            return true;
        }
        if (!atlasIsQuiescent(atlas, "post-clear")) return true;
        String geometryFailure = finalAtlasGeometryFailure(client, geometry, atlas);
        if (geometryFailure != null) return fail(client, geometryFailure);
        if (!atlasRevisionArmed) {
            atlasRevision = atlas.revision();
            atlasRevisionArmed = true;
        }
        if (++settleTicks < SHORT_SETTLE_TICKS) return true;
        RingWorldMod.LOGGER.info(
                "[handoff-foliage-capture] fixture cleared blocks={}, atlasRevision={}",
                clearedFixtureBlocks, atlas.revision());
        return captureAtCurrentPose(client, atlas, "empty-a",
                "ringworld-handoff-foliage-empty-a.png", Stage.CAPTURE_EMPTY_CONTROL);
    }

    private boolean captureEmptyControl(Minecraft client, RingGeometry geometry,
                                        RingTerrainAtlas atlas) {
        setCameraOrientation(client);
        if (!atPose(client, CENTER_Z) || !hasFullRadius(client)
                || !client.levelRenderer.hasRenderedAllSections()
                || !RingSurfaceTextureRenderer.legacyStreamingWindowComplete()
                || !fixtureIsCleared(client)) {
            if (++renderWaitTicks > RENDER_TIMEOUT_TICKS) {
                return fail(client, "empty control did not retain its exact rendered state");
            }
            controlFrameBaseline = -1L;
            return true;
        }
        String geometryFailure = finalAtlasGeometryFailure(client, geometry, atlas);
        if (geometryFailure != null) return fail(client, geometryFailure);
        if (controlFrameBaseline < 0L) {
            controlFrameBaseline = renderedFrames;
            return true;
        }
        if (renderedFrames - controlFrameBaseline < 2L) return true;
        return captureAtCurrentPose(client, atlas, "empty-b",
                "ringworld-handoff-foliage-empty-b.png", Stage.COMPLETE);
    }

    private boolean complete(Minecraft client) {
        String failure = screenshotFailure.get();
        if (failure != null) return fail(client, failure);
        if (successfulScreenshotWrites.get() != EXPECTED_SCREENSHOTS) return true;
        if (++completionTicks < 20) return true;
        stage = Stage.DONE;
        RingWorldMod.LOGGER.info(
                "[handoff-foliage-capture] result=true, captures complete, screenshots={}",
                successfulScreenshotWrites.get());
        client.stop();
        return true;
    }

    private void startFrameMetrics() {
        frameMetricsActive = true;
        lastFrameNanos = 0L;
        totalFrameNanos = 0L;
        maximumFrameNanos = 0L;
        frameSamples = 0;
        slowFrames = 0;
        streamingCoverageDropFrames = 0;
        streamingCoverageDropRun = 0;
        maximumStreamingCoverageDropRun = 0;
        unsafeStreamingCoverageFrames = 0;
    }

    private void finishFrameMetrics() {
        frameMetricsActive = false;
        double averageMillis = frameSamples == 0
                ? 0.0 : totalFrameNanos / 1_000_000.0 / frameSamples;
        RingWorldMod.LOGGER.info(
                "[handoff-foliage-capture] motion metrics steps={}, maxStep={}, "
                        + "frameSamples={}, averageMs={}, maxMs={}, over50Ms={}, "
                        + "streamingCoverageDropFrames={}, "
                        + "maximumStreamingCoverageDropRun={}, "
                        + "unsafeStreamingCoverageFrames={}",
                naturalSteps, maximumNaturalStep, frameSamples, averageMillis,
                maximumFrameNanos / 1_000_000.0, slowFrames,
                streamingCoverageDropFrames, maximumStreamingCoverageDropRun,
                unsafeStreamingCoverageFrames);
    }

    private boolean hasFullRadius(Minecraft client) {
        int effective = client.options.getEffectiveRenderDistance();
        if (effective != VIEW_DISTANCE_CHUNKS) return false;
        int cameraChunkX = (int)Math.floor(client.player.getX()) >> 4;
        int cameraChunkZ = (int)Math.floor(client.player.getZ()) >> 4;
        return contiguousLoadedChunks(client, cameraChunkX, cameraChunkZ, 1, effective)
                == effective
                && contiguousLoadedChunks(client, cameraChunkX, cameraChunkZ, -1, effective)
                == effective;
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

    private boolean fixtureMatches(Minecraft client) {
        if (createdFixtureBlocks != EXPECTED_FIXTURE_BLOCKS || planeCenterYs == null) {
            return false;
        }
        int foliageBlocks = 0;
        int sentinels = 0;
        for (int plane = 0; plane < PLANE_DISTANCES.length; plane++) {
            int x = planeBlockX(cameraX, PLANE_DISTANCES[plane]);
            int minY = planeCenterYs[plane] - PLANE_BELOW_CENTER;
            for (int y = minY; y <= planeCenterYs[plane] + PLANE_ABOVE_CENTER; y++) {
                int localY = y - minY;
                for (int z = MIN_PLANE_Z; z <= MAX_PLANE_Z; z++) {
                    BlockState expected = fixtureState(plane, localY, z - MIN_PLANE_Z);
                    BlockState actual = client.level.getBlockState(new BlockPos(x, y, z));
                    if (expected.isAir()) {
                        if (!actual.isAir()) return false;
                    } else {
                        if (!actual.is(expected.getBlock())
                                || !actual.getValue(LeavesBlock.PERSISTENT)) return false;
                        foliageBlocks++;
                    }
                }
            }
            int sentinelOffsetY = plane & 1;
            if (isFixtureLeaf(client, x, minY + sentinelOffsetY, MIN_PLANE_Z)) sentinels++;
            if (isFixtureLeaf(client, x, minY + sentinelOffsetY, MAX_PLANE_Z)) sentinels++;
        }
        return foliageBlocks == EXPECTED_FIXTURE_BLOCKS
                && sentinels == EXPECTED_SENTINELS;
    }

    private static boolean isFixtureLeaf(Minecraft client, int x, int y, int z) {
        BlockState state = client.level.getBlockState(new BlockPos(x, y, z));
        return (state.is(Blocks.AZALEA_LEAVES)
                || state.is(Blocks.FLOWERING_AZALEA_LEAVES))
                && state.getValue(LeavesBlock.PERSISTENT);
    }

    private static int createFixture(ServerLevel world, RingGeometry geometry,
                                     int[] centers) {
        int foliageBlocks = 0;
        double fixtureCameraX = geometry.circumferenceBlocks() / 4.0 + 0.5;
        for (int plane = 0; plane < PLANE_DISTANCES.length; plane++) {
            int x = planeBlockX(fixtureCameraX, PLANE_DISTANCES[plane]);
            if (Math.floorMod(x, 8) != 0) {
                throw new IllegalStateException(
                        "blend-only foliage plane must stay on an Atlas-cell boundary at x="
                                + x);
            }
            int minY = centers[plane] - PLANE_BELOW_CENTER;
            for (int y = minY; y <= centers[plane] + PLANE_ABOVE_CENTER; y++) {
                int localY = y - minY;
                for (int z = MIN_PLANE_Z; z <= MAX_PLANE_Z; z++) {
                    BlockState state = fixtureState(plane, localY, z - MIN_PLANE_Z);
                    world.setBlock(new BlockPos(x, y, z), state, 3);
                    if (!state.isAir()) foliageBlocks++;
                }
            }
        }
        return foliageBlocks;
    }

    private static int clearFixture(ServerLevel world, RingGeometry geometry,
                                    int[] centers) {
        int cleared = 0;
        double fixtureCameraX = geometry.circumferenceBlocks() / 4.0 + 0.5;
        for (int plane = 0; plane < PLANE_DISTANCES.length; plane++) {
            int x = planeBlockX(fixtureCameraX, PLANE_DISTANCES[plane]);
            int minY = centers[plane] - PLANE_BELOW_CENTER;
            for (int y = minY; y <= centers[plane] + PLANE_ABOVE_CENTER; y++) {
                int localY = y - minY;
                for (int z = MIN_PLANE_Z; z <= MAX_PLANE_Z; z++) {
                    if (fixtureState(plane, localY, z - MIN_PLANE_Z).isAir()) continue;
                    world.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                    cleared++;
                }
            }
        }
        return cleared;
    }

    private boolean fixtureIsCleared(Minecraft client) {
        if (clearedFixtureBlocks != EXPECTED_FIXTURE_BLOCKS || planeCenterYs == null) {
            return false;
        }
        for (int plane = 0; plane < PLANE_DISTANCES.length; plane++) {
            int x = planeBlockX(cameraX, PLANE_DISTANCES[plane]);
            int minY = planeCenterYs[plane] - PLANE_BELOW_CENTER;
            for (int y = minY; y <= planeCenterYs[plane] + PLANE_ABOVE_CENTER; y++) {
                int localY = y - minY;
                for (int z = MIN_PLANE_Z; z <= MAX_PLANE_Z; z++) {
                    if (fixtureState(plane, localY, z - MIN_PLANE_Z).isAir()) continue;
                    if (!client.level.getBlockState(new BlockPos(x, y, z)).isAir()) return false;
                }
            }
        }
        return true;
    }

    private static BlockState fixtureState(int plane, int localY, int localZ) {
        int checker = plane + localY + localZ;
        if ((checker & 1) != 0) return Blocks.AIR.defaultBlockState();
        BlockState leaves = ((checker >>> 1) & 1) == 0
                ? Blocks.AZALEA_LEAVES.defaultBlockState()
                : Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState();
        return leaves.setValue(LeavesBlock.PERSISTENT, true);
    }

    private static int planeBlockX(double fixtureCameraX, int distance) {
        return (int)Math.floor(fixtureCameraX - distance);
    }

    private static int nearestRayBlockY(RingGeometry geometry, float pitch,
                                        int distance, int minY, int maxYExclusive) {
        int bestY = minY;
        double bestDelta = Double.POSITIVE_INFINITY;
        for (int y = minY; y < maxYExclusive; y++) {
            double candidatePitch = geometry.pitchDegreesToIntrinsic(
                    CAMERA_Y, y + 0.5, distance, 0.0);
            double delta = Math.abs(candidatePitch - pitch);
            if (delta < bestDelta) {
                bestDelta = delta;
                bestY = y;
            }
        }
        return bestY;
    }

    private boolean atFixedXY(Minecraft client) {
        return Math.abs(client.player.getX() - cameraX) <= 0.001
                && Math.abs(client.player.getY() - CAMERA_Y) <= 0.001;
    }

    private boolean atPose(Minecraft client, double z) {
        return atFixedXY(client) && Math.abs(client.player.getZ() - z) <= 0.001;
    }

    private void setCameraOrientation(Minecraft client) {
        client.player.setYRot(CAMERA_YAW);
        client.player.setXRot(capturePitch);
    }

    private String worldName() {
        return System.getProperty(WORLD_PROPERTY, "").trim();
    }

    private boolean fail(Minecraft client, String detail) {
        if (stage == Stage.DONE) return true;
        frameMetricsActive = false;
        stage = Stage.DONE;
        RingWorldMod.LOGGER.error(
                "[handoff-foliage-capture] result=false, {}", detail);
        client.stop();
        return true;
    }

    private enum Stage {
        WAITING_FOR_WORLD("waiting-for-world"),
        WAITING_FOR_FIXTURE("waiting-for-fixture"),
        REQUEST_SECTION_REBUILD("request-section-rebuild"),
        WAIT_SECTION_REBUILD_FALLBACK("wait-section-rebuild-fallback"),
        WAIT_SECTION_REBUILD_RECOVERY("wait-section-rebuild-recovery"),
        MOVE_TO_NEGATIVE("move-to-negative"),
        SETTLE_NEGATIVE("settle-negative"),
        MOVE_TO_CENTER_A("move-to-center-a"),
        SETTLE_CENTER_A("settle-center-a"),
        CAPTURE_CENTER_A_CONTROL("capture-center-a-control"),
        REQUEST_POSITIVE_TELEPORT("request-positive-teleport"),
        WAIT_POSITIVE_TELEPORT("wait-positive-teleport"),
        MOVE_TO_POSITIVE("move-to-positive"),
        SETTLE_POSITIVE("settle-positive"),
        MOVE_TO_CENTER_B("move-to-center-b"),
        SETTLE_CENTER_B("settle-center-b"),
        CAPTURE_CENTER_B_CONTROL("capture-center-b-control"),
        REQUEST_BLINDNESS("request-blindness"),
        WAIT_BLINDNESS("wait-blindness"),
        REQUEST_BLINDNESS_CLEAR("request-blindness-clear"),
        WAIT_BLINDNESS_CLEAR("wait-blindness-clear"),
        REQUEST_FIXTURE_CLEAR("request-fixture-clear"),
        WAIT_FIXTURE_CLEAR("wait-fixture-clear"),
        CAPTURE_EMPTY_CONTROL("capture-empty-control"),
        COMPLETE("complete"),
        DONE("done");

        private final String id;

        Stage(String id) {
            this.id = id;
        }
    }
}
