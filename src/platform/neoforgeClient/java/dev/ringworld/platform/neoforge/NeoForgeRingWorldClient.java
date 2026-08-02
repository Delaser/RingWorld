package dev.ringworld.platform.neoforge;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.AtlasPregenerationClientState;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.LayoutSwitchTestClient;
import dev.ringworld.client.MultiplayerTestClient;
import dev.ringworld.client.ProductionLifecycleTestClient;
import dev.ringworld.client.RingClientPayloadTransport;
import dev.ringworld.client.RingProjectionCaptureClient;
import dev.ringworld.client.RingVisualParityCaptureClient;
import dev.ringworld.client.RingWorldClientSession;
import dev.ringworld.net.RingAtlasPregenerationStatusPayload;
import dev.ringworld.net.RingSettingsHandshake;
import dev.ringworld.net.RingSettingsPayload;
import dev.ringworld.net.RingTerrainAtlasMetadataPayload;
import dev.ringworld.net.RingTerrainAtlasRequestPayload;
import dev.ringworld.net.RingTerrainAtlasRevisionPayload;
import dev.ringworld.net.RingTerrainAtlasTilePayload;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NeoForge client lifecycle, payload handlers, and cache/network adapters. */
@EventBusSubscriber(modid = RingWorldMod.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeRingWorldClient {
    private static final String CURVED_OBJECT_CAPTURE_PROPERTY = "ringworld.curvedObjectCapture";
    private static final ProductionLifecycleTestClient PRODUCTION_LIFECYCLE =
            new ProductionLifecycleTestClient();
    private static final LayoutSwitchTestClient LAYOUT_SWITCH = new LayoutSwitchTestClient();
    private static final MultiplayerTestClient MULTIPLAYER_TEST = new MultiplayerTestClient();
    private static final RingProjectionCaptureClient PROJECTION_CAPTURE =
            new RingProjectionCaptureClient();
    private static final RingVisualParityCaptureClient VISUAL_PARITY_CAPTURE =
            new RingVisualParityCaptureClient();

    private NeoForgeRingWorldClient() { }

    public static void register(IEventBus modEventBus) {
        ClientRingState.configureCacheDirectory(FMLPaths.GAMEDIR.get().resolve("ringworld-cache"));
        RingClientPayloadTransport.configure(new NeoForgePayloadTransport());
        modEventBus.addListener(NeoForgeRingWorldClient::registerPayloadHandlers);
        modEventBus.addListener(NeoForgeRingWorldClient::registerRenderPipelines);
        RingWorldMod.LOGGER.info("RingWorld NeoForge client bootstrap active");
    }

    private static void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(dev.ringworld.client.render.RingSurfaceTextureRenderer.pipeline());
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(RingSettingsPayload.ID, NeoForgeRingWorldClient::handleSettings);
        event.register(RingTerrainAtlasMetadataPayload.ID, NeoForgeRingWorldClient::handleAtlasMetadata);
        event.register(RingTerrainAtlasTilePayload.ID, NeoForgeRingWorldClient::handleAtlasTile);
        event.register(RingTerrainAtlasRevisionPayload.ID, NeoForgeRingWorldClient::handleAtlasRevision);
        event.register(RingAtlasPregenerationStatusPayload.ID, NeoForgeRingWorldClient::handleAtlasStatus);
    }

    private static void handleSettings(RingSettingsPayload payload, IPayloadContext context) {
        if (payload.formatVersion() != RingWorldSettings.FORMAT_VERSION) {
            context.disconnect(Component.literal(
                    "Incompatible RingWorld format: server=" + payload.formatVersion()
                            + ", client=" + RingWorldSettings.FORMAT_VERSION));
            return;
        }
        if (!RingSettingsHandshake.hasMatchingPayloadFingerprint(payload)) {
            context.disconnect(Component.literal("RingWorld layout fingerprint mismatch."));
            return;
        }
        if (!requiredServerChannelsAvailable()) {
            context.disconnect(Component.literal(
                    "Server RingWorld feature channels are missing or out of date."));
            return;
        }
        RingWorldClientSession.clear();
        long fingerprint = RingSettingsHandshake.fingerprintFor(payload);
        ClientRingState.set(new RingGeometry(payload.width(), payload.circumference()),
                payload.wallHeight(), payload.surfaceReferenceY(), fingerprint);
        RingClientPayloadTransport.send(RingSettingsHandshake.acknowledgementFor(payload));
    }

    private static boolean requiredServerChannelsAvailable() {
        return RingClientPayloadTransport.canSend(dev.ringworld.net.RingSettingsAckPayload.ID)
                && RingClientPayloadTransport.canSend(RingTerrainAtlasRequestPayload.ID)
                && RingClientPayloadTransport.canSend(
                        dev.ringworld.net.RingAtlasPregenerationStatusRequestPayload.ID)
                && RingClientPayloadTransport.canSend(
                        dev.ringworld.net.RingAtlasPregenerationControlPayload.ID);
    }

    private static void handleAtlasMetadata(
            RingTerrainAtlasMetadataPayload payload, IPayloadContext context) {
        boolean cacheComplete = ClientRingState.installTerrainAtlas(payload);
        if (Boolean.getBoolean(CURVED_OBJECT_CAPTURE_PROPERTY)) return;
        if (!RingClientPayloadTransport.canSend(RingTerrainAtlasRequestPayload.ID)) {
            context.disconnect(Component.literal(
                    "Server RingWorld terrain-atlas protocol is missing or out of date."));
            return;
        }
        RingClientPayloadTransport.send(new RingTerrainAtlasRequestPayload(
                payload.worldHash(), ClientRingState.terrainAtlasDurableRevision(), cacheComplete));
    }

    private static void handleAtlasTile(RingTerrainAtlasTilePayload payload, IPayloadContext context) {
        ClientRingState.applyTerrainAtlasTile(
                payload.worldHash(), payload.tileX(), payload.tileZ(), payload.data());
    }

    private static void handleAtlasRevision(
            RingTerrainAtlasRevisionPayload payload, IPayloadContext context) {
        ClientRingState.commitTerrainAtlasRevision(payload.worldHash(), payload.revision());
    }

    private static void handleAtlasStatus(
            RingAtlasPregenerationStatusPayload payload, IPayloadContext context) {
        AtlasPregenerationClientState.install(Minecraft.getInstance(), payload);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) ClientRingState.updateCameraPosition(client.player.getX());
        if (!Boolean.getBoolean(CURVED_OBJECT_CAPTURE_PROPERTY)) {
            ClientRingState.saveTerrainAtlasIfDue(false);
        }
        if (PRODUCTION_LIFECYCLE.tick(client)) return;
        if (LAYOUT_SWITCH.tick(client)) return;
        if (MULTIPLAYER_TEST.tick(client)) return;
        if (PROJECTION_CAPTURE.tick(client)) return;
        VISUAL_PARITY_CAPTURE.tick(client);
    }

    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        PROJECTION_CAPTURE.frameRendered();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RingWorldClientSession.clear();
    }

    private static final class NeoForgePayloadTransport implements RingClientPayloadTransport.Adapter {
        @Override
        public boolean canSend(CustomPacketPayload.Type<?> type) {
            var connection = Minecraft.getInstance().getConnection();
            return connection != null && NetworkRegistry.hasChannel(connection, type.id());
        }

        @Override
        public void send(CustomPacketPayload payload) {
            ClientPacketDistributor.sendToServer(payload);
        }
    }
}
