package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FogType;

/** Opt-in, muted, disposable submerged-camera regression, shared by both loaders. */
public final class RingUnderwaterCaptureClient {
    private static final RingUnderwaterCaptureClient INSTANCE = new RingUnderwaterCaptureClient();
    private static final double[] HEIGHTS = {127.0, 121.0, 124.25, 127.0, 121.0, 127.0};
    private static final String[] NAMES = {"above", "submerged", "surface", "resurfaced", "reload", "reload-resurfaced"};
    private int ticks, stage, settle;
    private boolean opening, creating, setupRequested, positionRequested, pending;
    private volatile boolean setupReady;
    private volatile String failure;
    private java.util.concurrent.CompletableFuture<Void> reload;

    public static boolean tickIfEnabled(Minecraft client) {
        return Boolean.getBoolean("ringworld.captureUnderwater") && INSTANCE.tick(client);
    }

    private boolean tick(Minecraft client) {
        client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
        client.options.pauseOnLostFocus = false;
        client.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
        if (++ticks > 6_000) return fail(client, "timeout stage=" + stage
                + " atlasReady=" + RingSurfaceTextureRenderer.captureReady()
                + " screen=" + RingMinecraftClientAccess.screen(client)
                + " reload=" + (reload == null ? "not requested" : reload.isDone()));
        if (failure != null) return fail(client, failure);
        if (client.level == null || client.player == null) {
            if (!client.isGameLoadFinished() || creating) return true;
            if (!opening && RingMinecraftClientAccess.screen(client) instanceof TitleScreen) {
                opening = true;
                CreateWorldScreen.openFresh(client, () -> opening = false);
            } else if (RingMinecraftClientAccess.screen(client) instanceof CreateWorldScreen screen) {
                var creator = screen.getUiState();
                creator.setName("RingWorld Underwater Regression");
                creator.setSeed("259");
                creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                creator.setAllowCommands(true);
                creating = true;
                ((CreateWorldScreenInvoker) screen).ringworld$createLevel();
            }
            return true;
        }
        client.options.renderDistance().set(Math.clamp(
                Integer.getInteger("ringworld.underwaterViewDistance", 8), 2, 32));
        client.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
        client.options.vignette().set(false);
        RingMinecraftClientAccess.setGuiHidden(client, true);
        if (pending || RingMinecraftClientAccess.screen(client) != null) return true;
        if (ClientRingState.geometry() == null) return true;
        if (!setupReady) {
            if (!setupRequested) {
                setupRequested = true;
                RingIntegratedCaptureControl.execute(client, "underwater scene", context -> {
                    RingIntegratedCaptureControl.normalizeEnvironment(context, 6000, false);
                    // A sealed pool above terrain leaves an unobstructed view of the far ring.
                    for (BlockPos pos : BlockPos.betweenClosed(480, 117, -24, 544, 126, 24)) {
                        boolean shell = pos.getY() == 117 || pos.getX() == 480 || pos.getX() == 544
                                || pos.getZ() == -24 || pos.getZ() == 24;
                        context.world().setBlock(pos, (shell ? Blocks.GLASS : Blocks.WATER).defaultBlockState(), 2);
                    }
                    // Nearby real blocks must remain visible through the environmental fog.
                    for (BlockPos pos : BlockPos.betweenClosed(504, 118, -2, 505, 124, 2)) {
                        context.world().setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
                    }
                }, () -> setupReady = true, detail -> failure = detail);
            }
            return true;
        }
        if (stage == NAMES.length) {
            RingWorldMod.LOGGER.info("[underwater-capture] PASS: camera fluid assertions, above/submerged/surface/resurfaced/reload/reload-resurfaced; pixel review required");
            client.stop();
            return true;
        }
        if (!positionRequested) {
            positionRequested = true;
            double height = HEIGHTS[stage];
            RingIntegratedCaptureControl.execute(client, "underwater pose", context ->
                    RingIntegratedCaptureControl.teleport(context, 512.5, height, 0.5),
                    () -> { }, detail -> failure = detail);
            return true;
        }
        if (Math.abs(client.player.getY() - HEIGHTS[stage]) > 0.05) return true;
        client.player.setYRot(90.0F);
        client.player.setXRot(-15.0F);
        if (stage == 4) {
            if (reload == null) reload = client.reloadResourcePacks();
            if (!reload.isDone()) return true;
            if (reload.isCompletedExceptionally()) return fail(client, "reload failed");
        }
        // Sodium can legitimately omit the whole sky pass underwater. After
        // reload its Atlas buffers stay absent until resurfacing; require their
        // recovery in stage 5 instead of waiting forever in submerged stage 4.
        if (stage != 4 && !RingSurfaceTextureRenderer.captureReady()) { settle = 0; return true; }
        FogType expected = stage == 0 || stage == 3 || stage == 5 ? FogType.NONE : FogType.WATER;
        if (RingMinecraftClientAccess.camera(client).getFluidInCamera() != expected
                || !client.level.getChunkSource().hasChunk(32, 0)
                || !client.levelRenderer.hasRenderedAllSections()) { settle = 0; return true; }
        if (++settle < 120) return true;
        RingWorldMod.LOGGER.info("[underwater-capture] {} fluid={} camera={} atlasReady={}",
                NAMES[stage], expected, RingMinecraftClientAccess.camera(client).position(),
                RingSurfaceTextureRenderer.captureReady());
        pending = true;
        String name = "underwater-" + NAMES[stage] + ".png";
        RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, name,
                RingMinecraftClientAccess.mainRenderTarget(client), 1, message -> client.execute(() -> {
                    RingWorldMod.LOGGER.info("[underwater-capture] screenshot {}", message.getString());
                    pending = false; stage++; settle = 0; positionRequested = false;
                }));
        return true;
    }

    private boolean fail(Minecraft client, String reason) {
        RingWorldMod.LOGGER.error("[underwater-capture] FAIL: {}", reason);
        client.stop();
        return true;
    }
}
