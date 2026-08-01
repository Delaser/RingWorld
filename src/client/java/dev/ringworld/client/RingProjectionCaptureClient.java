package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRenderProfile;
import dev.ringworld.world.RingTerrainAtlas;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;

/**
 * Opt-in, non-destructive visual probe for the projection directions and
 * live/proxy handoff that expose complete-ring regressions on large layouts.
 */
final class RingProjectionCaptureClient {
    private static final String ENABLE_PROPERTY = "ringworld.captureRingProjection";
    private static final String WORLD_PROPERTY = "ringworld.projectionWorld";
    private static final String VIEW_DISTANCE_PROPERTY =
            "ringworld.projectionViewDistanceChunks";
    private static final String ENVIRONMENT_PROPERTY =
            "ringworld.projectionEnvironment";
    private static final int DEFAULT_VIEW_DISTANCE_CHUNKS = 16;
    private static final int MIN_VIEW_DISTANCE_CHUNKS = 2;
    private static final int MAX_VIEW_DISTANCE_CHUNKS = 32;
    private static final int WORLD_OPEN_TIMEOUT_TICKS = 2_400;
    private int stage;
    private int settleTicks;
    private int atlasWaitTicks;
    private boolean waitingLogged;
    private int worldOpenTicks;
    private boolean worldOpenRequested;
    private boolean worldReadyLogged;
    private int completionTicks;
    private boolean focusPolicyApplied;
    private boolean captureEnvironmentApplied;
    private CaptureEnvironment selectedEnvironment;
    private boolean captureStageArmed;
    private float capturePitch;
    private long lastFrameNanos;
    private long totalFrameNanos;
    private long maxFrameNanos;
    private int frameSamples;
    private int slowFrames;

    boolean tick(Minecraft client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return false;
        applyFocusPolicy(client);
        if (stage >= 4) return true;
        if (!ensureWorldOpen(client)) return true;
        if (stage == 3) {
            if (++completionTicks >= 20) finish(client, true, "captures complete");
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

        applyCaptureEnvironment(client);
        if (!captureStageArmed) {
            armCaptureStage(client, geometry, atlas);
            return true;
        }
        client.player.setYRot(90.0F);
        client.player.setXRot(capturePitch);
        if (++settleTicks < 100) return true;
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

    void frameRendered() {
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
        if (focusPolicyApplied) return;
        client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
        client.options.pauseOnLostFocus = false;
        focusPolicyApplied = true;
        RingWorldMod.LOGGER.info("[projection-capture] applied unattended focus policy");
    }

    private void applyCaptureEnvironment(Minecraft client) {
        if (captureEnvironmentApplied) return;
        int viewDistance = projectionViewDistanceChunks();
        CaptureEnvironment environment = selectedEnvironment();
        client.options.renderDistance().set(viewDistance);
        if (client.getConnection() != null) {
            client.getConnection().sendCommand("time set " + environment.timeTicks);
            client.getConnection().sendCommand("gamerule advance_time false");
            client.getConnection().sendCommand(
                    environment.raining ? "weather rain" : "weather clear");
        }
        captureEnvironmentApplied = true;
        RingWorldMod.LOGGER.info(
                "[projection-capture] normalized environment={} and applied {}-chunk view distance",
                environment.id, viewDistance);
    }

    private void armCaptureStage(Minecraft client, RingGeometry geometry,
                                 RingTerrainAtlas atlas) {
        if (stage == 0) {
            capturePitch = 0.0F;
            RingWorldMod.LOGGER.info("[projection-capture] tangent capture armed");
        } else if (stage == 1) {
            RingRenderProfile profile = RingRenderProfile.create(
                    geometry, projectionViewDistanceChunks() * 16.0);
            double targetDistance = profile.effectiveViewDistanceBlocks();
            double targetHeight = atlas.sample(
                    client.player.getX() + targetDistance,
                    client.player.getZ()).height();
            capturePitch = (float)geometry.pitchDegreesToIntrinsic(
                    client.player.getY(), targetHeight, targetDistance, 0.0);
            RingWorldMod.LOGGER.info(
                    "[projection-capture] {}-chunk handoff capture armed at pitch={}, distance={}, surfaceY={}",
                    projectionViewDistanceChunks(), capturePitch,
                    targetDistance, targetHeight);
        } else {
            capturePitch = -90.0F;
            RingWorldMod.LOGGER.info("[projection-capture] radial-up capture armed");
        }
        resetFrameMetrics();
        captureStageArmed = true;
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

    private enum CaptureEnvironment {
        NOON("noon", 6_000, false),
        DUSK("dusk", 14_008, false),
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
