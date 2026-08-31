package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSkyProfile;
import dev.ringworld.world.RingWallStyle;
import dev.ringworld.world.RingWorldConfig;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;

/** Opt-in disposable gallery fixture for matched wall and sky review captures. */
public final class RingAppearanceComparisonCaptureClient {
    public static final String ENABLE_PROPERTY = "ringworld.captureAppearanceComparison";
    private static final String SEED = "-2162056627494116761";
    private static final int WIDTH = 128;
    private static final int CIRCUMFERENCE = 2_048;
    private static final int WALL_HEIGHT = 160;
    private static final int TIMEOUT_TICKS = 2_400;
    private static final int RIM_SETTLE_TICKS = 240;
    private static final int SKY_SETTLE_TICKS = 100;
    private static final String SKY_WORLD = "Appearance 01 Weathered";
    private static final SkyVariant[] SKY_VARIANTS = {
            new SkyVariant("sky-01-atmosphere-small", RingSkyProfile.Backdrop.ATMOSPHERE,
                    RingSkyProfile.LightSource.SMALL, 0.25),
            new SkyVariant("sky-02-night-small", RingSkyProfile.Backdrop.NIGHT,
                    RingSkyProfile.LightSource.SMALL, 0.25),
            new SkyVariant("sky-02b-night-small-opposite", RingSkyProfile.Backdrop.NIGHT,
                    RingSkyProfile.LightSource.SMALL, 0.75),
            new SkyVariant("sky-03-void-small", RingSkyProfile.Backdrop.VOID,
                    RingSkyProfile.LightSource.SMALL, 0.25),
            new SkyVariant("sky-04-atmosphere-large", RingSkyProfile.Backdrop.ATMOSPHERE,
                    RingSkyProfile.LightSource.LARGE, 0.25),
            new SkyVariant("sky-05-atmosphere-none", RingSkyProfile.Backdrop.ATMOSPHERE,
                    RingSkyProfile.LightSource.NONE, 0.25, false),
            new SkyVariant("sky-06-atmosphere-dusk-wall-horizon",
                    RingSkyProfile.Backdrop.ATMOSPHERE,
                    RingSkyProfile.LightSource.SMALL, 0.25, true)
    };

    private int presetIndex;
    private int skyIndex = -1;
    private int ticks;
    private int finishTicks;
    private boolean optionsApplied;
    private boolean creationScreenRequested;
    private boolean creationCommitted;
    private boolean rimPoseRequested;
    private boolean industrialCorridorCleared;
    private boolean rimCaptured;
    private boolean skyPoseRequested;
    private boolean disconnectRequested;
    private boolean failed;
    private boolean skyWorldOpenRequested;
    private final boolean skyOnly = Boolean.getBoolean(
            "ringworld.captureAppearanceSkyOnly");
    private final boolean industrialPatternsOnly = Boolean.getBoolean(
            "ringworld.captureIndustrialPatterns");

    public boolean tick(Minecraft client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return false;
        applyOptions(client);
        if (failed) {
            if (++finishTicks >= 20) client.stop();
            return true;
        }
        if (presetIndex >= wallVariantCount()) {
            if (++finishTicks == 1) {
                RingWorldMod.LOGGER.info(
                        "[appearance-comparison] PASS: all rim and sky captures complete");
            }
            if (finishTicks >= 20) client.stop();
            return true;
        }
        if (client.level == null || client.player == null) {
            if (skyOnly) {
                openSkyWorld(client);
                return true;
            }
            createNextWorld(client);
            return true;
        }
        if (RingMinecraftClientAccess.screen(client) instanceof PauseScreen) {
            RingMinecraftClientAccess.setScreen(client, null);
        }
        if (RingMinecraftClientAccess.screen(client) != null) return true;
        captureWorld(client);
        return true;
    }

    private void openSkyWorld(Minecraft client) {
        if (++ticks > TIMEOUT_TICKS) {
            fail("timed out opening sky comparison world");
            return;
        }
        if (client.getSingleplayerServer() != null || !client.isGameLoadFinished()
                || skyWorldOpenRequested) return;
        skyWorldOpenRequested = true;
        client.createWorldOpenFlows().openWorld(SKY_WORLD,
                () -> fail("sky comparison world load cancelled"));
    }

