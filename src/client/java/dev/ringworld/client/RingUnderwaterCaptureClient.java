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
    private static final boolean DEEP_HANDOFF = Boolean.getBoolean("ringworld.captureDeepWaterHandoff");
    private static final double[] HEIGHTS = {127.0, 121.0, 124.25, 127.0, 121.0, 127.0};
    private static final double[] DEEP_HEIGHTS = {96.0, 58.0, 62.25, 96.0, 58.0, 96.0};
    private static final String[] NAMES = {"above", "submerged", "surface", "resurfaced", "reload", "reload-resurfaced"};
    private int ticks, stage, settle;
    private boolean opening, creating, setupRequested, positionRequested, pending;
    private volatile boolean setupReady;
    private volatile String failure;
    private java.util.concurrent.CompletableFuture<Void> reload;
    private final CopiedWorldFileFixUpgrade copiedWorldUpgrade = new CopiedWorldFileFixUpgrade();

    public static boolean tickIfEnabled(Minecraft client) {
        return (Boolean.getBoolean("ringworld.captureUnderwater") || DEEP_HANDOFF) && INSTANCE.tick(client);
    }

    private boolean tick(Minecraft client) {
        client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
        client.options.pauseOnLostFocus = false;
        client.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
        // Set the requested distance before login so native client information
        // and the server's per-player chunk subscription match the capture.
        int viewDistance = Math.clamp(Integer.getInteger("ringworld.underwaterViewDistance", 8), 2, 32);
        if (client.options.renderDistance().get() != viewDistance) {
            client.options.renderDistance().set(viewDistance);
            client.options.broadcastOptions();
        }
        if (++ticks > (DEEP_HANDOFF ? 20_000 : 6_000)) return fail(client, "timeout stage=" + stage
                + " atlasReady=" + RingSurfaceTextureRenderer.captureReady()
                + " screen=" + RingMinecraftClientAccess.screen(client)
                + " reload=" + (reload == null ? "not requested" : reload.isDone()));
        if (failure != null) return fail(client, failure);
        if (client.level == null || client.player == null) {
            if (DEEP_HANDOFF && creating) copiedWorldUpgrade.handleIfRequired(
                    client, "underwater-capture", "Deep Water Handoff Review");
            if (!client.isGameLoadFinished() || creating) return true;
            if (!opening && RingMinecraftClientAccess.screen(client) instanceof TitleScreen) {
                opening = true;
                if (DEEP_HANDOFF && java.nio.file.Files.isRegularFile(client.gameDirectory.toPath()
                        .resolve("saves/Deep Water Handoff Review/level.dat"))) {
                    creating = true;
                    client.createWorldOpenFlows().openWorld("Deep Water Handoff Review",
                            () -> failure = "copied deep-water world opening cancelled");
                    return true;
                }
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
                    if (DEEP_HANDOFF && Boolean.getBoolean("ringworld.reuseDeepWaterScene")) {
                        // Repeated ABI probes may copy the already captured scene.
                        // Fail rather than silently using an unprepared world.
                        if (!context.world().getBlockState(new BlockPos(512, 0, 112)).is(Blocks.STONE)
                                || !context.world().getBlockState(new BlockPos(512, 63, 112)).is(Blocks.WATER)
                                || !context.world().getBlockState(new BlockPos(512, 96, 112)).isAir()) {
                            throw new IllegalStateException("copied deep-water scene is not prepared");
                        }
                        return;
                    }
                    // The default elevated pool checks submersion; the deep
                    // variant also exposes premature seabed coverage loss.
                    int bottom = DEEP_HANDOFF ? 0 : 117;
                    int top = DEEP_HANDOFF ? 63 : 126;
                    int endX = DEEP_HANDOFF ? 800 : 544;
                    int halfWidth = DEEP_HANDOFF ? 120 : 24;
                    for (BlockPos pos : BlockPos.betweenClosed(480, bottom, -halfWidth, endX, top, halfWidth)) {
                        boolean shell = pos.getY() == bottom || pos.getX() == 480 || pos.getX() == endX
                                || pos.getZ() == -halfWidth || pos.getZ() == halfWidth;
                        context.world().setBlock(pos, (shell ? (DEEP_HANDOFF ? Blocks.STONE : Blocks.GLASS)
                                : Blocks.WATER).defaultBlockState(), 2);
                    }
                    if (DEEP_HANDOFF) {
                        for (BlockPos pos : BlockPos.betweenClosed(480, 64, -120, 800, 128, 120)) {
                            context.world().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                    // Nearby real blocks must remain visible through the environmental fog.
                    for (BlockPos pos : BlockPos.betweenClosed(504, bottom + 1, -2, 505, top - 2, 2)) {
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
            double height = heights()[stage];
            RingIntegratedCaptureControl.execute(client, "underwater pose", context ->
                    RingIntegratedCaptureControl.teleport(context, 512.5, height, DEEP_HANDOFF ? 112.5 : 0.5),
                    () -> { }, detail -> failure = detail);
            return true;
        }
        if (Math.abs(client.player.getY() - heights()[stage]) > 0.05) return true;
        client.player.setYRot(DEEP_HANDOFF ? -90.0F : 90.0F);
        client.player.setXRot(DEEP_HANDOFF ? 25.0F : -15.0F);
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
                || (DEEP_HANDOFF && !deepSceneChunksReady(client, viewDistance))
                || !client.levelRenderer.hasRenderedAllSections()) { settle = 0; return true; }
        if (++settle < 120) return true;
        RingWorldMod.LOGGER.info("[underwater-capture] {} fluid={} camera={} atlasReady={} view={}/{}",
                NAMES[stage], expected, RingMinecraftClientAccess.camera(client).position(),
                RingSurfaceTextureRenderer.captureReady(), viewDistance, client.options.getEffectiveRenderDistance());
        pending = true;
        String name = "underwater-" + NAMES[stage] + ".png";
        RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, name,
                RingMinecraftClientAccess.mainRenderTarget(client), 1, message -> client.execute(() -> {
                    RingWorldMod.LOGGER.info("[underwater-capture] screenshot {}", message.getString());
                    pending = false; stage++; settle = 0; positionRequested = false;
                }));
        return true;
    }

    private static double[] heights() { return DEEP_HANDOFF ? DEEP_HEIGHTS : HEIGHTS; }

    private static boolean deepSceneChunksReady(Minecraft client, int distance) {
        // An idle compiler does not prove that the floor chunks have arrived.
        // Check the pool along the east-facing view, inside the native range.
        for (int x = 32; x <= Math.min(50, 32 + (int)(distance * 0.90)); x++) {
            for (int z = 6; z <= 7; z++) {
                if (!client.level.getChunkSource().hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    private boolean fail(Minecraft client, String reason) {
        RingWorldMod.LOGGER.error("[underwater-capture] FAIL: {}", reason);
        client.stop();
        return true;
    }
}
