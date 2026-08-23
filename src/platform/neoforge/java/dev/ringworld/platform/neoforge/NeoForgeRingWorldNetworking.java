package dev.ringworld.platform.neoforge;

import dev.ringworld.RingWorldMod;
import dev.ringworld.net.RingAtlasPregenerationControlPayload;
import dev.ringworld.net.RingAtlasPregenerationStatusPayload;
import dev.ringworld.net.RingAtlasPregenerationStatusRequestPayload;
import dev.ringworld.net.RingHandshakeTracker;
import dev.ringworld.net.RingMultiplayerTestPayload;
import dev.ringworld.net.RingSettingsAckPayload;
import dev.ringworld.net.RingSettingsHandshake;
import dev.ringworld.net.RingSettingsPayload;
import dev.ringworld.net.RingTerrainAtlasMetadataPayload;
import dev.ringworld.net.RingTerrainAtlasRequestPayload;
import dev.ringworld.net.RingTerrainAtlasRevisionPayload;
import dev.ringworld.net.RingTerrainAtlasTilePayload;
import dev.ringworld.server.RingWorldMultiplayerTest;
import dev.ringworld.server.RingTerrainAtlasServer;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;
import java.util.function.BiConsumer;

/** NeoForge transport for the shared RingWorld payload records and handshake state. */
public final class NeoForgeRingWorldNetworking {
    private static final String CHANNEL_VERSION = "ringworld-26.1-v2";
    private static final RingHandshakeTracker HANDSHAKES = new RingHandshakeTracker();
    private static volatile BiConsumer<CustomPacketPayload, IPayloadContext> clientPayloadHandler;

