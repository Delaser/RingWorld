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
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Disposable native-game study of real exterior building versus finite Atlas coverage. */
public final class RingFloatingStructureCaptureClient {
    private static final RingFloatingStructureCaptureClient INSTANCE = new RingFloatingStructureCaptureClient();
    private int stage, ticks, settle;
    private boolean opening, creating, capturePending;
    private boolean reviewOpening, reviewReady;
    private CompletableFuture<Void> setup;

    public static boolean tickIfEnabled(Minecraft client) {
        if (Boolean.getBoolean("ringworld.floatingStructureReview")) return INSTANCE.review(client);
        return Boolean.getBoolean("ringworld.captureFloatingStructure") && INSTANCE.tick(client);
    }
    /** Open the existing sample once, then hand control back to the player. */
    private boolean review(Minecraft client) {
        if (reviewReady) return false;
        client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
        client.options.pauseOnLostFocus = false;
        client.options.onboardAccessibility = false;
        if (client.level == null || client.player == null) {
            if (!reviewOpening && client.isGameLoadFinished()
                    && RingMinecraftClientAccess.screen(client) instanceof TitleScreen) {
                reviewOpening = true;
                client.createWorldOpenFlows().openWorld("RingWorld Floating Structure Study (1)",
                        () -> { throw new IllegalStateException("Floating study review cancelled"); });
            }
            return true;
        }
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || RingMinecraftClientAccess.screen(client) != null) return true;
        client.options.renderDistance().set(10);
        RingMinecraftClientAccess.setGuiHidden(client, false);
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            Vec3 pose = new Vec3(570, 185, geometry.maxWidthZ() + 94);
            Vec3 target = new Vec3(512, 148, geometry.maxWidthZ() + 29);
            Vec3 direction = geometry.toCameraLocal(target, pose.add(0, player.getEyeHeight(), 0));
            float yaw = (float)Math.toDegrees(Math.atan2(-direction.x, direction.z));
            float pitch = (float)-Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(server.overworld(), pose.x, pose.y, pose.z, Set.<Relative>of(), yaw, pitch, false);
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
            if (!opening && RingMinecraftClientAccess.screen(client) instanceof TitleScreen) {
                opening = true;
                CreateWorldScreen.openFresh(client, () -> opening = false);
            } else if (RingMinecraftClientAccess.screen(client) instanceof CreateWorldScreen screen) {
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
        client.options.vignette().set(false);
        RingMinecraftClientAccess.setGuiHidden(client, true);
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || capturePending || RingMinecraftClientAccess.screen(client) != null) return true;
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
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule minecraft:advance_time false");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
                    server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR);
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
                    var player = server.getPlayerList().getPlayers().getFirst();
                    Vec3 direction = geometry.toCameraLocal(target, pose.add(0, player.getEyeHeight(), 0));
                    float yaw = (float)Math.toDegrees(Math.atan2(-direction.x, direction.z));
                    float pitch = (float)-Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
                    player.teleportTo(server.overworld(), pose.x, pose.y, pose.z, Set.<Relative>of(), yaw, pitch, false);
                    RingWorldMod.LOGGER.info("[floating-study] camera stage={} pose={} yaw={} pitch={}", stage, pose, yaw, pitch);
                });
            }
            if (settle < 160) return true;
            String name = stage == 1 ? "floating-near" : stage == 2 ? "floating-atlas-distance" : "floating-opposite-ring";
            capturePending = true;
            RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, name + ".png", RingMinecraftClientAccess.mainRenderTarget(client), 1,
                    message -> client.execute(() -> { capturePending = false; RingWorldMod.LOGGER.info("[floating-study] screenshot {}", message.getString()); }));
            stage++; settle = 0;
        } else if (++settle > 30) {
            RingWorldMod.LOGGER.info("[floating-study] CAPTURE COMPLETE; exterior structure deliberately absent from finite Atlas");
            client.stop();
        }
        return true;
    }
}
