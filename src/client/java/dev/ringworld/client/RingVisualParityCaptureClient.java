package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingTerrainAtlas;
import net.minecraft.client.Minecraft;
import dev.ringworld.client.compat.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;

/**
 * Disposable, opt-in rendered-frame gate for the three views that expose the
 * periodic seam and the finite width rims. The harness deliberately opens a
 * named copied save in-process and asks the integrated server to move the
 * player for each pose; it never writes to the source save or locally spoofs
 * an authoritative player position.
 */
public final class RingVisualParityCaptureClient {
    private static final String ENABLE_PROPERTY = "ringworld.captureRingVisualParity";
    private static final String WORLD_PROPERTY = "ringworld.visualParityWorld";
    private static final String VIEW_DISTANCE_PROPERTY =
            "ringworld.visualParityViewDistanceChunks";
    private static final int DEFAULT_VIEW_DISTANCE_CHUNKS = 16;
    private static final int MIN_VIEW_DISTANCE_CHUNKS = 2;
    private static final int MAX_VIEW_DISTANCE_CHUNKS = 32;
    private static final int WORLD_OPEN_TIMEOUT_TICKS = 2_400;
    private static final int ATLAS_READY_TIMEOUT_TICKS = 2_400;
    private static final int POSITION_TIMEOUT_TICKS = 600;
    private static final int RENDER_TIMEOUT_TICKS = 1_200;
    private static final int SETTLE_TICKS = 120;
    private static final double SEAM_CAMERA_Y = 120.0;

    private int stage;
    private int worldOpenTicks;
    private int atlasReadyTicks;
    private int positionTicks;
    private int renderTicks;
    private int settleTicks;
    private int completionTicks;
    private boolean worldOpenRequested;
    private boolean focusPolicyApplied;
    private boolean environmentRequested;
    private volatile boolean environmentReady;
    private volatile String serverControlFailure;
    private boolean positionRequested;
    private boolean seamArmed;
    private boolean seamCrossed;
    private double seamBoundary;
    private double previousSeamX;
    private double maximumSeamStep;
    private float seamYaw;
    private float seamPitch;
    private boolean seamMotionMetricsActive;
    private long seamMotionLastFrameNanos;
    private long seamMotionTotalFrameNanos;
    private long seamMotionMaxFrameNanos;
    private int seamMotionFrameSamples;
    private int seamMotionSlowFrames;

    public boolean tick(Minecraft client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return false;
        applyFocusPolicy(client);
        if (stage >= CaptureView.values().length) {
            if (++completionTicks >= 20) finish(client, true, "captures complete");
            return true;
        }
        if (!ensureWorldOpen(client)) return true;
        if (client.screen instanceof PauseScreen) client.setScreen(null);
        if (client.screen != null) return true;

        RingGeometry geometry = ClientRingState.geometry();
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        if (geometry == null || atlas == null || !atlas.isComplete()) {
            if (++atlasReadyTicks > ATLAS_READY_TIMEOUT_TICKS) {
                finish(client, false, "timed out waiting for complete terrain atlas");
            }
            return true;
        }
        if (client.player == null || client.getConnection() == null) return true;

        String controlFailure = serverControlFailure;
        if (controlFailure != null) {
            finish(client, false, controlFailure);
            return true;
        }
        if (!ensureEnvironment(client)) return true;
        CaptureView view = CaptureView.values()[stage];
        if (!positionRequested) {
            requestServerPosition(client, geometry, view);
            return true;
        }
        if (!atServerPosition(client, geometry, view)
                && !(view == CaptureView.SEAM && seamArmed)) {
            if (++positionTicks > POSITION_TIMEOUT_TICKS) {
                finish(client, false, "timed out reaching " + view.id + " position");
            }
            return true;
        }

        if (view == CaptureView.SEAM) {
            if (!runNaturalSeamCrossing(client, geometry, view)) return true;
        } else {
            client.player.setYRot(view.yaw);
            client.player.setXRot(view.pitch);
        }
        if (!client.levelRenderer.hasRenderedAllSections()) {
            if (++renderTicks > RENDER_TIMEOUT_TICKS) {
                finish(client, false, "timed out rendering " + view.id + " position");
            }
            return true;
        }
        if (++settleTicks < SETTLE_TICKS) return true;

        Screenshot.grab(client.gameDirectory, view.screenshotName,
                client.getMainRenderTarget(), 1,
                message -> RingWorldMod.LOGGER.info(
                        "[visual-parity-capture] {} screenshot: {}",
                        view.id, message.getString()));
        if (view == CaptureView.SEAM) finishSeamMotionMetrics();
        RingWorldMod.LOGGER.info(
                "[visual-parity-capture] captured {} at x={}, y={}, z={}, yaw={}, pitch={}",
                view.id, client.player.getX(), client.player.getY(), client.player.getZ(),
                client.player.getYRot(), client.player.getXRot());
        stage++;
        positionRequested = false;
        positionTicks = 0;
        renderTicks = 0;
        settleTicks = 0;
        return true;
    }

