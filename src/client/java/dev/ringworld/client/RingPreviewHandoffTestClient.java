package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.world.RingAtlasFidelity;
import dev.ringworld.world.RingTerrainPreviewStage;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingWorldGenerationSettings;
import dev.ringworld.world.RingWorldLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;

/** Opt-in disposable same-process staged-preview cancellation regression. */
final class RingPreviewHandoffTestClient {
    static final String ENABLE_PROPERTY = "ringworld.previewHandoffTest";
    private int phase;
    private int worldIndex;
    private int settleTicks;
    private long started = System.nanoTime();
    private long secondJoin;
    private long firstHash;
    private boolean editorOpened;
    private volatile boolean captureSaved;

    boolean tick(Minecraft client) {
        client.options.pauseOnLostFocus = false;
        if ((System.nanoTime() - started) / 1_000_000_000L > 600) {
            return fail(client, "timed out in phase " + phase);
        }
        if (phase == 0) {
            if (!editorOpened) {
                if (!(RingMinecraftClientAccess.screen(client) instanceof TitleScreen)) return true;
                var config = RingWorldConfig.load();
                RingWorldConfig.saveBootstrapLayout(worldIndex == 0 ? 256 : 128,
                        worldIndex == 0 ? 16384 : 2048, 160,
                        config.wallStyle(), config.skyProfile(), new RingWorldGenerationSettings(
                                RingAtlasFidelity.VERY_HIGH, RingWorldLayout.VANILLA, false, false,
                                RingWorldGenerationSettings.FORMAT_VERSION), false);
                CreateWorldScreen.openFresh(client, () -> editorOpened = false);
                editorOpened = true;
            } else if (RingMinecraftClientAccess.screen(client) instanceof CreateWorldScreen screen) {
                var creator = screen.getUiState();
                creator.setName(worldIndex == 0 ? "RingWorld Preview Handoff Medium" : "RingWorld Preview Handoff Small");
                creator.setSeed(worldIndex == 0 ? "12345" : "67890");
                creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                creator.setAllowCommands(true);
                ((CreateWorldScreenInvoker) screen).ringworld$createLevel();
                phase = 1;
            }
            return true;
        }
        if (phase == 1) {
            if (client.player == null || ClientRingState.terrainAtlas() == null) return true;
            var atlas = ClientRingState.terrainAtlas();
            int expectedCircumference = worldIndex == 0 ? 16384 : 2048;
            if (atlas.sampleStep() != 2 || atlas.geometry().circumferenceBlocks() != expectedCircumference) {
                return fail(client, "wrong geometry or Atlas fidelity");
            }
            if (worldIndex == 0) {
                if (ClientRingState.terrainPreviewStage() < RingTerrainPreviewStage.VERY_HIGH.wireValue()) return true;
                if (ClientRingState.terrainPreviewStage() > RingTerrainPreviewStage.VERY_HIGH.wireValue()
                        || atlas.isComplete()) return fail(client, "old world finished before cancellation");
                firstHash = atlas.worldHash();
                RingWorldMod.LOGGER.info("[preview-handoff] leaving Medium after VERY_HIGH; hash={}", firstHash);
                client.disconnectFromWorld(Component.literal("RingWorld preview worker handoff regression"));
                phase = 2;
            } else {
                secondJoin = System.nanoTime();
                phase = 3;
            }
            return true;
        }
        if (phase == 2 || phase == 5) {
            if (client.level != null || client.getSingleplayerServer() != null
                    || !RingWorldClientSession.isCleared()) return true;
            if (phase == 5) {
                RingWorldMod.LOGGER.info("[preview-handoff] PASS: Medium cancellation, Small CURRENT preview, capture, normal disconnect-clear");
                phase = 6;
                client.stop();
            } else {
                RingWorldMod.LOGGER.info("[preview-handoff] first disconnect-clear");
                worldIndex = 1;
                editorOpened = false;
                RingMinecraftClientAccess.setScreen(client, new TitleScreen());
                phase = 0;
            }
            return true;
        }
        if (phase == 3) {
            var atlas = ClientRingState.terrainAtlas();
            if (atlas == null || atlas.worldHash() == firstHash || atlas.isComplete()) {
                return fail(client, "Small preview missing before completion or stale identity");
            }
            long elapsedMs = (System.nanoTime() - secondJoin) / 1_000_000;
            if (elapsedMs > 15000) return fail(client, "Small CURRENT preview exceeded 15 seconds after join");
            var preview = ClientRingState.terrainPreview();
            if (preview == null) return true;
            if (ClientRingState.terrainPreviewStage() != RingTerrainPreviewStage.CURRENT.wireValue()
                    || preview.columns() != 512 || preview.rows() != 16) {
                return fail(client, "did not observe Small CURRENT 512x16 stage");
            }
            RingWorldMod.LOGGER.info("[preview-handoff] Small CURRENT 512x16 received in {} ms; present={}/{}; hash={}",
                    elapsedMs, atlas.presentCount(), atlas.cellCount(), atlas.worldHash());
            client.player.setXRot(-65.0F);
            RingMinecraftClientAccess.setScreen(client, null);
            phase = 4;
            return true;
        }
        if (phase == 4 && ++settleTicks == 10) {
            RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "preview-handoff-small.png",
                    RingMinecraftClientAccess.mainRenderTarget(client), 1, message -> captureSaved = true);
        }
        if (phase == 4 && captureSaved) {
            client.disconnectFromWorld(Component.literal("RingWorld preview handoff complete"));
            phase = 5;
        }
        return true;
    }

    private boolean fail(Minecraft client, String reason) {
        RingWorldMod.LOGGER.error("[preview-handoff] FAIL: {}", reason);
        phase = 6;
        client.stop();
        return true;
    }
}
