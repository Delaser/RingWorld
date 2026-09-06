package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/** Opt-in matched, completed-Atlas captures in four newly created disposable worlds. */
final class RingAtlasFidelityGalleryClient {
    private final RingAtlasFidelity[] profiles = System.getProperty("ringworld.fidelityGalleryProfile", "all").equals("all")
            ? RingAtlasFidelity.values()
            : new RingAtlasFidelity[]{RingAtlasFidelity.valueOf(System.getProperty("ringworld.fidelityGalleryProfile"))};
    private final long measurementNanos = Long.getLong("ringworld.fidelityGallerySeconds", 10L) * 1_000_000_000L;
    private final boolean compareMesh = Boolean.getBoolean("ringworld.fidelityGalleryCompareMesh");
    private final boolean leaveOpen = Boolean.getBoolean("ringworld.fidelityGalleryLeaveOpen");
    private int comparisonIndex;
    private int index;
    private int phase;
    private int settled;
    private boolean measuring;
    private boolean captureRequested;
    private long lastFrame;
    private long totalFrameNanos;
    private long maxFrameNanos;
    private int frameSamples;
    private int slowFrames;

    void frameRendered() {
        if (!measuring) return;
        long now = System.nanoTime();
        if (lastFrame != 0) {
            long elapsed = now - lastFrame;
            totalFrameNanos += elapsed;
            maxFrameNanos = Math.max(maxFrameNanos, elapsed);
            frameSamples++;
            if (elapsed > 50_000_000L) slowFrames++;
        }
        lastFrame = now;
    }
    private boolean opened;
    private volatile boolean positioned;
    private volatile boolean captured;
    private volatile String failure;
    private final long started = System.nanoTime();