    private void applyOptions(Minecraft client) {
        if (optionsApplied) return;
        client.options.renderDistance().set(industrialPatternsOnly ? 28 : 10);
        client.options.simulationDistance().set(5);
        client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
        client.options.pauseOnLostFocus = false;
        RingMinecraftClientAccess.setGuiHidden(client, true);
        optionsApplied = true;
    }

    private void createNextWorld(Minecraft client) {
        if (disconnectRequested) {
            if (client.getSingleplayerServer() != null) return;
            disconnectRequested = false;
            creationScreenRequested = false;
            creationCommitted = false;
            rimPoseRequested = false;
            industrialCorridorCleared = false;
            rimCaptured = false;
            skyPoseRequested = false;
            skyIndex = -1;
            ticks = 0;
        }
        if (++ticks > TIMEOUT_TICKS) {
            fail("timed out creating wall variant " + currentWallLabel());
            return;
        }
        if (client.getSingleplayerServer() != null || !client.isGameLoadFinished()) return;
        if (!creationScreenRequested) {
            RingWorldConfig.saveBootstrapLayout(
                    WIDTH, CIRCUMFERENCE, WALL_HEIGHT, currentWallStyle(),
                    RingSkyProfile.DEFAULT, false);
            CreateWorldScreen.openFresh(client, () -> creationScreenRequested = false);
            creationScreenRequested = true;
            RingWorldMod.LOGGER.info("[appearance-comparison] creating {}", currentWallLabel());
            return;
        }
        if (!creationCommitted
                && RingMinecraftClientAccess.screen(client) instanceof CreateWorldScreen screen) {
            WorldCreationUiState creator = screen.getUiState();
            creator.setName(industrialPatternsOnly
                    ? String.format("Industrial Clearwall Pattern %02d %s", presetIndex + 1,
                            currentWallLabel())
                    : String.format("Appearance %02d %s", presetIndex + 1,
                            currentWallLabel()));
            creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            creator.setAllowCommands(true);
            creator.setSeed(SEED);
            creationCommitted = true;
            ((CreateWorldScreenInvoker) screen).ringworld$createLevel();
        }
    }

