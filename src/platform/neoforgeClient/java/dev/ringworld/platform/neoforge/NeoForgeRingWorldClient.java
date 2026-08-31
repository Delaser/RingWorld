package dev.ringworld.platform.neoforge;

import com.mojang.brigadier.arguments.FloatArgumentType;
import dev.ringworld.RingWorldMod;
import dev.ringworld.client.AtlasPregenerationClientState;
import dev.ringworld.client.AtlasPregenerationUiTestClient;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.CurvedObjectCaptureClient;
import dev.ringworld.client.LayoutSwitchTestClient;
import dev.ringworld.client.MultiplayerTestClient;
import dev.ringworld.client.ProductionLifecycleTestClient;
import dev.ringworld.client.RingClientPayloadTransport;
import dev.ringworld.client.RingMapCompassCaptureClient;
import dev.ringworld.client.RingMediumIndustrialLightingTestClient;
import dev.ringworld.client.RingProjectionCaptureClient;
import dev.ringworld.client.RingVisualParityCaptureClient;
import dev.ringworld.client.RingWorldClientSession;
import dev.ringworld.client.RingWorldCreationUiTestClient;
import dev.ringworld.client.RingAtlasLightTuning;
import dev.ringworld.net.RingAtlasPregenerationStatusPayload;
import dev.ringworld.net.RingSettingsHandshake;
import dev.ringworld.net.RingSettingsPayload;
import dev.ringworld.net.RingSkyProfilePayload;
import dev.ringworld.net.RingTerrainAtlasMetadataPayload;
import dev.ringworld.net.RingTerrainAtlasRequestPayload;
import dev.ringworld.net.RingTerrainAtlasRevisionPayload;
import dev.ringworld.net.RingTerrainAtlasTilePayload;
import dev.ringworld.net.RingTerrainPreviewPayload;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
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
    private static final ProductionLifecycleTestClient PRODUCTION_LIFECYCLE =
            new ProductionLifecycleTestClient();
    private static final LayoutSwitchTestClient LAYOUT_SWITCH = new LayoutSwitchTestClient();
    private static final MultiplayerTestClient MULTIPLAYER_TEST = new MultiplayerTestClient();
    private static final RingProjectionCaptureClient PROJECTION_CAPTURE =
            new RingProjectionCaptureClient();
    private static final RingVisualParityCaptureClient VISUAL_PARITY_CAPTURE =
            new RingVisualParityCaptureClient();
    private static final CurvedObjectCaptureClient CURVED_OBJECT_CAPTURE =
            new CurvedObjectCaptureClient();
    private static final AtlasPregenerationUiTestClient ATLAS_PREGENERATION_UI_TEST =
            new AtlasPregenerationUiTestClient();
    private static final RingWorldCreationUiTestClient CREATION_UI_TEST =
            new RingWorldCreationUiTestClient();
    private static final RingMapCompassCaptureClient MAP_COMPASS_CAPTURE =
            new RingMapCompassCaptureClient();
    private static final RingMediumIndustrialLightingTestClient MEDIUM_INDUSTRIAL_LIGHTING =
            new RingMediumIndustrialLightingTestClient();

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
        event.register(RingSkyProfilePayload.ID, NeoForgeRingWorldClient::handleSkyProfile);
        event.register(RingTerrainAtlasMetadataPayload.ID, NeoForgeRingWorldClient::handleAtlasMetadata);
        event.register(RingTerrainAtlasTilePayload.ID, NeoForgeRingWorldClient::handleAtlasTile);
        event.register(RingTerrainAtlasRevisionPayload.ID, NeoForgeRingWorldClient::handleAtlasRevision);
        event.register(RingTerrainPreviewPayload.ID, NeoForgeRingWorldClient::handleTerrainPreview);
        event.register(RingAtlasPregenerationStatusPayload.ID, NeoForgeRingWorldClient::handleAtlasStatus);
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        var falloff = Commands.argument("falloff", FloatArgumentType.floatArg(
                        dev.ringworld.world.RingAtlasLightProfile.MIN_FALLOFF,
                        dev.ringworld.world.RingAtlasLightProfile.MAX_FALLOFF))
                .executes(context -> setGamma(context.getSource(),
                        FloatArgumentType.getFloat(context, "falloff"),
                        dev.ringworld.world.RingAtlasLightProfile.DEFAULT_GAMMA_PEAK));
        falloff.then(Commands.argument("peak", FloatArgumentType.floatArg(
                        dev.ringworld.world.RingAtlasLightProfile.MIN_PEAK,
                        dev.ringworld.world.RingAtlasLightProfile.MAX_PEAK))
                .executes(context -> setGamma(context.getSource(),
                        FloatArgumentType.getFloat(context, "falloff"),
                        FloatArgumentType.getFloat(context, "peak"))));
        var ringLights = Commands.literal("ringlights")
                .executes(context -> showAtlasLight(context.getSource()))
                .then(Commands.literal("show")
                        .executes(context -> showAtlasLight(context.getSource())))
                .then(Commands.literal("reset").executes(context -> {
                    var profile = RingAtlasLightTuning.reset();
                    sendAtlasLightFeedback(context.getSource(), profile.summary());
                    return 1;
                }))
                .then(falloff);
        event.getDispatcher().register(Commands.literal("ringworld").then(ringLights));
    }

    private static int showAtlasLight(CommandSourceStack source) {
        sendAtlasLightFeedback(source, RingAtlasLightTuning.profile().summary());
        return 1;
    }

    private static int setGamma(CommandSourceStack source, float falloff, float peak) {
        var profile = RingAtlasLightTuning.useGamma(falloff, peak);
        sendAtlasLightFeedback(source, profile.summary());
        return 1;
    }

    private static void sendAtlasLightFeedback(CommandSourceStack source, String summary) {
        source.sendSuccess(() -> Component.literal("Ring Atlas light: " + summary), false);
    }

    private static void handleSettings(RingSettingsPayload payload, IPayloadContext context) {
        // Keep session teardown, static client state, and acknowledgement send
        // together on the client game thread. enqueueWork is immediate when the
        // client payload registry has already selected that thread.
        context.enqueueWork(() -> handleSettingsOnClientThread(payload, context));
    }

    private static void handleSkyProfile(RingSkyProfilePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientRingState.setSkyProfile(payload.profile());
            } catch (IllegalArgumentException exception) {
                context.disconnect(Component.literal("Invalid RingWorld sky profile from server."));
            }
        });
    }

    private static void handleSettingsOnClientThread(
            RingSettingsPayload payload, IPayloadContext context) {
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
                payload.wallHeight(), payload.surfaceReferenceY(),
                payload.terrainNoiseMapping(), payload.wallStyle(), payload.skyProfile(),
                payload.generationSettings(), payload.seed(), payload.formatVersion(), fingerprint);
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
        context.enqueueWork(() -> handleAtlasMetadataOnClientThread(payload, context));
    }

    private static void handleAtlasMetadataOnClientThread(
            RingTerrainAtlasMetadataPayload payload, IPayloadContext context) {
        boolean cacheComplete = ClientRingState.installTerrainAtlas(payload);
        if (Boolean.getBoolean(CurvedObjectCaptureClient.ENABLE_PROPERTY)) return;
        if (!RingClientPayloadTransport.canSend(RingTerrainAtlasRequestPayload.ID)) {
            context.disconnect(Component.literal(
                    "Server RingWorld terrain-atlas protocol is missing or out of date."));
            return;
        }
        RingClientPayloadTransport.send(new RingTerrainAtlasRequestPayload(
                payload.worldHash(), ClientRingState.terrainAtlasDurableRevision(), cacheComplete));
    }

    private static void handleAtlasTile(RingTerrainAtlasTilePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleAtlasTileOnClientThread(payload));
    }

    private static void handleAtlasTileOnClientThread(RingTerrainAtlasTilePayload payload) {
        ClientRingState.applyTerrainAtlasTile(
                payload.worldHash(), payload.tileX(), payload.tileZ(), payload.data());
    }

    private static void handleAtlasRevision(
            RingTerrainAtlasRevisionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleAtlasRevisionOnClientThread(payload));
    }

    private static void handleAtlasRevisionOnClientThread(RingTerrainAtlasRevisionPayload payload) {
        ClientRingState.commitTerrainAtlasRevision(payload.worldHash(), payload.revision());
    }

    private static void handleTerrainPreview(
            RingTerrainPreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRingState.installTerrainPreview(payload));
    }

    private static void handleAtlasStatus(
            RingAtlasPregenerationStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleAtlasStatusOnClientThread(payload));
    }

    private static void handleAtlasStatusOnClientThread(RingAtlasPregenerationStatusPayload payload) {
        AtlasPregenerationClientState.install(Minecraft.getInstance(), payload);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (CREATION_UI_TEST.startMenuIfEnabled(client)) {
            CREATION_UI_TEST.tick(client);
            return;
        }
        if (CURVED_OBJECT_CAPTURE.startWorldIfEnabled(client)) return;
        if (MAP_COMPASS_CAPTURE.startWorldIfEnabled(client)) return;
        if (ATLAS_PREGENERATION_UI_TEST.startWorldIfEnabled(client)) return;
        if (client.player != null) ClientRingState.updateCameraPosition(client.player.getX());
        if (!Boolean.getBoolean(CurvedObjectCaptureClient.ENABLE_PROPERTY)) {
            ClientRingState.saveTerrainAtlasIfDue(false);
        }
        if (PRODUCTION_LIFECYCLE.tick(client)) return;
        if (LAYOUT_SWITCH.tick(client)) return;
        if (MULTIPLAYER_TEST.tick(client)) return;
        if (PROJECTION_CAPTURE.tick(client)) return;
        if (VISUAL_PARITY_CAPTURE.tick(client)) return;
        if (CURVED_OBJECT_CAPTURE.tick(client)) return;
        if (MEDIUM_INDUSTRIAL_LIGHTING.tick(client)) return;
        if (MAP_COMPASS_CAPTURE.tick(client)) return;
        ATLAS_PREGENERATION_UI_TEST.tick(client);
    }

    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        PROJECTION_CAPTURE.frameRendered();
        VISUAL_PARITY_CAPTURE.frameRendered();
        ATLAS_PREGENERATION_UI_TEST.frameRendered();
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
