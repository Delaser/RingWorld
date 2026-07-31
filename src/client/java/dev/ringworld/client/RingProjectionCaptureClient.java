package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;

/**
 * Opt-in, non-destructive visual probe for the two projection directions that
 * expose complete-ring clipping differently on large layouts.
 */
final class RingProjectionCaptureClient {
    private static final String ENABLE_PROPERTY = "ringworld.captureRingProjection";
    private static final String WORLD_PROPERTY = "ringworld.projectionWorld";
    private static final int WORLD_OPEN_TIMEOUT_TICKS = 2_400;
    private int stage;
    private int settleTicks;
    private int atlasWaitTicks;
    private boolean waitingLogged;
    private int worldOpenTicks;
    private boolean worldOpenRequested;
    private boolean worldReadyLogged;
    private int completionTicks;

    boolean tick(Minecraft client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return false;
        if (stage >= 3) return true;
        if (!ensureWorldOpen(client)) return true;
        if (stage == 2) {
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

        client.player.setYRot(90.0F);
        client.player.setXRot(stage == 0 ? 0.0F : -90.0F);
        if (++settleTicks < 100) return true;
        settleTicks = 0;

        if (stage == 0) {
            Screenshot.grab(
                    client.gameDirectory, "ringworld-projection-tangent.png",
                    client.getMainRenderTarget(), 1,
                    message -> RingWorldMod.LOGGER.info(
                            "[projection-capture] tangent screenshot: {}",
                            message.getString()));
            RingWorldMod.LOGGER.info(
                    "[projection-capture] tangent/along-ring view captured at C={}, R={}",
                    geometry.circumferenceBlocks(), geometry.radius());
            stage = 1;
            return true;
        }

        Screenshot.grab(
                client.gameDirectory, "ringworld-projection-up.png",
                client.getMainRenderTarget(), 1,
                message -> RingWorldMod.LOGGER.info(
                        "[projection-capture] radial-up screenshot: {}",
                        message.getString()));
        RingWorldMod.LOGGER.info(
                "[projection-capture] radial/up view captured at C={}, diameter={}; complete",
                geometry.circumferenceBlocks(), geometry.radius() * 2.0);
        stage = 2;
        return true;
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

    private String projectionWorld() {
        return System.getProperty(WORLD_PROPERTY, "").trim();
    }

    private void finish(Minecraft client, boolean passed, String detail) {
        if (stage >= 3) return;
        stage = 3;
        RingWorldMod.LOGGER.info("[projection-capture] result={}, {}", passed, detail);
        client.stop();
    }
}
