package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import dev.ringworld.server.RingWorldMultiplayerTest;
import dev.ringworld.server.RingTerrainAtlasServer;
import dev.ringworld.world.RingWorldSettings;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;

/** Performs the mandatory client-mod handshake and ships immutable settings. */
public final class RingWorldNetworking {
    private RingWorldNetworking() { }

    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(RingSettingsPayload.ID, RingSettingsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RingSettingsAckPayload.ID, RingSettingsAckPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RingMultiplayerTestPayload.ID, RingMultiplayerTestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingTerrainAtlasMetadataPayload.ID, RingTerrainAtlasMetadataPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingTerrainAtlasTilePayload.ID, RingTerrainAtlasTilePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RingTerrainAtlasRequestPayload.ID, RingTerrainAtlasRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RingAtlasPregenerationStatusRequestPayload.ID,
                RingAtlasPregenerationStatusRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RingAtlasPregenerationControlPayload.ID,
                RingAtlasPregenerationControlPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RingAtlasPregenerationStatusPayload.ID,
                RingAtlasPregenerationStatusPayload.CODEC);
    }

    public static void registerServer() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendSettings(handler));
        ServerPlayNetworking.registerGlobalReceiver(RingSettingsAckPayload.ID, (payload, context) ->
                context.server().execute(() -> validateAcknowledgement(payload, context.player().connection)));
        ServerPlayNetworking.registerGlobalReceiver(RingMultiplayerTestPayload.ID, (payload, context) -> {
            if (!Boolean.getBoolean("ringworld.multiplayerTest")) return;
            context.server().execute(() -> {
                RingWorldMultiplayerTest.recordClientResult(payload.role(), payload.phase(), payload.passed());
                RingWorldMod.LOGGER.info(
                        "[multiplayer:{}] client phase={} passed={} value={} player={}",
                        payload.role(), payload.phase(), payload.passed(), payload.value(),
                        context.player().getName().getString());
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(RingTerrainAtlasRequestPayload.ID, (payload, context) ->
                context.server().execute(() -> RingTerrainAtlasServer.requestTiles(
                        context.player(), payload.worldHash(), payload.cacheComplete())));
        ServerPlayNetworking.registerGlobalReceiver(RingAtlasPregenerationStatusRequestPayload.ID, (payload, context) ->
                context.server().execute(() -> RingTerrainAtlasServer.requestPregenerationStatus(
                        context.player(), payload.worldHash())));
        ServerPlayNetworking.registerGlobalReceiver(RingAtlasPregenerationControlPayload.ID, (payload, context) ->
                context.server().execute(() -> RingTerrainAtlasServer.controlPregeneration(
                        context.player(), payload.worldHash(), payload.action())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                RingTerrainAtlasServer.clearPlayer(handler.player));
    }

    private static void sendSettings(ServerGamePacketListenerImpl handler) {
        ServerLevel overworld = handler.player.level().getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        if (!ServerPlayNetworking.canSend(handler.player, RingSettingsPayload.ID)) {
            handler.disconnect(Component.literal(
                    "RingWorld client is missing or out of date. Install a matching RingWorld client version."));
            return;
        }
        RingWorldSettings settings = RingWorldSettings.get(overworld);
        ServerPlayNetworking.send(handler.player, new RingSettingsPayload(
                settings.widthBlocks(), settings.circumferenceBlocks(), settings.generatorSeed(),
                settings.wallHeightBlocks(), settings.surfaceReferenceY(),
                settings.formatVersion(), settings.layoutFingerprint()));
    }

    private static void validateAcknowledgement(RingSettingsAckPayload payload,
                                                ServerGamePacketListenerImpl handler) {
        ServerLevel overworld = handler.player.level().getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        RingWorldSettings settings = RingWorldSettings.get(overworld);
        if (payload.formatVersion() != settings.formatVersion()
                || payload.fingerprint() != settings.layoutFingerprint()) {
            handler.disconnect(Component.literal("RingWorld geometry/protocol acknowledgement mismatch."));
            return;
        }
        RingWorldMod.LOGGER.info("RingWorld settings acknowledged by {}: {}x{}, format {}",
                handler.player.getName().getString(), settings.circumferenceBlocks(),
                settings.widthBlocks(), payload.formatVersion());
        RingTerrainAtlasServer.sendMetadata(handler.player);
    }
}