    private void captureWorld(Minecraft client) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || ++ticks > TIMEOUT_TICKS) {
            if (ticks > TIMEOUT_TICKS) fail("timed out loading " + currentWallLabel());
            return;
        }
        if (skyOnly) {
            if (skyIndex < 0) {
                skyIndex = 0;
                ticks = 0;
            }
            if (skyIndex < SKY_VARIANTS.length) {
                captureSky(client);
            } else if (++finishTicks >= 20) {
                RingWorldMod.LOGGER.info(
                        "[appearance-comparison] PASS: corrected sky captures complete");
                client.stop();
            }
            return;
        }
        if (!rimPoseRequested) {
            rimPoseRequested = true;
            ticks = 0;
            client.getConnection().sendCommand("time set 6000");
            client.getConnection().sendCommand("weather clear");
            if (industrialPatternsOnly) {
                client.getConnection().sendCommand("gamemode spectator @s");
            }
            double y = industrialPatternsOnly ? 82.0 : 108.0;
            double z = industrialPatternsOnly ? 24.5 : geometry.maxWidthZ() - 35.5;
            float pitch = industrialPatternsOnly ? 0.0F : 18.0F;
            client.getConnection().sendCommand("tp @s "
                    + (CIRCUMFERENCE / 4.0 + 0.5) + " " + y + " " + z
                    + " 0 " + pitch);
            return;
        }
        if (industrialPatternsOnly && !industrialCorridorCleared) {
            // Teleport first, then allow its view ticket to load the complete
            // corridor. Remove only disposable foreground terrain, stopping
            // one block before the generated inner wall face at Z=59.
            if (ticks < 60) return;
            client.getConnection().sendCommand("fill 480 64 20 544 79 44 air replace");
            client.getConnection().sendCommand("fill 480 80 20 544 95 44 air replace");
            client.getConnection().sendCommand("fill 480 64 45 544 79 58 air replace");
            client.getConnection().sendCommand("fill 480 80 45 544 95 58 air replace");
            industrialCorridorCleared = true;
            ticks = 0;
            return;
        }
        if (!rimCaptured) {
            client.player.setYRot(0.0F);
            client.player.setXRot(industrialPatternsOnly ? 0.0F : 18.0F);
            int settleTicks = industrialPatternsOnly ? RIM_SETTLE_TICKS + 120 : RIM_SETTLE_TICKS;
            if (ticks < settleTicks
                    || (!client.levelRenderer.hasRenderedAllSections()
                    && ticks < TIMEOUT_TICKS / 2)) return;
            rimCaptured = true;
            ticks = 0;
            capture(client, String.format(industrialPatternsOnly
                            ? "industrial-clearwall-pattern-%02d-%s" : "rim-%02d-%s",
                    presetIndex + 1, slug(currentWallLabel())));
            if (!industrialPatternsOnly && presetIndex == 0) skyIndex = 0;
            return;
        }
        if (skyIndex >= 0 && skyIndex < SKY_VARIANTS.length) {
            captureSky(client);
            return;
        }
        if (!disconnectRequested && ticks >= 30) {
            disconnectRequested = true;
            presetIndex++;
            ticks = 0;
            client.disconnectFromWorld(Component.literal(
                    "RingWorld appearance comparison next preset"));
        }
    }

    private void captureSky(Minecraft client) {
        SkyVariant variant = SKY_VARIANTS[skyIndex];
        if (!skyPoseRequested) {
            skyPoseRequested = true;
            ticks = 0;
            client.getConnection().sendCommand("ringworld sky "
                    + variant.backdrop().name().toLowerCase(java.util.Locale.ROOT));
            client.getConnection().sendCommand("ringworld sun "
                    + variant.sun().name().toLowerCase(java.util.Locale.ROOT));
            client.getConnection().sendCommand("time set "
                    + (variant.wallHorizon() ? "sunset" : "6000"));
            client.getConnection().sendCommand("weather clear");
            double y = variant.wallHorizon() ? 97.0 : 120.0;
            double z = variant.wallHorizon() ? WIDTH / 2.0 - 1.5 : 0.5;
            float yaw = variant.wallHorizon() ? -90.0F : 90.0F;
            float pitch = variant.wallHorizon() ? 0.0F : -90.0F;
            client.getConnection().sendCommand("tp @s "
                    + (CIRCUMFERENCE * variant.longitudeFraction() + 0.5)
                    + " " + y + " " + z + " " + yaw + " " + pitch);
            RingWorldMod.LOGGER.info("[appearance-comparison] settling {}", variant.name());
            return;
        }
        client.player.setYRot(variant.wallHorizon() ? -90.0F : 90.0F);
        client.player.setXRot(variant.wallHorizon() ? 0.0F : -90.0F);
        if (ticks < SKY_SETTLE_TICKS) return;
        capture(client, variant.name());
        skyIndex++;
        skyPoseRequested = false;
        ticks = 0;
    }

    private void capture(Minecraft client, String name) {
        RingMinecraftClientAccess.grabScreenshot(
                client.gameDirectory, name,
                RingMinecraftClientAccess.mainRenderTarget(client), 1,
                message -> RingWorldMod.LOGGER.info(
                        "[appearance-comparison] screenshot {}: {}", name, message.getString()));
    }

    private int wallVariantCount() {
        return industrialPatternsOnly
                ? RingWallStyle.Pattern.selectableValues().length
                : RingWallStyle.Preset.values().length;
    }

    private RingWallStyle currentWallStyle() {
        if (!industrialPatternsOnly) return RingWallStyle.Preset.values()[presetIndex].style();
        return RingWallStyle.custom(7, RingWallStyle.Palette.INDUSTRIAL,
                RingWallStyle.Pattern.selectableValues()[presetIndex], 10);
    }

    private String currentWallLabel() {
        return industrialPatternsOnly
                ? RingWallStyle.Pattern.selectableValues()[presetIndex].label()
                : RingWallStyle.Preset.values()[presetIndex].label();
    }

    private static String slug(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private void fail(String detail) {
        failed = true;
        RingWorldMod.LOGGER.error("[appearance-comparison] FAIL: {}", detail);
    }

    private record SkyVariant(String name, RingSkyProfile.Backdrop backdrop,
                              RingSkyProfile.LightSource sun,
                              double longitudeFraction,
                              boolean wallHorizon) {
        SkyVariant(String name, RingSkyProfile.Backdrop backdrop,
                   RingSkyProfile.LightSource sun, double longitudeFraction) {
            this(name, backdrop, sun, longitudeFraction, false);
        }
    }
}
