package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingWallStyle;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Opt-in interactive setup for reviewing Atlas lights around a complete Medium
 * ring. It opens one descriptively named disposable save, scatters villages at
 * terrain height, and then returns control to the player at midnight.
 */
public final class RingMediumIndustrialLightingTestClient {
    public static final String ENABLE_PROPERTY = "ringworld.mediumIndustrialLightingTest";
    public static final String WORLD_NAME =
            "RingWorld Medium Industrial Village Lighting Test";
    private static final int EXPECTED_CIRCUMFERENCE = 16_384;
    private static final int EXPECTED_WIDTH = 256;
    private static final int SITE_SETTLE_TICKS = 100;
    private static final int FINAL_SETTLE_TICKS = 240;
    private static final VillageSite[] VILLAGES = {
            new VillageSite(1_024, -72),
            new VillageSite(3_072, 24),
            new VillageSite(5_120, 72),
            new VillageSite(7_168, -24),
            new VillageSite(9_216, -72),
            new VillageSite(11_264, 24),
            new VillageSite(13_312, 72),
            new VillageSite(15_360, -24)
    };

    private boolean optionsApplied;
    private boolean worldOpenRequested;
    private boolean setupStarted;
    private boolean setupComplete;
    private int villageIndex;
    private int stageTicks;
    private long atlasRevisionBeforeVillages;

    public boolean tick(Minecraft client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || setupComplete) return false;
        applyOptions(client);
        if (client.level == null || client.player == null) {
            openWorld(client);
            return true;
        }
        if (RingMinecraftClientAccess.screen(client) instanceof PauseScreen) {
            RingMinecraftClientAccess.setScreen(client, null);
        }
        if (RingMinecraftClientAccess.screen(client) != null) return true;

        RingGeometry geometry = ClientRingState.geometry();
        RingTerrainAtlas atlas = ClientRingState.terrainAtlas();
        if (geometry == null || atlas == null || !atlas.isComplete()) return true;
        if (!setupStarted) {
            if (!validateWorld(client, geometry)) return false;
            setupStarted = true;
            atlasRevisionBeforeVillages = atlas.revision();
            client.getConnection().sendCommand("gamemode creative @s");
            client.getConnection().sendCommand("weather clear");
            client.getConnection().sendCommand("time set midnight");
            RingWorldMod.LOGGER.info(
                    "[medium-industrial-lighting] complete Atlas ready; placing {} villages",
                    VILLAGES.length);
        }

        if (villageIndex < VILLAGES.length) {
            placeNextVillage(client);
            return true;
        }
        if (++stageTicks < FINAL_SETTLE_TICKS
                || ClientRingState.terrainAtlas().revision() <= atlasRevisionBeforeVillages) {
            return true;
        }
        client.getConnection().sendCommand("gamemode spectator @s");
        client.getConnection().sendCommand("weather clear");
        client.getConnection().sendCommand("time set midnight");
        client.getConnection().sendCommand("tp @s 0.5 140 0.5 90 -82");
        client.player.sendSystemMessage(Component.literal(
                "Medium Industrial ring ready: eight villages placed; Atlas lights updated."));
        RingWorldMod.LOGGER.info(
                "[medium-industrial-lighting] READY: eight villages placed and light revision {} received",
                ClientRingState.terrainAtlas().revision());
        setupComplete = true;
        return false;
    }

    private void placeNextVillage(Minecraft client) {
        VillageSite site = VILLAGES[villageIndex];
        if (stageTicks++ == 0) {
            client.getConnection().sendCommand(
                    "tp @s " + (site.x() + 0.5) + " 150 " + (site.z() + 0.5) + " 0 45");
            return;
        }
        BlockPos probe = new BlockPos(site.x(), 64, site.z());
        if (stageTicks < SITE_SETTLE_TICKS || !client.level.hasChunkAt(probe)) return;
        int surfaceY = client.level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site.x(), site.z());
        client.getConnection().sendCommand("place structure minecraft:village_plains "
                + site.x() + " " + surfaceY + " " + site.z());
        villageIndex++;
        stageTicks = 0;
        RingWorldMod.LOGGER.info(
                "[medium-industrial-lighting] placed village {}/{} at {},{},{}",
                villageIndex, VILLAGES.length, site.x(), surfaceY, site.z());
    }

    private boolean validateWorld(Minecraft client, RingGeometry geometry) {
        RingWallStyle expected = RingWallStyle.Preset.INDUSTRIAL_SUPERSTRUCTURE.style();
        if (geometry.circumferenceBlocks() == EXPECTED_CIRCUMFERENCE
                && geometry.widthBlocks() == EXPECTED_WIDTH
                && ClientRingState.wallStyle().equals(expected)) {
            return true;
        }
        setupComplete = true;
        String message = "Wrong lighting test world: expected Medium 16384x256 with Industrial walls.";
        client.player.sendSystemMessage(Component.literal(message));
        RingWorldMod.LOGGER.error("[medium-industrial-lighting] {}", message);
        return false;
    }

    private void openWorld(Minecraft client) {
        if (worldOpenRequested || client.getSingleplayerServer() != null
                || !client.isGameLoadFinished()
                || !(RingMinecraftClientAccess.screen(client) instanceof TitleScreen)) return;
        worldOpenRequested = true;
        client.createWorldOpenFlows().openWorld(WORLD_NAME, () -> {
            worldOpenRequested = false;
            RingWorldMod.LOGGER.error(
                    "[medium-industrial-lighting] world open cancelled: {}", WORLD_NAME);
        });
    }

    private void applyOptions(Minecraft client) {
        if (optionsApplied) return;
        client.options.renderDistance().set(28);
        client.options.simulationDistance().set(8);
        client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
        client.options.pauseOnLostFocus = false;
        optionsApplied = true;
    }

    private record VillageSite(int x, int z) { }
}
