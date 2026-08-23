package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Server-thread bridge for disposable integrated-client capture fixtures.
 *
 * <p>Copied production worlds can legitimately be survival worlds with
 * commands disabled. Visual acceptance must therefore mutate only its copied
 * integrated-server fixture instead of pretending the test player is an
 * operator and sending chat commands.</p>
 */
final class RingIntegratedCaptureControl {
    private RingIntegratedCaptureControl() { }

    static void execute(Minecraft client, String operation,
                        Consumer<Context> action,
                        Runnable completed,
                        Consumer<String> failed) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            failed.accept(operation + " requires an integrated server and player");
            return;
        }
        UUID playerId = client.player.getUUID();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    throw new IllegalStateException("integrated player is unavailable");
                }
                action.accept(new Context(server, server.overworld(), player));
                completed.run();
            } catch (RuntimeException exception) {
                String detail = operation + " failed: " + exception.getMessage();
                RingWorldMod.LOGGER.error("[capture-control] {}", detail, exception);
                failed.accept(detail);
            }
        });
    }

    static void normalizeEnvironment(Context context, int timeTicks, boolean raining) {
        var source = context.server().createCommandSourceStack()
                .withPermission(2)
                .withSuppressedOutput();
        context.server().getCommands().performPrefixedCommand(
                source, "time set " + timeTicks);
        context.world().getGameRules().getRule(GameRules.RULE_DAYLIGHT)
                .set(false, context.server());
        context.server().getCommands().performPrefixedCommand(
                source, raining ? "weather rain" : "weather clear");
        context.player().setGameMode(GameType.SPECTATOR);
    }

    static void teleport(Context context, double x, double y, double z) {
        context.player().teleportTo(context.world(), x, y, z,
                Set.<RelativeMovement>of(), context.player().getYRot(),
                context.player().getXRot());
    }

    record Context(MinecraftServer server, ServerLevel world, ServerPlayer player) { }
}