    boolean tick(Minecraft client) {
        if (phase == 5) return true;
        if (Boolean.getBoolean("ringworld.fidelityGalleryResume")) {
            if (!opened && RingMinecraftClientAccess.screen(client) instanceof TitleScreen) {
                String worldName = System.getProperty("ringworld.fidelityGalleryWorld",
                        "RingWorld Atlas Fidelity " + profiles[0].label());
                client.createWorldOpenFlows().openWorld(worldName, () -> failure = "world open cancelled");
                opened = true;
            }
            if (failure != null) return fail(client, failure);
            if (client.player != null && ClientRingState.terrainAtlas() != null) {
                var dispatcher = client.getConnection().getCommands();
                var lod = dispatcher.getRoot().getChild("ringworld").getChild("lod");
                if (lod == null || lod.getChild("high") == null) return fail(client, "LOD chat tree missing");
                client.getConnection().sendCommand("ringworld lod high");
                if (RingClientLodTuning.quality() != RingLodQuality.HIGH) return fail(client, "LOD execution failed");
                RingMinecraftClientAccess.setGuiHidden(client, false);
                org.lwjgl.glfw.GLFW.glfwShowWindow(client.getWindow().handle());
                RingWorldMod.LOGGER.info("[fidelity-gallery] PASS: resumed owner world; LOD chat tree and execution verified");
                phase = 5;
            }
            return true;
        }
        client.options.pauseOnLostFocus = false;
        client.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
        client.options.fov().set(30);
        RingMinecraftClientAccess.setGuiHidden(client, true);
        if (failure != null) return fail(client, failure);
        if ((System.nanoTime() - started) / 1_000_000_000L > 1800) return fail(client, "timeout phase=" + phase);
        if (phase == 4) return true;
        var fidelity = profiles[index];
        if (phase == 0) {
            if (!opened) {
                if (!(RingMinecraftClientAccess.screen(client) instanceof TitleScreen)) return true;
                var config = RingWorldConfig.load();
                RingWorldConfig.saveBootstrapLayout(128, 2048, 160,
                        config.wallStyle(), config.skyProfile(), new RingWorldGenerationSettings(
                                fidelity, RingWorldLayout.VANILLA, false, false,
                                RingWorldGenerationSettings.FORMAT_VERSION), false);
                String worldName = "RingWorld Atlas Fidelity " + fidelity.label();
                if (new java.io.File(client.gameDirectory, "saves/" + worldName + "/level.dat").isFile()) {
                    client.createWorldOpenFlows().openWorld(worldName, () -> failure = "world open cancelled");
                    opened = true;
                    phase = 1;
                    return true;
                }
                CreateWorldScreen.openFresh(client, () -> opened = false);
                opened = true;
            } else if (RingMinecraftClientAccess.screen(client) instanceof CreateWorldScreen screen) {
                var creator = screen.getUiState();
                creator.setName("RingWorld Atlas Fidelity " + fidelity.label());
                creator.setSeed("67890");
                creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                creator.setAllowCommands(true);
                ((CreateWorldScreenInvoker) screen).ringworld$createLevel();
                phase = 1;
            }
            return true;
        }
        if (phase == 1) {
            var atlas = ClientRingState.terrainAtlas();
            if (client.player == null || atlas == null || !atlas.isComplete()) return true;
            if (atlas.sampleStep() != fidelity.sampleStepBlocks()) return fail(client, "wrong fidelity");
            if (compareMesh) {
                var requested = comparisonIndex == 0 ? RingLodQuality.MEDIUM : RingLodQuality.HIGH;
                client.getConnection().sendCommand("ringworld lod " + requested.command());
                if (RingClientLodTuning.quality() != requested) return fail(client, "client LOD command did not select " + requested);
            }
            RingIntegratedCaptureControl.execute(client, "fidelity capture setup", context -> {
                RingIntegratedCaptureControl.normalizeEnvironment(context, 6000, false);
                RingIntegratedCaptureControl.teleport(context, 512.5, 340, 0.5);
            }, () -> positioned = true, detail -> failure = detail);
            phase = 2;
            return true;
        }
        if (phase == 2) {
            if (!positioned) return true;
            RingMinecraftClientAccess.setScreen(client, null);
            client.player.setYRot(0);
            client.player.setXRot(-90);
            if (Math.abs(client.player.getX() - 512.5) > 1 || Math.abs(client.player.getY() - 340) > 1) return true;
            if (!dev.ringworld.client.render.RingSurfaceTextureRenderer.displayReady()) return true;
            if (!client.levelRenderer.hasRenderedAllSections()) { settled = 0; return true; }
            if (++settled == 100) measuring = true;
            if (!captureRequested && totalFrameNanos >= measurementNanos) {
                measuring = false;
                captureRequested = true;
                RingWorldMod.LOGGER.info("[fidelity-gallery] FPS {}: frames={}, durationNs={}, averageFps={}, maxFrameMs={}, over50ms={}",
                        compareMesh ? RingClientLodTuning.quality() : fidelity, frameSamples, totalFrameNanos,
                        frameSamples * 1_000_000_000.0 / totalFrameNanos,
                        maxFrameNanos / 1_000_000.0, slowFrames);
                var atlas = ClientRingState.terrainAtlas();
                RingWorldMod.LOGGER.info("[fidelity-gallery] capturing {}: {}/{} cells, step={}, pose=512.5,340,0.5 yaw=0 pitch=-90 fov=30",
                        compareMesh ? RingClientLodTuning.quality() : fidelity, atlas.presentCount(), atlas.cellCount(), atlas.sampleStep());
                RingMinecraftClientAccess.grabScreenshot(client.gameDirectory,
                        "atlas-" + (compareMesh ? RingClientLodTuning.quality().command() : fidelity.name().toLowerCase(Locale.ROOT)) + ".png",
                        RingMinecraftClientAccess.mainRenderTarget(client), 1, message -> captured = true);
            }
            if (captured) {
                if (compareMesh && comparisonIndex++ == 0) {
                    positioned = false; captured = false; settled = 0; phase = 1;
                    measuring = false; captureRequested = false; lastFrame = 0;
                    totalFrameNanos = 0; maxFrameNanos = 0; frameSamples = 0; slowFrames = 0;
                    return true;
                }
                if (leaveOpen) {
                    phase = 5;
                    RingMinecraftClientAccess.setGuiHidden(client, false);
                    org.lwjgl.glfw.GLFW.glfwShowWindow(client.getWindow().handle());
                    client.getConnection().sendCommand("ringworld lod show");
                    RingWorldMod.LOGGER.info("[fidelity-gallery] PASS: captures complete; leaving world open for owner");
                    return true;
                }
                client.disconnectFromWorld(Component.literal("Atlas fidelity capture complete"));
                phase = 3;
            }
            return true;
        }
        if (phase == 3) {
            if (client.level != null || client.getSingleplayerServer() != null || !RingWorldClientSession.isCleared()) return true;
            if (++index == profiles.length) {
                RingWorldMod.LOGGER.info("[fidelity-gallery] PASS: selected completed Atlas captures and clean disconnects");
                phase = 4;
                client.stop();
            } else {
                opened = false; positioned = false; captured = false; settled = 0; phase = 0;
                measuring = false; captureRequested = false; lastFrame = 0;
                totalFrameNanos = 0; maxFrameNanos = 0; frameSamples = 0; slowFrames = 0;
                RingMinecraftClientAccess.setScreen(client, new TitleScreen());
            }
        }
        return true;
    }

    private boolean fail(Minecraft client, String reason) {
        RingWorldMod.LOGGER.error("[fidelity-gallery] FAIL: {}", reason);
        phase = 4;
        client.stop();
        return true;
    }
}
