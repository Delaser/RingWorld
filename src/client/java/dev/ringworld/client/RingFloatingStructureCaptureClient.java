package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.world.RingGeometry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.levelgen.Heightmap;

/** Disposable native-game study of real exterior building versus finite Atlas coverage. */
public final class RingFloatingStructureCaptureClient {
    private static final RingFloatingStructureCaptureClient INSTANCE = new RingFloatingStructureCaptureClient();
    private int stage, ticks, settle;
    private boolean opening, creating, capturePending;
    private boolean reviewOpening, reviewReady;
    private CompletableFuture<Void> setup;
    private double cloudCaptureX = Double.NaN;
    private boolean cloudCameraProofLogged;

    public static boolean tickIfEnabled(Minecraft client) {
        if (Boolean.getBoolean("ringworld.captureAtlasWalls")) return INSTANCE.captureWalls(client);
        if (Boolean.getBoolean("ringworld.floatingStructureReview")) return INSTANCE.review(client);
        return Boolean.getBoolean("ringworld.captureFloatingStructure") && INSTANCE.tick(client);
    }
    /** Opt-in regression capture; the runner supplies a disposable copy of the saved study. */
    private boolean captureWalls(Minecraft client) {
        client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
        client.options.pauseOnLostFocus = false;
        client.options.onboardAccessibility = false;
        client.options.enableVsync().set(false);
        client.options.framerateLimit().set(60);
        if (++ticks > 9000) throw new IllegalStateException("Atlas wall capture timed out at " + stage
                + " screen=" + client.screen);
        if (client.level == null || client.player == null) {
            if (!opening && client.isGameLoadFinished()
                    && client.screen instanceof TitleScreen) {
                opening = true;
                client.createWorldOpenFlows().openWorld("Atlas Wall Review",
                        () -> { throw new IllegalStateException("Atlas wall review cancelled"); });
            }
            return true;
        }
        var geometry = ClientRingState.geometry();
        var atlas = ClientRingState.terrainAtlas();
        if (geometry == null || atlas == null || !atlas.isComplete()
                || capturePending || client.screen != null) return true;
        client.options.hideGui = true;
        boolean clouds = Boolean.getBoolean("ringworld.captureCloudAltitude");
        client.options.cloudStatus().set(clouds ? (stage < 3
                ? net.minecraft.client.CloudStatus.FANCY : net.minecraft.client.CloudStatus.FAST)
                : net.minecraft.client.CloudStatus.OFF);
        boolean altitude = Boolean.getBoolean("ringworld.captureAtlasAltitude");
        if (stage >= (altitude || clouds ? 6 : 3)) {
            RingWorldMod.LOGGER.info("[atlas-wall-review] CAPTURE COMPLETE: {}",
                    clouds ? "cloud altitude: fancy/fast below/inside/above dynamic deck"
                            : altitude ? "altitude handoff: three qualities, lower/ground/above-build controls"
                            : "low/medium/high, closed edges, full-depth walls");
            client.stop();
            return true;
        }
        if (settle == 0) {
            cloudCameraProofLogged = false;
            client.options.renderDistance().set(10);
            RingClientLodTuning.select(dev.ringworld.world.RingLodQuality.values()[!clouds && stage < 3 ? stage : 1]);
            if (altitude || clouds) {
                setup = new CompletableFuture<>();
                var server = client.getSingleplayerServer();
                int cloudBase = dev.ringworld.world.RingCloudBounds.baseHeight(
                        client.level.getMinBuildHeight(), ClientRingState.wallHeightBlocks());
                double y = clouds ? cloudBase + (stage % 3 == 0 ? -16 : stage % 3 == 1 ? 2 : 16)
                        - client.player.getEyeHeight()
                        : stage < 3 ? 234.67834 : stage == 3 ? 185 : stage == 4 ? 130 : 340;
                if (clouds) RingWorldMod.LOGGER.info("[cloud-altitude-review] wallHeight={} base={} eyeY={} mode={}",
                        ClientRingState.wallHeightBlocks(), cloudBase, y + client.player.getEyeHeight(),
                        client.options.cloudStatus().get());
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayers().get(0);
                        var world = server.overworld();
                        if (clouds) {
                            var source = server.createCommandSourceStack().withSuppressedOutput();
                            for (String command : new String[]{"time set 6000", "weather clear",
                                    "gamerule doDaylightCycle false", "gamerule doWeatherCycle false"}) {
                                server.getCommands().performPrefixedCommand(source, command);
                            }
                            double lowestFeet = cloudBase - 16.0 - player.getEyeHeight();
                            var serverAtlas = dev.ringworld.server.RingAtlasPregenerationService.atlas(world);
                            if (!Double.isFinite(cloudCaptureX)) {
                                for (int candidate = 0; candidate < Math.min(128, geometry.circumferenceBlocks() / 16); candidate++) {
                                    int x = geometry.wrapBlockX(geometry.circumferenceBlocks() / 4 + candidate * 16);
                                    if (serverAtlas.sample(x + 0.5, 0.5).height() >= lowestFeet - 4) continue;
                                    if (world.getHeight(Heightmap.Types.WORLD_SURFACE, x, 0) >= lowestFeet - 2) continue;
                                    cloudCaptureX = x + 0.5;
                                    break;
                                }
                                if (!Double.isFinite(cloudCaptureX)) throw new IllegalStateException("No clear native cloud-capture column below deck");
                            }
                            for (double eyeOffset : new double[]{-16, 2, 16}) {
                                double feet = cloudBase + eyeOffset - player.getEyeHeight();
                                AABB bounds = new AABB(cloudCaptureX - 0.31, feet, 0.19,
                                        cloudCaptureX + 0.31, feet + player.getBbHeight(), 0.81);
                                if (!world.noCollision(player, bounds)
                                        || !world.getBlockState(BlockPos.containing(cloudCaptureX, cloudBase + eyeOffset, 0.5)).isAir()) {
                                    throw new IllegalStateException("Cloud camera column intersects native terrain");
                                }
                            }
                        }
                        player.setGameMode(GameType.CREATIVE);
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        player.teleportTo(world, clouds ? cloudCaptureX : 1192.649, y, clouds ? 0.5 : 0.181,
                                Set.<RelativeMovement>of(), -91.4F, clouds ? -12F : 78.3F);
                        setup.complete(null);
                    } catch (Throwable failure) { setup.completeExceptionally(failure); }
                });
            }
            settle = 1;
            return true;
        }
        if (altitude || clouds) {
            if (!setup.isDone()) return true;
            setup.join();
        }
        settle++;
        if (settle < 160 || !dev.ringworld.client.render.RingSurfaceTextureRenderer.displayReady()) return true;
        if (clouds) {
            int base = dev.ringworld.world.RingCloudBounds.baseHeight(client.level.getMinBuildHeight(), ClientRingState.wallHeightBlocks());
            double expectedEyeY = base + (stage % 3 == 0 ? -16 : stage % 3 == 1 ? 2 : 16);
            Vec3 eye = client.gameRenderer.getMainCamera().getPosition();
            BlockPos eyeBlock = BlockPos.containing(eye);
            if (!client.level.hasChunkAt(eyeBlock)) return true;
            if (!client.level.getBlockState(eyeBlock).isAir() || !client.level.noCollision(client.player)
                    || Math.abs(eye.x - cloudCaptureX) > 0.125 || Math.abs(eye.y - expectedEyeY) > 0.125
                    || Math.abs(eye.z - 0.5) > 0.125 || !client.player.getAbilities().flying) {
                throw new IllegalStateException("Native cloud capture camera is obstructed, displaced, or not flying");
            }
            if (!cloudCameraProofLogged) {
                cloudCameraProofLogged = true;
                RingWorldMod.LOGGER.info("[cloud-camera] stage={} mode={} base={} x={} eyeY={} z={} expectedEyeY={} serverVolumeClear=true clientEyeAir=true clientVolumeClear=true flying=true",
                        stage, client.options.cloudStatus().get(), base, eye.x, eye.y, eye.z, expectedEyeY);
            }
        }
        capturePending = true;
        String name = clouds ? "cloud-altitude-" + (stage < 3 ? "fancy-" : "fast-")
                + (stage % 3 == 0 ? "below" : stage % 3 == 1 ? "inside" : "above")
                : altitude ? "atlas-altitude-" + (stage < 3
                ? "high-" + RingClientLodTuning.quality().command()
                : stage == 3 ? "lower-medium" : stage == 4 ? "ground-medium" : "above-build-medium")
                : "atlas-walls-" + RingClientLodTuning.quality().command();
        dev.ringworld.client.compat.Screenshot.grab(client.gameDirectory, name + ".png",
                client.getMainRenderTarget(), 1,
                message -> client.execute(() -> {
                    capturePending = false;
                    RingWorldMod.LOGGER.info("[atlas-wall-review] screenshot {}", message.getString());
                }));
        stage++; settle = 0;
        return true;
    }

    /** Open the existing sample once, then hand control back to the player. */
    private boolean review(Minecraft client) {
        if (reviewReady) return false;
        client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
        client.options.pauseOnLostFocus = false;
        client.options.onboardAccessibility = false;
        if (client.level == null || client.player == null) {
            if (!reviewOpening && client.isGameLoadFinished()
                    && client.screen instanceof TitleScreen) {
                reviewOpening = true;
                client.createWorldOpenFlows().openWorld("RingWorld Floating Structure Study (1)",
                        () -> { throw new IllegalStateException("Floating study review cancelled"); });
            }
            return true;
        }
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || client.screen != null) return true;
        client.options.renderDistance().set(10);
        client.options.hideGui = false;
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().get(0);
            boolean keepPose = Boolean.getBoolean("ringworld.keepReviewPose");
            Vec3 pose = keepPose ? player.position() : new Vec3(570, 185, geometry.maxWidthZ() + 94);
            Vec3 target = new Vec3(512, 148, geometry.maxWidthZ() + 29);
            Vec3 direction = geometry.toCameraLocal(target, pose.add(0, player.getEyeHeight(), 0));
            float yaw = keepPose ? player.getYRot() : (float)Math.toDegrees(Math.atan2(-direction.x, direction.z));
            float pitch = keepPose ? player.getXRot()
                    : (float)-Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(server.overworld(), pose.x, pose.y, pose.z, Set.<RelativeMovement>of(), yaw, pitch);
            RingWorldMod.LOGGER.info("[floating-study] REVIEW READY: normal walls, near viewpoint, player control, muted, staying open");
        });
        reviewReady = true;
        return true;
    }
    private boolean tick(Minecraft client) {
        client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
        client.options.pauseOnLostFocus = false;
        client.options.onboardAccessibility = false;
        client.options.enableVsync().set(false);
        client.options.framerateLimit().set(60);
        if (++ticks > 9000) throw new IllegalStateException("Floating structure capture timed out at " + stage);
        if (client.level == null || client.player == null) {
            if (!client.isGameLoadFinished() || creating) return true;
            if (!opening && client.screen instanceof TitleScreen) {
                opening = true;
                CreateWorldScreen.openFresh(client, client.screen);
            } else if (client.screen instanceof CreateWorldScreen screen) {
                screen.getUiState().setName("RingWorld Floating Structure Study");
                screen.getUiState().setSeed("255");
                screen.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                screen.getUiState().setAllowCommands(true);
                creating = true;
                ((CreateWorldScreenInvoker) screen).ringworld$createLevel();
            }
            return true;
        }
        client.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
        
        client.options.hideGui = true;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || capturePending || client.screen != null) return true;
        if (stage == 0) {
            var server = client.getSingleplayerServer();
            setup = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    var world = server.overworld();
                    int z0 = geometry.maxWidthZ() + 13;
                    int count = 0;
                    for (int x = 480; x < 544; x++) for (int y = 132; y < 164; y++) for (int z = z0; z < z0 + 32; z++) {
                        var block = y == 132 || y == 163 ? Blocks.POLISHED_DEEPSLATE
                                : (x == 480 || x == 543 || z == z0 || z == z0 + 31)
                                    ? (x % 16 == 0 || x == 543 || z == z0 || z == z0 + 31) && (y % 8 == 4 || x % 16 == 0 || x == 543)
                                        ? Blocks.IRON_BLOCK : Blocks.GLASS
                                    : null;
                        if (block != null) { world.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 3); count++; }
                    }
                    // Bright corner accents make the silhouette easy to recognize.
                    for (int x : new int[]{480, 496, 512, 528, 543}) for (int z : new int[]{z0, z0 + 31})
                        for (int y : new int[]{133, 140, 148, 156, 162})
                            world.setBlock(new BlockPos(x, y, z), Blocks.SEA_LANTERN.defaultBlockState(), 3);
                    RingWorldMod.LOGGER.info("[floating-study] built {} blocks: 64x32x32, 12-block gap, z={}", count, z0);
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule doDaylightCycle false");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
                    server.getPlayerList().getPlayers().get(0).setGameMode(GameType.SPECTATOR);
                    setup.complete(null);
                } catch (Throwable failure) { setup.completeExceptionally(failure); }
            });
            stage++; return true;
        }
        if (!setup.isDone()) return true;
        setup.join();
        if (ClientRingState.terrainAtlas() == null || !ClientRingState.terrainAtlas().isComplete()) {
            if (ticks % 200 == 0) RingWorldMod.LOGGER.info("[floating-study] waiting for complete Atlas");
            return true;
        }
        if (stage <= 3) {
            Vec3 pose = stage == 1 ? new Vec3(570, 185, geometry.maxWidthZ() + 94)
                    : stage == 2 ? new Vec3(900, 120, geometry.maxWidthZ() + 94)
                    : new Vec3(1536, 110, geometry.maxWidthZ() + 94);
            Vec3 target = new Vec3(512, 148, geometry.maxWidthZ() + 29);
            if (settle++ == 0) {
                client.options.renderDistance().set(stage == 1 ? 10 : 6);
                var server = client.getSingleplayerServer();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayers().get(0);
                    Vec3 direction = geometry.toCameraLocal(target, pose.add(0, player.getEyeHeight(), 0));
                    float yaw = (float)Math.toDegrees(Math.atan2(-direction.x, direction.z));
                    float pitch = (float)-Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
                    player.teleportTo(server.overworld(), pose.x, pose.y, pose.z, Set.<RelativeMovement>of(), yaw, pitch);
                    RingWorldMod.LOGGER.info("[floating-study] camera stage={} pose={} yaw={} pitch={}", stage, pose, yaw, pitch);
                });
            }
            if (settle < 160) return true;
            String name = stage == 1 ? "floating-near" : stage == 2 ? "floating-atlas-distance" : "floating-opposite-ring";
            capturePending = true;
            dev.ringworld.client.compat.Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1,
                    message -> client.execute(() -> { capturePending = false; RingWorldMod.LOGGER.info("[floating-study] screenshot {}", message.getString()); }));
            stage++; settle = 0;
        } else if (++settle > 30) {
            RingWorldMod.LOGGER.info("[floating-study] CAPTURE COMPLETE; exterior structure deliberately absent from finite Atlas");
            client.stop();
        }
        return true;
    }
}