    private NeoForgeRingWorldNetworking() { }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(CHANNEL_VERSION);
        registrar.playToClient(RingSettingsPayload.ID, RingSettingsPayload.CODEC,
                NeoForgeRingWorldNetworking::handleClientPayload);
        registrar.playToServer(RingSettingsAckPayload.ID, RingSettingsAckPayload.CODEC,
                NeoForgeRingWorldNetworking::handleAcknowledgement);
        registrar.playToServer(RingMultiplayerTestPayload.ID, RingMultiplayerTestPayload.CODEC,
                NeoForgeRingWorldNetworking::handleMultiplayerTest);
        registrar.playToClient(RingTerrainAtlasMetadataPayload.ID, RingTerrainAtlasMetadataPayload.CODEC,
                NeoForgeRingWorldNetworking::handleClientPayload);
        registrar.playToClient(RingTerrainAtlasTilePayload.ID, RingTerrainAtlasTilePayload.CODEC,
                NeoForgeRingWorldNetworking::handleClientPayload);
        registrar.playToClient(RingTerrainAtlasRevisionPayload.ID, RingTerrainAtlasRevisionPayload.CODEC,
                NeoForgeRingWorldNetworking::handleClientPayload);
        registrar.playToClient(RingAtlasPregenerationStatusPayload.ID, RingAtlasPregenerationStatusPayload.CODEC,
                NeoForgeRingWorldNetworking::handleClientPayload);
        registrar.playToServer(RingTerrainAtlasRequestPayload.ID, RingTerrainAtlasRequestPayload.CODEC,
                NeoForgeRingWorldNetworking::handleAtlasRequest);
        registrar.playToServer(RingAtlasPregenerationStatusRequestPayload.ID,
                RingAtlasPregenerationStatusRequestPayload.CODEC,
                NeoForgeRingWorldNetworking::handleAtlasStatusRequest);
        registrar.playToServer(RingAtlasPregenerationControlPayload.ID,
                RingAtlasPregenerationControlPayload.CODEC,
                NeoForgeRingWorldNetworking::handleAtlasControl);
    }

    public static void configureClientPayloadHandler(
            BiConsumer<CustomPacketPayload, IPayloadContext> handler) {
        clientPayloadHandler = handler;
    }

    private static void handleClientPayload(CustomPacketPayload payload, IPayloadContext context) {
        BiConsumer<CustomPacketPayload, IPayloadContext> handler = clientPayloadHandler;
        if (handler == null) {
            context.disconnect(Component.literal("RingWorld client payload handler is unavailable."));
            return;
        }
        handler.accept(payload, context);
    }

    public static void sendSettings(ServerPlayer player) {
        ServerLevel overworld = player.level().getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            player.connection.disconnect(Component.literal(
                    "RingWorld could not load the authoritative Overworld settings."));
            return;
        }
        HANDSHAKES.begin(player.getUUID(), player.level().getServer().getTickCount());
        PacketDistributor.sendToPlayer(player,
                RingSettingsHandshake.payloadFor(RingWorldSettings.get(overworld)));
    }

    public static void clear(ServerPlayer player) {
        HANDSHAKES.clear(player.getUUID());
    }

    public static void expireAcknowledgements(MinecraftServer server) {
        for (UUID playerId : HANDSHAKES.expire(server.getTickCount())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            RingWorldMod.LOGGER.warn("RingWorld settings acknowledgement timed out for {}",
                    player.getName().getString());
            player.connection.disconnect(Component.literal(
                    "RingWorld handshake timed out. Install the matching client mod and reconnect."));
        }
    }

    private static void handleAcknowledgement(RingSettingsAckPayload payload, IPayloadContext context) {
        // Payload delivery may originate on Netty. Keep the handshake tracker,
        // player connection, and atlas stream on the server thread just as the
        // Fabric receiver does. enqueueWork is immediate when NeoForge already
        // selected its main-thread handler.
        context.enqueueWork(() -> handleAcknowledgementOnServerThread(payload, context));
    }

    private static void handleAcknowledgementOnServerThread(
            RingSettingsAckPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ServerLevel overworld = player.level().getServer().getLevel(Level.OVERWORLD);
        if (overworld == null || !RingSettingsHandshake.accepts(RingWorldSettings.get(overworld), payload)) {
            HANDSHAKES.clear(player.getUUID());
            context.disconnect(Component.literal("RingWorld geometry/protocol acknowledgement mismatch."));
            return;
        }
        RingHandshakeTracker.AcknowledgementResult result = HANDSHAKES.acknowledge(player.getUUID());
        if (result == RingHandshakeTracker.AcknowledgementResult.UNEXPECTED) {
            context.disconnect(Component.literal(
                    "RingWorld settings acknowledgement was unexpected or expired. Reconnect with a matching client."));
        } else if (result == RingHandshakeTracker.AcknowledgementResult.ACCEPTED) {
            RingWorldMod.LOGGER.info("RingWorld settings acknowledged by {} on NeoForge: format {}",
                    player.getName().getString(), payload.formatVersion());
            RingTerrainAtlasServer.sendMetadata(player);
        }
    }

    private static void handleMultiplayerTest(RingMultiplayerTestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleMultiplayerTestOnServerThread(payload, context));
    }

    private static void handleMultiplayerTestOnServerThread(
            RingMultiplayerTestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !Boolean.getBoolean("ringworld.multiplayerTest")
                || !HANDSHAKES.isAcknowledged(player.getUUID())) return;
        RingWorldMultiplayerTest.recordClientResult(payload.role(), payload.phase(), payload.passed());
    }

    private static void handleAtlasRequest(RingTerrainAtlasRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleAtlasRequestOnServerThread(payload, context));
    }

    private static void handleAtlasRequestOnServerThread(
            RingTerrainAtlasRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && requireAcknowledged(player, context)) {
            RingTerrainAtlasServer.requestTiles(
                    player, payload.worldHash(), payload.revision(), payload.cacheComplete());
        }
    }

    private static void handleAtlasStatusRequest(
            RingAtlasPregenerationStatusRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleAtlasStatusRequestOnServerThread(payload, context));
    }

    private static void handleAtlasStatusRequestOnServerThread(
            RingAtlasPregenerationStatusRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && requireAcknowledged(player, context)) {
            RingTerrainAtlasServer.requestPregenerationStatus(player, payload.worldHash());
        }
    }

    private static void handleAtlasControl(
            RingAtlasPregenerationControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleAtlasControlOnServerThread(payload, context));
    }

    private static void handleAtlasControlOnServerThread(
            RingAtlasPregenerationControlPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && requireAcknowledged(player, context)) {
            RingTerrainAtlasServer.controlPregeneration(player, payload.worldHash(), payload.action());
        }
    }

    private static boolean requireAcknowledged(ServerPlayer player, IPayloadContext context) {
        if (HANDSHAKES.isAcknowledged(player.getUUID())) return true;
        context.disconnect(Component.literal(
                "RingWorld handshake is incomplete; reconnect with a matching client."));
        return false;
    }
}
