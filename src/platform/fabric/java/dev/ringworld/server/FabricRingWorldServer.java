package dev.ringworld.server;

import dev.ringworld.net.RingWorldNetworking;
import dev.ringworld.platform.fabric.FabricHeadlessPlayerAdmission;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** Fabric lifecycle adapter for the loader-neutral authoritative server core. */
public final class FabricRingWorldServer {
    private FabricRingWorldServer() { }

    public static void register() {
        RingWorldServer.configurePreLoadRejectionHandler(RingWorldHeadlessPrewarm::recordPreLoadRejection);
        RingWorldHeadlessPrewarm.register();
        FabricTerrainAtlasPlatform.register();
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            RingWorldServer.tickRingWorld(world);
            if (RingWorldServer.isOverworld(world)) RingTerrainAtlasServer.tick(world);
        });
        ServerTickEvents.END_SERVER_TICK.register(RingWorldProductionLifecycleTest::tick);
        ServerTickEvents.END_SERVER_TICK.register(RingWorldStrongholdTest::tick);
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (!RingWorldServer.isOverworld(world)) return;
            RingTerrainAtlasServer.captureLoadedChunk(world, chunk);
            RingWorldServer.onChunkLoaded(world, chunk);
        });
        ServerWorldEvents.LOAD.register((server, world) -> {
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
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            RingTerrainAtlasServer.unload(world);
            RingWorldServer.onLevelUnloaded(world);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // PlayerList owns the normal pre-login admission boundary. Keep
            // this event check as a defensive fallback for alternate paths.
            if (FabricHeadlessPlayerAdmission.rejectIfActive(handler.player)) return;
            RingWorldServer.onPlayerJoined(handler.player);
        });
        RingWorldNetworking.registerServer();
    }
}
