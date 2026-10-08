package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.Level;

/** Opt-in, disposable horizon regression; normal clients never enter this fixture. */
public final class RingHorizonCaptureClient {
    private static final RingHorizonCaptureClient INSTANCE = new RingHorizonCaptureClient();
    private int stage;
    private int ticks;
    private int settle;
    private boolean opening;
    private boolean creating;
    private boolean capturePending;
    private CompletableFuture<Void> reload;

    public static boolean tickIfEnabled(Minecraft client) {
        return Boolean.getBoolean("ringworld.captureHorizon") && INSTANCE.tick(client);
    }

    private boolean tick(Minecraft client) {
        client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
        if (++ticks > 6_000) return fail(client, "timeout stage=" + stage);
        if (client.level == null || client.player == null) {
            if (!client.isGameLoadFinished() || creating) return true;
            if (!opening && RingMinecraftClientAccess.screen(client) instanceof TitleScreen) {
                opening = true;
                CreateWorldScreen.openFresh(client, () -> opening = false);
            } else if (RingMinecraftClientAccess.screen(client) instanceof CreateWorldScreen screen) {
                var creator = screen.getUiState();
                creator.setName("RingWorld Horizon Regression");
                creator.setSeed("259");
                creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                creator.setAllowCommands(true);
                creating = true;
                ((CreateWorldScreenInvoker) screen).ringworld$createLevel();
            }
            return true;
        }
        client.options.pauseOnLostFocus = false;
        client.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
        client.options.vignette().set(false);
        RingMinecraftClientAccess.setGuiHidden(client, true);
        if (capturePending || RingMinecraftClientAccess.screen(client) != null) return true;
        if (stage == 0) {
            if (ClientRingState.geometry() == null) return true;
            client.getConnection().sendCommand("gamemode spectator @s");
            if (Boolean.getBoolean("ringworld.horizonBlindnessControl")) {
                client.getConnection().sendCommand("effect give @s minecraft:blindness 120 0 true");
            }
            client.getConnection().sendCommand("gamerule minecraft:advance_time false");
            next();
        } else if (stage <= captureCount()) {
            if (client.level.dimension() != Level.OVERWORLD || ClientRingState.geometry() == null) return true;
            int index = stage - 1;
            boolean scene = Boolean.getBoolean("ringworld.horizonSceneControl");
            String phase = scene ? "scene-day" : PHASES[index / 2];
            boolean elevated = index % 2 == 1;
            if (settle++ == 0) {
                client.getConnection().sendCommand("time set " + (scene ? 6000 : TIMES[index / 2]));
                client.getConnection().sendCommand("weather " + WEATHER[index / 2]);
                client.getConnection().sendCommand(scene
                        ? "tp @s 512.5 " + (elevated ? "160 0 90 -70" : "80 0 90 -20")
                        : "tp @s 512.5 " + (elevated ? "160 96.5" : "80 96.5") + " 0 0");
            }
            if (settle < (index / 2 >= 4 ? 220 : 100)) return true;
            capture(client, "horizon-" + phase + (elevated ? "-elevated" : "-ground"));
            next();
        } else if (stage == captureCount() + 1) {
            if (reload == null) reload = client.reloadResourcePacks();
            if (!reload.isDone()) return true;
            if (reload.isCompletedExceptionally()) return fail(client, "resource reload failed");
            if (++settle < 100) return true;
            capture(client, "horizon-after-reload");
            client.getConnection().sendCommand("execute in minecraft:the_nether run tp @s 0.5 100 0.5 45 -30");
            next();
        } else if (stage == captureCount() + 2 || stage == captureCount() + 3) {
            boolean nether = stage == captureCount() + 2;
            if (client.level.dimension() != (nether ? Level.NETHER : Level.END)) return true;
            if (ClientRingState.geometry() != null) return fail(client, "RingWorld active outside Overworld");
            if (++settle < 100) return true;
            capture(client, nether ? "horizon-nether-control" : "horizon-end-control");
            client.getConnection().sendCommand(nether
                    ? "execute in minecraft:the_end run tp @s 10000.5 200 10000.5 45 -30"
                    : "execute in minecraft:overworld run tp @s 512.5 160 96.5 0 0");
            next();
        } else if (stage == captureCount() + 4) {
            if (client.level.dimension() != Level.OVERWORLD || ClientRingState.geometry() == null) return true;
            if (++settle < 100) return true;
            capture(client, "horizon-overworld-return");
            next();
        } else {
            RingWorldMod.LOGGER.info("[horizon-test] CAPTURE COMPLETE: phases, reload and dimension controls; pixel review required");
            client.stop();
        }
        return true;
    }

    private static final String[] PHASES = {"sunset", "sunrise", "day", "night", "rain", "thunder"};
    private static final int[] TIMES = {12800, 23200, 6000, 18000, 6000, 12800};
    private static final String[] WEATHER = {"clear", "clear", "clear", "clear", "rain", "thunder"};

    private static int captureCount() {
        return Boolean.getBoolean("ringworld.horizonCaptureShort")
                || Boolean.getBoolean("ringworld.horizonSceneControl") ? 2 : PHASES.length * 2;
    }

    private void capture(Minecraft client, String name) {
        capturePending = true;
        RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, name + ".png",
                RingMinecraftClientAccess.mainRenderTarget(client), 1, message -> client.execute(() -> {
                    capturePending = false;
                    RingWorldMod.LOGGER.info("[horizon-test] screenshot {}", message.getString());
                }));
    }

    private void next() { stage++; settle = 0; }

    private boolean fail(Minecraft client, String reason) {
        RingWorldMod.LOGGER.error("[horizon-test] FAIL: {}", reason);
        client.stop();
        return true;
    }
}
