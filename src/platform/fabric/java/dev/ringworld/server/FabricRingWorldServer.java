package dev.ringworld.server;

import dev.ringworld.net.RingWorldNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;

/** Fabric lifecycle adapter for the loader-neutral authoritative server core. */
public final class FabricRingWorldServer {
    private FabricRingWorldServer() { }

    public static void register() {
        RingWorldServer.configurePreLoadRejectionHandler(RingWorldHeadlessPrewarm::recordPreLoadRejection);
        RingWorldHeadlessPrewarm.register();
        FabricTerrainAtlasPlatform.register();
        ServerTickEvents.END_LEVEL_TICK.register(world -> {
            RingWorldServer.tickRingWorld(world);
            if (RingWorldServer.isOverworld(world)) RingTerrainAtlasServer.tick(world);
        });
        ServerTickEvents.END_SERVER_TICK.register(RingWorldProductionLifecycleTest::tick);
        ServerTickEvents.END_SERVER_TICK.register(RingWorldStrongholdTest::tick);
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk, generated) -> {
            if (!RingWorldServer.isOverworld(world)) return;
            RingTerrainAtlasServer.captureLoadedChunk(world, chunk);
            RingWorldServer.onChunkLoaded(world, chunk);
        });
        ServerLevelEvents.LOAD.register((server, world) -> {
            try {
                RingWorldServer.attachWorldGeometry(world);
                if (RingWorldServer.isOverworld(world)) {
                    boolean headless = RingWorldHeadlessPrewarm.suppressesBackgroundAutostart(server);
                    RingTerrainAtlasServer.load(world, !headless);
                    if (headless) RingWorldHeadlessPrewarm.start(world);
                }
            } catch (Throwable failure) {
                if (RingWorldServer.isOverworld(world) && RingWorldHeadlessPrewarm.requested(server)) {
                    RingWorldHeadlessPrewarm.failStartup(server, world, failure);
                    return;
                }
                throw failure;
            }
        });
        ServerLevelEvents.UNLOAD.register((server, world) -> {
            RingTerrainAtlasServer.unload(world);
            RingWorldServer.onLevelUnloaded(world);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (RingWorldHeadlessPrewarm.rejectPlayerJoins(server)) {
                handler.disconnect(Component.literal(
                        "RingWorld headless atlas preparation is active; player joins are disabled."));
                return;
            }
            RingWorldServer.onPlayerJoined(handler.player);
        });
        RingWorldNetworking.registerServer();
    }
}
