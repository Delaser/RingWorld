package dev.ringworld.server;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Fabric command and payload transport adapter for the shared atlas coordinator. */
public final class FabricTerrainAtlasPlatform {
    private FabricTerrainAtlasPlatform() { }

    public static void register() {
        RingTerrainAtlasServer.configureTransport(new RingTerrainAtlasServer.PayloadTransport() {
            @Override
            public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
                return ServerPlayNetworking.canSend(player, type);
            }

            @Override
            public void send(ServerPlayer player, CustomPacketPayload payload) {
                ServerPlayNetworking.send(player, payload);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                RingTerrainAtlasServer.registerCommands(dispatcher));
    }
}
