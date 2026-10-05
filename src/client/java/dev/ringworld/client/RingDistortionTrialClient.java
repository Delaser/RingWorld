package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingWorldGenerationSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Disposable minimum-ring trial. Only active with -Dringworld.distortionTrial=true. */
public final class RingDistortionTrialClient {
    private static final String NAME = "RingWorld Nearby Distortion Trial 2048";
    private static int phase, settled, platformX, floatingChunk;
    private static final java.util.List<dev.ringworld.world.RingFloatingSurface> floatingLayers = new java.util.ArrayList<>();
    private static boolean opened, requested;
    private static volatile boolean positioned, captured;
    private static volatile String failure;
    private RingDistortionTrialClient() { }

    public static boolean tick(Minecraft client) {
        if (!Boolean.getBoolean("ringworld.distortionTrial") || phase == 4) return false;
        if (failure != null) {
            RingWorldMod.LOGGER.error("[distortion-trial] {}", failure);
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
        if (!Boolean.getBoolean("ringworld.distortionTrialResume")) {
            client.options.fov().set(70);
            client.options.renderDistance().set(12);
        }
        if (phase == 0) {
            if (!opened) {
                if (!(RingMinecraftClientAccess.screen(client) instanceof TitleScreen)) return true;
                var config = RingWorldConfig.load();
                RingWorldConfig.saveBootstrapLayout(128, 2048, 160, config.wallStyle(),
                        config.skyProfile(), RingWorldGenerationSettings.DEFAULT, false);
                opened = true;
                if (new java.io.File(client.gameDirectory, "saves/" + NAME + "/level.dat").isFile()) {
                    client.createWorldOpenFlows().openWorld(NAME, () -> failure = "world open cancelled");
                    phase = 1;
                } else CreateWorldScreen.openFresh(client, () -> opened = false);
            } else if (RingMinecraftClientAccess.screen(client) instanceof CreateWorldScreen screen) {
                var state = screen.getUiState();
                state.setName(NAME);
                state.setSeed("67890");
                state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                state.setAllowCommands(true);
                ((CreateWorldScreenInvoker) screen).ringworld$createLevel();
                phase = 1;
            }
            return true;
        }
        if (phase == 7) {
            if (requested) {
                if (!positioned) return true;
                floatingChunk++;
                if (floatingChunk % 128 == 0) RingWorldMod.LOGGER.info("[floating-atlas-trial] scanned {} chunks", floatingChunk);
                requested = false; positioned = false;
            }
            var geometry = ClientRingState.geometry();
            if (floatingChunk >= geometry.circumferenceChunks()*geometry.widthChunks()) {
                RingFloatingAtlasTrial.publish(ClientRingState.terrainAtlas().worldHash(), floatingLayers);
                dev.ringworld.client.render.RingSurfaceTextureRenderer.clear();
                RingWorldMod.LOGGER.info("[floating-atlas-trial] captured {} detached columns; rebuilding layered surface", floatingLayers.size());
                phase = 5;
                return true;
            }
            int index = floatingChunk;
            requested = true;
            RingIntegratedCaptureControl.execute(client, "floating Atlas capture", context ->
                    RingFloatingAtlasTrial.captureChunk(context, index/geometry.widthChunks(),
                            geometry.minChunkZ()+index%geometry.widthChunks(), floatingLayers),
                    () -> positioned = true, message -> failure = message);
            return true;
        }
        if (phase == 6) {
            if (requested) {
                if (!positioned) return true;
                platformX += 16;
                positioned = false;
                requested = false;
            }
            if (platformX >= 2048) {
                RingWorldMod.LOGGER.info("[distortion-trial] platform extension complete and verified: full 2048-block loop at Y319, Z=-4..4; original section preserved");
                phase = Boolean.getBoolean("ringworld.floatingAtlasTrial") ? 7 : 5;
                return true;
            }
            int start = platformX;
            requested = true;
            RingIntegratedCaptureControl.execute(client, "extend distortion platform", context -> {
                int top = context.world().getMinY() + context.world().getHeight() - 1;
                for (int x = start; x < start + 16; x++) for (int z = -4; z <= 4; z++) {
                    // Keep the owner's existing platform and edits intact.
                    if (x >= 432 && x <= 592) continue;
                    var material = x % 16 == 0 ? Blocks.GOLD_BLOCK : Blocks.SMOOTH_QUARTZ;
                    if (z == -4 || z == 4) material = Blocks.SEA_LANTERN;
                    var pos = new BlockPos(x, top, z);
                    context.world().setBlock(pos, material.defaultBlockState(), 3);
                    if (!context.world().getBlockState(pos).is(material)) {
                        throw new IllegalStateException("platform placement failed at " + pos);
                    }
                }
            }, () -> positioned = true, message -> failure = message);
            return true;
        }
        if (phase == 5) {
            var atlas = ClientRingState.terrainAtlas();
            if (atlas == null || !atlas.isComplete()
                    || !dev.ringworld.client.render.RingSurfaceTextureRenderer.displayReady()) return true;
            if (!client.levelRenderer.hasRenderedAllSections() || ++settled < 60) return true;
            RingMinecraftClientAccess.setGuiHidden(client, true);
            RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "distortion-fixed-height.png",
                    RingMinecraftClientAccess.mainRenderTarget(client), 1,
                    message -> client.execute(() -> RingMinecraftClientAccess.setGuiHidden(client, false)));
            RingWorldMod.LOGGER.info("[distortion-trial] captured fixed-height correction at saved viewpoint");
            phase = 4;
            return false;
        }
        if (phase == 1) {
            if (client.player == null || ClientRingState.geometry() == null) return true;
            if (ClientRingState.geometry().circumferenceBlocks() != 2048) {
                failure = "expected the smallest ring (2048 blocks)";
                return true;
            }
            if (Boolean.getBoolean("ringworld.distortionTrialResume")) {
                RingDistortionTuning.select(true);
                RingMinecraftClientAccess.setScreen(client, null);
                RingWorldMod.LOGGER.info("[distortion-trial] resumed saved viewpoint {},{},{} yaw={} pitch={}",
                        client.player.getX(), client.player.getY(), client.player.getZ(),
                        client.player.getYRot(), client.player.getXRot());
                phase = Boolean.getBoolean("ringworld.distortionTrialExtendPlatform") ? 6
                        : Boolean.getBoolean("ringworld.floatingAtlasTrial") ? 7 : 5;
                return true;
            }
            RingIntegratedCaptureControl.execute(client, "distortion platform", context -> {
                RingIntegratedCaptureControl.normalizeEnvironment(context, 6000, false);
                context.player().setGameMode(GameType.CREATIVE);
                int top = context.world().getMinY() + context.world().getHeight() - 1;
                for (int x = 432; x <= 592; x++) for (int z = -4; z <= 4; z++) {
                    var material = x % 16 == 0 ? Blocks.GOLD_BLOCK : Blocks.SMOOTH_QUARTZ;
                    if (z == -4 || z == 4) material = Blocks.SEA_LANTERN;
                    context.world().setBlock(new BlockPos(x, top, z), material.defaultBlockState(), 3);
                }
                // Expose 1-block steps below the legal build ceiling.
                for (int x = 464; x < 512; x += 4) for (int z = 6; z <= 9; z++) {
                    int y = top - (512 - x) / 4;
                    for (int h = y - 3; h <= y; h++) context.world().setBlock(
                            new BlockPos(x, h, z), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                }
                context.player().setYRot(90);
                context.player().setXRot(28);
                RingIntegratedCaptureControl.teleport(context, 512.5, top + 1.0, 0.5);
            }, () -> positioned = true, message -> failure = message);
            phase = 2;
            return true;
        }
        if (!positioned) return true;
        RingMinecraftClientAccess.setScreen(client, null);
        client.player.setYRot(90);
        client.player.setXRot(28);
        RingMinecraftClientAccess.setGuiHidden(client, true);
        if (!requested) {
            if (!client.levelRenderer.hasRenderedAllSections()) { settled = 0; return true; }
            if (++settled < 60) return true;
            requested = true;
            RingMinecraftClientAccess.grabScreenshot(client.gameDirectory,
                    phase == 2 ? "distortion-off.png" : "distortion-on.png",
                    RingMinecraftClientAccess.mainRenderTarget(client), 1, message -> captured = true);
        }
        if (captured) {
            if (phase == 2) {
                client.getConnection().sendCommand("ringworld distortion on");
                if (!RingDistortionTuning.enabled()) failure = "client distortion command failed";
                phase = 3; settled = 0; requested = false; captured = false;
            } else {
                RingMinecraftClientAccess.setGuiHidden(client, false);
                client.getConnection().sendCommand("ringworld distortion show");
                RingWorldMod.LOGGER.info("[distortion-trial] PASS: on/off captures; leaving minimum ring at max build height open");
                phase = 4;
            }
        }
        return true;
    }
}
