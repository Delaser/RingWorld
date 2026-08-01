package dev.ringworld.platform.neoforge;

import dev.ringworld.server.RingWorldProductionLifecycleTest;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.server.RingWorldStrongholdTest;
import dev.ringworld.server.RingTerrainAtlasServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** NeoForge lifecycle adapter for the loader-neutral authoritative server core. */
public final class NeoForgeRingWorldServer {
    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel world) {
            RingWorldServer.attachWorldGeometry(world);
            if (RingWorldServer.isOverworld(world)) RingTerrainAtlasServer.load(world);
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel world) {
            RingTerrainAtlasServer.unload(world);
            RingWorldServer.onLevelUnloaded(world);
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel world) {
            if (RingWorldServer.isOverworld(world)) {
                RingTerrainAtlasServer.captureLoadedChunk(world, event.getChunk());
            }
            RingWorldServer.onChunkLoaded(world, event.getChunk());
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel world) {
            RingWorldServer.tickRingWorld(world);
            if (RingWorldServer.isOverworld(world)) RingTerrainAtlasServer.tick(world);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        NeoForgeRingWorldNetworking.expireAcknowledgements(event.getServer());
        RingWorldProductionLifecycleTest.tick(event.getServer());
        RingWorldStrongholdTest.tick(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RingWorldServer.onPlayerJoined(player);
            NeoForgeRingWorldNetworking.sendSettings(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NeoForgeRingWorldNetworking.clear(player);
            RingTerrainAtlasServer.clearPlayer(player);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        RingTerrainAtlasServer.registerCommands(event.getDispatcher());
    }
}
