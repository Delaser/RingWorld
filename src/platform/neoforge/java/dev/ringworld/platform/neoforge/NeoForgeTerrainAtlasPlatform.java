package dev.ringworld.platform.neoforge;

import dev.ringworld.server.RingTerrainAtlasServer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** NeoForge payload transport adapter for the shared atlas coordinator. */
public final class NeoForgeTerrainAtlasPlatform {
    private NeoForgeTerrainAtlasPlatform() { }

    public static void configure() {
        RingTerrainAtlasServer.configureTransport(new RingTerrainAtlasServer.PayloadTransport() {
            @Override
            public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
                // Every RingWorld play payload is registered as non-optional;
                // NeoForge rejects a mismatched channel set during negotiation.
                return true;
            }

            @Override
            public void send(ServerPlayer player, CustomPacketPayload payload) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        });
    }
}
