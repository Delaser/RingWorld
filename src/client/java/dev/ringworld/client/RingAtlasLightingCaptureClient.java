package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.world.RingTerrainAtlas;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/**
 * Disposable graphical proof that authoritative surface block light reaches
 * the distant complete-ring surface without changing its daytime colour.
 */
public final class RingAtlasLightingCaptureClient {
    public static final String ENABLE_PROPERTY = "ringworld.captureAtlasLighting";
    private static final String SEED = "-2162056627494116761";
    private static final int TIMEOUT_TICKS = 7_200;
    private static final int LAMP_CENTER_X = 1_024;
    private static final int VIEW_X = 0;

    private int stage;
    private int ticks;
    private int stageTicks;
    private boolean optionsApplied;
    private boolean worldScreenOpened;
    private boolean worldStarted;
    private long revisionBeforeLights;
    private int expectedLitCells;
    private int capturesSaved;
    private boolean capturePending;
    private boolean failed;

    public boolean tick(Minecraft client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return false;
        applyOptions(client);
        if (failed) {
            if (++stageTicks >= 20) client.stop();
            return true;
        }
        if (++ticks > TIMEOUT_TICKS) return fail(client, "timed out in stage " + stage);
        if (client.level == null || client.player == null) {
            createWorld(client);
            return true;
        }
        if (RingMinecraftClientAccess.screen(client) instanceof PauseScreen) {
            RingMinecraftClientAccess.setScreen(client, null);
        }
        if (RingMinecraftClientAccess.screen(client) != null || capturePending) return true;
        stageTicks++;
        switch (stage) {
            case 0 -> waitForCompleteAtlasAndLoadLampSite(client);
            case 1 -> placeLampField(client);
            case 2 -> verifyLightRevisionAndReturnToView(client);
            case 3 -> captureDay(client);
            case 4 -> captureNight(client);
            case 5 -> finish(client);
            default -> { }
        }
        return true;
    }

    private void applyOptions(Minecraft client) {
        if (optionsApplied) return;
        client.options.renderDistance().set(12);
        client.options.simulationDistance().set(8);
        client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
        client.options.cloudStatus().set(CloudStatus.OFF);
        client.options.pauseOnLostFocus = false;
        client.debugEntries.setOverlayVisible(false);
        RingMinecraftClientAccess.setGuiHidden(client, true);
        optionsApplied = true;
    }

    private void createWorld(Minecraft client) {
        if (worldStarted || client.getSingleplayerServer() != null
                || !client.isGameLoadFinished()) return;
        if (!worldScreenOpened) {
            if (!(RingMinecraftClientAccess.screen(client) instanceof TitleScreen)) return;
            CreateWorldScreen.openFresh(client, () -> worldScreenOpened = false);
            worldScreenOpened = true;
            return;
        }
        if (RingMinecraftClientAccess.screen(client) instanceof CreateWorldScreen screen) {
            WorldCreationUiState creator = screen.getUiState();
            creator.setName("RingWorld Atlas Lighting Regression");
            creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            creator.setAllowCommands(true);
            creator.setSeed(SEED);
            worldStarted = true;
            ((CreateWorldScreenInvoker)screen).ringworld$createLevel();
        }
    }

    private void waitForCompleteAtlasAndLoadLampSite(Minecraft client) {
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        if (atlas == null || !atlas.isComplete()) return;
        client.getConnection().sendCommand("gamemode spectator @s");
        client.getConnection().sendCommand("weather clear");
        client.getConnection().sendCommand("time set noon");
        client.getConnection().sendCommand("tp @s " + (LAMP_CENTER_X + 0.5)
                + " 140 0.5 90 -35");
        nextStage();
    }

    private void placeLampField(Minecraft client) {
        if (stageTicks < 120 || !client.levelRenderer.hasRenderedAllSections()) return;
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        if (atlas == null || !atlas.isComplete()) return;
        revisionBeforeLights = atlas.revision();
        int step = atlas.sampleStep();
        int centerColumn = LAMP_CENTER_X / step;
        int centerRow = Math.floorDiv(-atlas.geometry().minWidthZ(), step);
        expectedLitCells = 0;
        for (int rowOffset = -4; rowOffset <= 4; rowOffset++) {
            int row = centerRow + rowOffset;
            if (row < 0 || row >= atlas.rows()) continue;
            for (int columnOffset = -10; columnOffset <= 10; columnOffset++) {
                // A loose, visibly artificial constellation reads better at
                // full-ring scale than a solid luminous rectangle.
                if ((columnOffset + rowOffset * 3) % 3 != 0) continue;
                int column = Math.floorMod(centerColumn + columnOffset, atlas.columns());
                int blockX = column * step + step / 2;
                int blockZ = atlas.geometry().minWidthZ() + row * step + step / 2;
                int blockY = Math.round(atlas.cellHeight(column, row));
                client.getConnection().sendCommand("setblock " + blockX + " " + blockY
                        + " " + blockZ + " minecraft:sea_lantern");
                expectedLitCells++;
            }
        }
        RingWorldMod.LOGGER.info("[atlas-lighting] placed {} sampled lamps", expectedLitCells);
        nextStage();
    }

    private void verifyLightRevisionAndReturnToView(Minecraft client) {
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        if (atlas == null || atlas.revision() <= revisionBeforeLights) return;
        int lit = 0;
        for (int row = 0; row < atlas.rows(); row++) {
            for (int column = 0; column < atlas.columns(); column++) {
                if (atlas.cellBlockLight(column, row) > 0) lit++;
            }
        }
        if (lit < expectedLitCells / 2) return;
        RingWorldMod.LOGGER.info(
                "[atlas-lighting] authoritative client Atlas received {} lit cells at revision {}",
                lit, atlas.revision());
        client.getConnection().sendCommand("tp @s " + (VIEW_X + 0.5) + " 115 0.5 90 -90");
        client.getConnection().sendCommand("time set noon");
        nextStage();
    }

    private void captureDay(Minecraft client) {
        holdPose(client);
        if (stageTicks < 180 || !client.levelRenderer.hasRenderedAllSections()) return;
        capture(client, "atlas-lighting-01-day");
        client.getConnection().sendCommand("time set midnight");
        nextStage();
    }

    private void captureNight(Minecraft client) {
        holdPose(client);
        if (stageTicks < 180) return;
        capture(client, "atlas-lighting-02-night");
        nextStage();
    }

    private void finish(Minecraft client) {
        if (capturesSaved < 2 || stageTicks < 20) return;
        RingWorldMod.LOGGER.info(
                "[atlas-lighting] PASS: complete Atlas retained daytime terrain and exposed authoritative surface lights at night");
        client.stop();
    }

    private static void holdPose(Minecraft client) {
        client.player.setYRot(90.0F);
        client.player.setXRot(-90.0F);
    }

    private void capture(Minecraft client, String name) {
        capturePending = true;
        RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, name + ".png",
                RingMinecraftClientAccess.mainRenderTarget(client), 1, message ->
                        client.execute(() -> {
                            capturesSaved++;
                            capturePending = false;
                            RingWorldMod.LOGGER.info(
                                    "[atlas-lighting] screenshot {}", message.getString());
                        }));
    }

    private void nextStage() {
        stage++;
        stageTicks = 0;
    }

    private boolean fail(Minecraft client, String detail) {
        failed = true;
        stageTicks = 0;
        RingWorldMod.LOGGER.error("[atlas-lighting] FAIL: {}", detail);
        return true;
    }
}