    private boolean runNaturalSeamCrossing(
            Minecraft client, RingGeometry geometry, CaptureView view) {
        if (seamCrossed) return true;
        if (!seamArmed) {
            client.player.setYRot(view.yaw);
            client.player.setXRot(view.pitch);
            if (!client.levelRenderer.hasRenderedAllSections()) {
                if (++renderTicks > RENDER_TIMEOUT_TICKS) {
                    finish(client, false, "timed out rendering the seam approach");
                }
                return false;
            }
            if (++settleTicks < SETTLE_TICKS) return false;
            seamArmed = true;
            seamBoundary = geometry.nextPositiveSeamX(client.player.getX());
            previousSeamX = client.player.getX();
            seamYaw = client.player.getYRot();
            seamPitch = client.player.getXRot();
            renderTicks = 0;
            settleTicks = 0;
            startSeamMotionMetrics();
            RingWorldMod.LOGGER.info(
                    "[visual-parity-capture] armed natural seam crossing x={} boundary={} yaw={} pitch={}",
                    previousSeamX, seamBoundary, seamYaw, seamPitch);
        }

        double currentX = client.player.getX();
        maximumSeamStep = Math.max(maximumSeamStep, Math.abs(currentX - previousSeamX));
        if (maximumSeamStep > 1.25) {
            finish(client, false, "seam crossing position pop=" + maximumSeamStep);
            return false;
        }
        if (Math.abs(Mth.wrapDegrees(client.player.getYRot() - seamYaw)) > 0.01F
                || Math.abs(client.player.getXRot() - seamPitch) > 0.01F) {
            finish(client, false, "seam crossing changed camera orientation");
            return false;
        }
        if (currentX < seamBoundary + 2.0) {
            double nextX = Math.min(seamBoundary + 2.0, currentX + 0.25);
            previousSeamX = currentX;
            client.player.setPos(nextX, client.player.getY(), client.player.getZ());
            client.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                    nextX, client.player.getY(), client.player.getZ(),
                    client.player.getYRot(), client.player.getXRot(),
                    client.player.onGround()));
            return false;
        }

        seamCrossed = true;
        renderTicks = 0;
        settleTicks = 0;
        RingWorldMod.LOGGER.info(
                "[visual-parity-capture] natural seam crossing complete x={} boundary={} maxStep={}",
                currentX, seamBoundary, maximumSeamStep);
        return true;
    }

    /** Called from each loader's existing end-of-level render callback. */
    public void frameRendered() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || !seamMotionMetricsActive) return;
        long now = System.nanoTime();
        if (seamMotionLastFrameNanos != 0L) {
            long elapsed = now - seamMotionLastFrameNanos;
            seamMotionTotalFrameNanos += elapsed;
            seamMotionMaxFrameNanos = Math.max(seamMotionMaxFrameNanos, elapsed);
            seamMotionFrameSamples++;
            if (elapsed > 50_000_000L) seamMotionSlowFrames++;
        }
        seamMotionLastFrameNanos = now;
    }

    private void startSeamMotionMetrics() {
        seamMotionMetricsActive = true;
        seamMotionLastFrameNanos = 0L;
        seamMotionTotalFrameNanos = 0L;
        seamMotionMaxFrameNanos = 0L;
        seamMotionFrameSamples = 0;
        seamMotionSlowFrames = 0;
    }

    private void finishSeamMotionMetrics() {
        seamMotionMetricsActive = false;
        double averageMillis = seamMotionFrameSamples == 0 ? 0.0
                : seamMotionTotalFrameNanos / 1_000_000.0 / seamMotionFrameSamples;
        RingWorldMod.LOGGER.info(
                "[visual-parity-capture] seam motion frame metrics: samples={}, averageMs={}, maxMs={}, over50Ms={}",
                seamMotionFrameSamples, averageMillis,
                seamMotionMaxFrameNanos / 1_000_000.0, seamMotionSlowFrames);
    }

    private boolean ensureWorldOpen(Minecraft client) {
        if (client.player != null && client.level != null) return true;
        if (++worldOpenTicks > WORLD_OPEN_TIMEOUT_TICKS) {
            finish(client, false, "timed out opening save '" + worldName() + "'");
            return false;
        }
        if (!worldOpenRequested && client.isGameLoadFinished()
                && client.getSingleplayerServer() == null) {
            worldOpenRequested = true;
            RingWorldMod.LOGGER.info(
                    "[visual-parity-capture] opening copied save '{}' in-process", worldName());
            client.createWorldOpenFlows().openWorld(worldName(),
                    () -> finish(client, false, "save load cancelled for '" + worldName() + "'"));
        }
        return false;
    }

    private void applyFocusPolicy(Minecraft client) {
        if (focusPolicyApplied) return;
        client.options.pauseOnLostFocus = false;
        focusPolicyApplied = true;
        RingWorldMod.LOGGER.info("[visual-parity-capture] applied unattended focus policy");
    }

    private boolean ensureEnvironment(Minecraft client) {
        if (serverControlFailure != null) return false;
        if (environmentReady) return true;
        if (environmentRequested) return false;
        int viewDistance = viewDistanceChunks();
        client.options.renderDistance().set(viewDistance);
        environmentRequested = true;
        RingIntegratedCaptureControl.execute(client, "visual-parity environment setup",
                context -> RingIntegratedCaptureControl.normalizeEnvironment(
                        context, 6_000, false),
                () -> environmentReady = true,
                detail -> serverControlFailure = detail);
        RingWorldMod.LOGGER.info(
                "[visual-parity-capture] normalized noon/clear environment at {} chunks",
                viewDistance);
        return false;
    }

    private void requestServerPosition(Minecraft client, RingGeometry geometry, CaptureView view) {
        Pose pose = view.pose(geometry);
        double cameraY = cameraY(client, view);
        positionRequested = true;
        RingIntegratedCaptureControl.execute(client, "visual-parity " + view.id + " pose",
                context -> RingIntegratedCaptureControl.teleport(
                        context, pose.x, cameraY, pose.z),
                () -> { },
                detail -> serverControlFailure = detail);
        RingWorldMod.LOGGER.info(
                "[visual-parity-capture] requested server-authoritative {} pose x={}, y={}, z={}",
                view.id, pose.x, cameraY, pose.z);
    }

    private boolean atServerPosition(Minecraft client, RingGeometry geometry, CaptureView view) {
        Pose pose = view.pose(geometry);
        return Math.abs(geometry.shortestCircumferenceDelta(pose.x, client.player.getX())) < 1.5
                && Math.abs(client.player.getY() - cameraY(client, view)) < 1.5
                && Math.abs(client.player.getZ() - pose.z) < 1.5;
    }

    private double cameraY(Minecraft client, CaptureView view) {
        if (view == CaptureView.SEAM || view == CaptureView.SEAM_JOIN) return SEAM_CAMERA_Y;
        int wallTopExclusive = client.level.getMinBuildHeight() + ClientRingState.wallHeightBlocks();
        return wallTopExclusive - 8.0;
    }

    private int viewDistanceChunks() {
        String configured = System.getProperty(VIEW_DISTANCE_PROPERTY,
                Integer.toString(DEFAULT_VIEW_DISTANCE_CHUNKS)).trim();
        try {
            return Math.clamp(Integer.parseInt(configured),
                    MIN_VIEW_DISTANCE_CHUNKS, MAX_VIEW_DISTANCE_CHUNKS);
        } catch (NumberFormatException exception) {
            RingWorldMod.LOGGER.warn(
                    "[visual-parity-capture] invalid {}='{}'; using {} chunks",
                    VIEW_DISTANCE_PROPERTY, configured, DEFAULT_VIEW_DISTANCE_CHUNKS);
            return DEFAULT_VIEW_DISTANCE_CHUNKS;
        }
    }

    private String worldName() {
        return System.getProperty(WORLD_PROPERTY, "").trim();
    }

    private void finish(Minecraft client, boolean passed, String detail) {
        if (stage > CaptureView.values().length) return;
        seamMotionMetricsActive = false;
        stage = CaptureView.values().length + 1;
        RingWorldMod.LOGGER.info("[visual-parity-capture] result={}, {}", passed, detail);
        client.stop();
    }

    private record Pose(double x, double z) { }

    private enum CaptureView {
        SEAM("seam", "ringworld-visual-parity-seam.png", -90.0F, 8.0F) {
            @Override
            Pose pose(RingGeometry geometry) {
                return new Pose(geometry.circumferenceBlocks() - 4.0, 0.5);
            }
        },
        SEAM_JOIN("seam-join", "ringworld-visual-parity-seam-join.png", 90.0F, 8.0F) {
            @Override
            Pose pose(RingGeometry geometry) {
                // Look back from canonical zero into the C-1 side. The natural
                // crossing view faces away from this join and cannot reveal a
                // generated terrain wall left behind the player.
                return new Pose(2.0, 0.5);
            }
        },
        MIN_RIM("min-rim", "ringworld-visual-parity-min-rim.png", 180.0F, 0.0F) {
            @Override
            Pose pose(RingGeometry geometry) {
                return new Pose(geometry.circumferenceBlocks() / 4.0,
                        geometry.minWidthZ() + RingGenerationBoundary.RIM_THICKNESS + 16.5);
            }
        },
        MAX_RIM("max-rim", "ringworld-visual-parity-max-rim.png", 0.0F, 0.0F) {
            @Override
            Pose pose(RingGeometry geometry) {
                return new Pose(geometry.circumferenceBlocks() / 4.0,
                        geometry.maxWidthZ() - RingGenerationBoundary.RIM_THICKNESS - 16.5);
            }
        };

        private final String id;
        private final String screenshotName;
        private final float yaw;
        private final float pitch;

        CaptureView(String id, String screenshotName, float yaw, float pitch) {
            this.id = id;
            this.screenshotName = screenshotName;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        abstract Pose pose(RingGeometry geometry);
    }
}
