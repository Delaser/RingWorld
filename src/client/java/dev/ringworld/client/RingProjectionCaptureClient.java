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
    private int stage;
    private int settleTicks;
    private int atlasWaitTicks;
    private boolean waitingLogged;

    boolean tick(Minecraft client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return false;
        if (stage >= 2) return true;
        if (client.player == null || client.level == null) return true;
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
}
