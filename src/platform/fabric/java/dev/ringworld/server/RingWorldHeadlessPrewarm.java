package dev.ringworld.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Fabric lifecycle bridge for the loader-neutral headless prewarm coordinator. */
final class RingWorldHeadlessPrewarm {
    private RingWorldHeadlessPrewarm() { }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(HeadlessPrewarmCoordinator::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(HeadlessPrewarmCoordinator::serverStopping);
    }

    static boolean requested(MinecraftServer server) { return HeadlessPrewarmCoordinator.requested(server); }

    static boolean suppressesBackgroundAutostart(MinecraftServer server) {
        return HeadlessPrewarmCoordinator.suppressesBackgroundAutostart(server);
    }

    static boolean rejectPlayerJoins(MinecraftServer server) {
        return HeadlessPrewarmCoordinator.rejectPlayerJoins(server);
    }

    static void recordPreLoadRejection(MinecraftServer server, Throwable failure) {
        HeadlessPrewarmCoordinator.recordPreLoadRejection(server, failure);
    }

    static void start(ServerLevel world) { HeadlessPrewarmCoordinator.start(world); }

    static void failStartup(MinecraftServer server, ServerLevel world, Throwable failure) {
        HeadlessPrewarmCoordinator.failStartup(server, world, failure);
    }
}
