package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.api.RingWorldApi;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Test-only integrated-server coordinator for the production lifecycle run.
 *
 * <p>The client independently verifies its renderer state before, during, and
 * after this sequence. Keeping the coordinator server-owned ensures the test
 * uses the real 26.1 cross-dimension teleport path rather than a client-side
 * world swap.</p>
 */
public final class RingWorldProductionLifecycleTest {
    private static final int SETTLE_TICKS = 80;
    private static final int STAGE_TIMEOUT_TICKS = 1_200;
    private static final Map<UUID, Progress> PROGRESS = new HashMap<>();
    private static final Set<UUID> CLIENT_READY = new HashSet<>();
    private static volatile boolean transferCycleFinished;
    private static volatile boolean transferCyclePassed;

    private record Progress(int stage, int ticks) { }

    private RingWorldProductionLifecycleTest() { }

    public static boolean enabled() {
        return Boolean.getBoolean("ringworld.productionLifecycleTest");
    }

    /** Called on the integrated server thread once the real client has its complete baseline. */
    public static void markClientReady(UUID playerId) {
        CLIENT_READY.add(playerId);
        RingWorldMod.LOGGER.info("[production-lifecycle] client baseline ready player={}", playerId);
    }

    public static boolean transferCyclePassed() {
        return transferCycleFinished && transferCyclePassed;
    }

    public static boolean transferCycleFailed() {
        return transferCycleFinished && !transferCyclePassed;
    }

    /** Invoked once per server tick, rather than from the Overworld-only seam loop. */
    public static void tick(MinecraftServer server) {
        if (!enabled() || transferCycleFinished) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        RingWorldSettings settings = RingWorldSettings.get(overworld);
        if (settings.widthBlocks() != RingWorldSettings.DEFAULT_WIDTH
                || settings.circumferenceBlocks() != RingWorldSettings.DEFAULT_CIRCUMFERENCE) {
            RingWorldMod.LOGGER.error(
                    "[production-lifecycle] result=false reason=expected-production-layout actual={}x{}",
                    settings.circumferenceBlocks(), settings.widthBlocks());
            finish(false);
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(server, player);
            if (transferCycleFinished) return;
        }
    }

    private static void tickPlayer(MinecraftServer server, ServerPlayer player) {
        Progress progress = PROGRESS.getOrDefault(player.getUUID(), new Progress(0, 0));
        if (progress.ticks() >= STAGE_TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error(
                    "[production-lifecycle] result=false reason=server-stage-timeout stage={} dimension={}",
                    progress.stage(), player.level().dimension().identifier());
            finish(false);
            return;
        }

        switch (progress.stage()) {
            case 0 -> {
                if (player.level().dimension() != Level.OVERWORLD) return;
                if (!CLIENT_READY.contains(player.getUUID())) return;
                if (!validateDimensionState(player, Level.OVERWORLD)) return;
                preparePlayer(player);
                if (progress.ticks() >= SETTLE_TICKS) {
                    teleport(player, requireLevel(server, Level.NETHER), 0.5, 200.0, 0.5,
                            1, "Nether (setup)");
                } else {
                    advance(player, progress);
                }
            }
            case 1 -> awaitDimensionAndTeleport(server, player, progress, Level.NETHER,
                    Level.OVERWORLD, 2, "Overworld");
            case 2 -> awaitDimensionAndTeleport(server, player, progress, Level.OVERWORLD,
                    Level.END, 3, "End");
            case 3 -> awaitDimensionAndTeleport(server, player, progress, Level.END,
                    Level.OVERWORLD, 4, "Overworld");
            case 4 -> {
                if (player.level().dimension() != Level.OVERWORLD) {
                    advance(player, progress);
                    return;
                }
                if (!validateDimensionState(player, Level.OVERWORLD)) return;
                if (progress.ticks() >= SETTLE_TICKS) {
                    finish(true);
                    RingWorldMod.LOGGER.info(
                            "[production-lifecycle] server-transfer result=true sequence=nether,overworld,end,overworld");
                } else {
                    advance(player, progress);
                }
            }
            default -> throw new IllegalStateException("Unknown production lifecycle stage " + progress.stage());
        }
    }

    private static void awaitDimensionAndTeleport(MinecraftServer server, ServerPlayer player,
                                                   Progress progress,
                                                   net.minecraft.resources.ResourceKey<Level> expected,
                                                   net.minecraft.resources.ResourceKey<Level> next,
                                                   int nextStage, String nextName) {
        if (player.level().dimension() != expected) {
            advance(player, progress);
            return;
        }
        if (!validateDimensionState(player, expected)) return;
        if (progress.ticks() >= SETTLE_TICKS) {
            teleport(player, requireLevel(server, next), 0.5, 200.0, 0.5, nextStage, nextName);
        } else {
            advance(player, progress);
        }
    }

    private static ServerLevel requireLevel(MinecraftServer server,
                                            net.minecraft.resources.ResourceKey<Level> key) {
        ServerLevel level = server.getLevel(key);
        if (level == null) throw new IllegalStateException("Missing lifecycle test dimension " + key.identifier());
        return level;
    }

    private static void preparePlayer(ServerPlayer player) {
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void teleport(ServerPlayer player, ServerLevel target,
                                 double x, double y, double z, int nextStage, String targetName) {
        preparePlayer(player);
        player.teleport(new TeleportTransition(target, new Vec3(x, y, z), Vec3.ZERO,
                player.getYRot(), player.getXRot(), Set.<Relative>of(), TeleportTransition.DO_NOTHING));
        PROGRESS.put(player.getUUID(), new Progress(nextStage, 0));
        RingWorldMod.LOGGER.info("[production-lifecycle] server-transfer target={} stage={}",
                targetName, nextStage);
    }

    private static void advance(ServerPlayer player, Progress progress) {
        PROGRESS.put(player.getUUID(), new Progress(progress.stage(), progress.ticks() + 1));
    }

    private static boolean validateDimensionState(
            ServerPlayer player, net.minecraft.resources.ResourceKey<Level> expected) {
        boolean expectedRingWorld = expected == Level.OVERWORLD;
        boolean active = RingWorldApi.isRingWorld(player.level());
        boolean canonical = !expectedRingWorld
                || (active && player.getX() >= 0.0
                && player.getX() < RingWorldApi.geometry(player.level()).circumferenceBlocks());
        if (active == expectedRingWorld && canonical) return true;
        RingWorldMod.LOGGER.error(
                "[production-lifecycle] result=false reason=server-dimension-state dimension={} active={} canonical={}",
                expected.identifier(), active, canonical);
        finish(false);
        return false;
    }

    private static void finish(boolean passed) {
        transferCyclePassed = passed;
        transferCycleFinished = true;
    }
}
