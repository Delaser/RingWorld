package dev.ringworld.platform.neoforge;

import dev.ringworld.server.HeadlessPrewarmCoordinator;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

import java.util.Objects;

/** Owns the NeoForge headless-prewarm player-admission decision. */
public final class NeoForgeHeadlessPlayerAdmission {
    private static final Component REJECTION_REASON = Component.literal(
            "RingWorld headless atlas preparation is active; player joins are disabled.");

    private NeoForgeHeadlessPlayerAdmission() { }

    /** Rejects at {@code PlayerList.placeNewPlayer} HEAD, before play setup. */
    public static boolean rejectBeforePlayLogin(Connection connection, ServerPlayer player) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(player, "player");
        return rejectIfActive(
                HeadlessPrewarmCoordinator.rejectPlayerJoins(player.level().getServer()),
                () -> {
                    if (connection.getPacketListener() instanceof ServerCommonPacketListenerImpl listener) {
                        listener.disconnect(REJECTION_REASON);
                    } else {
                        connection.disconnect(REJECTION_REASON);
                    }
                });
    }

    /** Defensive fallback for an alternate path that reaches the login event. */
    public static boolean rejectIfActive(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return rejectIfActive(
                HeadlessPrewarmCoordinator.rejectPlayerJoins(player.level().getServer()),
                () -> player.connection.disconnect(REJECTION_REASON));
    }

    /** Pure decision seam used by the dual-loader unit suite. */
    static boolean rejectIfActive(boolean active, Runnable disconnect) {
        Objects.requireNonNull(disconnect, "disconnect");
        if (!active) return false;
        disconnect.run();
        return true;
    }
}
