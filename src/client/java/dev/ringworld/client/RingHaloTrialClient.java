package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.net.RingTerrainPreviewPayload;
import dev.ringworld.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.GameType;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/** Disposable, opt-in Halo demo: seed preview only, no full Atlas or pregeneration. */
public final class RingHaloTrialClient {
    private static final String SEED = System.getProperty("ringworld.haloTrialSeed", "67890");
    private static final String NAME = "RingWorld Halo Scale Trial " + Integer.toHexString(SEED.hashCode());
    private static int phase, settled;
    private static boolean opened;
    private static volatile boolean positioned, previewReady;
    private static volatile String failure;
    private RingHaloTrialClient() { }

    public static boolean tick(Minecraft client) {
        if (!Boolean.getBoolean("ringworld.haloTrial") || phase == 4) return false;
        if (failure != null) {
            RingWorldMod.LOGGER.error("[halo-trial] {}", failure);
            phase = 4;
            return false;
        }
        client.options.pauseOnLostFocus = false;
        client.options.onboardAccessibility = false;
        if (phase == 0 && client.isGameLoadFinished()
                && RingMinecraftClientAccess.screen(client) != null
                && RingMinecraftClientAccess.screen(client).getClass().getSimpleName()
                        .equals("AccessibilityOnboardingScreen")) {
            RingMinecraftClientAccess.setScreen(client, new TitleScreen());
        }
        if (phase == 0) {
            if (!opened) {
                if (!(RingMinecraftClientAccess.screen(client) instanceof TitleScreen)) return true;
                var config = RingWorldConfig.load();
                RingWorldConfig.saveBootstrapLayout(RingHaloTrial.WIDTH, RingHaloTrial.CIRCUMFERENCE,
                        160, config.wallStyle(), config.skyProfile(), RingWorldGenerationSettings.DEFAULT, false);
                client.options.fov().set(70);
                client.options.renderDistance().set(12);
                opened = true;
                if (new java.io.File(client.gameDirectory, "saves/" + NAME + "/level.dat").isFile()) {
                    client.createWorldOpenFlows().openWorld(NAME, () -> failure = "world open cancelled");
                    phase = 1;
                } else CreateWorldScreen.openFresh(client, () -> opened = false);
            } else if (RingMinecraftClientAccess.screen(client) instanceof CreateWorldScreen screen) {
                var state = screen.getUiState();
                state.setName(NAME);
                state.setSeed(SEED);
                state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                state.setAllowCommands(true);
                ((CreateWorldScreenInvoker) screen).ringworld$createLevel();
                phase = 1;
            }
            return true;
        }
        if (phase == 1) {
            if (client.player == null || ClientRingState.geometry() == null) return true;
            if (!RingHaloTrial.active(ClientRingState.geometry())) {
                failure = "unexpected world geometry";
                return true;
            }
            var atlas = ClientRingState.terrainAtlas();
            var worker = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "RingWorld Halo seed preview trial");
                thread.setDaemon(true);
                return thread;
            });
            RingIntegratedCaptureControl.execute(client, "Halo scale setup", context -> {
                RingIntegratedCaptureControl.normalizeEnvironment(context, 6000, false);
                context.player().setGameMode(GameType.SPECTATOR);
                context.player().setYRot(-90);
                context.player().setXRot(-35);
                RingIntegratedCaptureControl.teleport(context, 100_000.5, 160, 0.5);
                CompletableFuture.runAsync(() -> {
                    try {
                        var world = context.world();
                        for (var stage : RingTerrainPreviewStage.values()) {
                            var preview = RingTerrainPreviewSampler.generate(atlas.worldHash(), atlas.geometry(),
                                    stage, world.getChunkSource().getGenerator(),
                                    world.getChunkSource().randomState(), world);
                            if (preview == null) throw new IllegalStateException("seed preview sampler unavailable");
                            var payload = new RingTerrainPreviewPayload(atlas.worldHash(), stage.wireValue(), preview.encode());
                            client.execute(() -> {
                                ClientRingState.installTerrainPreview(payload);
                                if (stage == RingTerrainPreviewStage.ULTRA) previewReady = true;
                            });
                            RingWorldMod.LOGGER.info("[halo-trial] seed={} preview={} {}x{}; no chunks pregenerated",
                                    SEED, stage.logLabel(), preview.columns(), preview.rows());
                        }
                    } catch (Exception exception) {
                        failure = exception.toString();
                        RingWorldMod.LOGGER.error("[halo-trial] preview failed", exception);
                    } finally { worker.shutdown(); }
                }, worker);
            }, () -> positioned = true, message -> { failure = message; worker.shutdown(); });
            RingMinecraftClientAccess.setScreen(client, null);
            phase = 2;
        }
        if (phase == 2 && positioned) {
            client.player.setYRot(-90);
            client.player.setXRot(-35);
        }
        if (phase == 2 && positioned && previewReady
                && dev.ringworld.client.render.RingSurfaceTextureRenderer.displayReady()
                && ++settled >= 60) {
            RingMinecraftClientAccess.setGuiHidden(client, true);
            settled = 0;
            phase = 3;
        }
        if (phase == 3 && ++settled >= 5) {
            RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "halo-literal-scale.png",
                    RingMinecraftClientAccess.mainRenderTarget(client), 1,
                    message -> client.execute(() -> RingMinecraftClientAccess.setGuiHidden(client, false)));
            RingWorldMod.LOGGER.info("[halo-trial] ready: {} around x {} across; seed {}; placeholder-only; game left open",
                    RingHaloTrial.CIRCUMFERENCE, RingHaloTrial.WIDTH, SEED);
            phase = 4;
        }
        return phase != 4;
    }
}
