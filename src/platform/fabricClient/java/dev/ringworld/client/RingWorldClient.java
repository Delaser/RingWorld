package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.server.RingWorldVanillaFixtureRegistries;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.net.RingSettingsPayload;
import dev.ringworld.net.RingSkyProfilePayload;
import dev.ringworld.net.RingSettingsAckPayload;
import dev.ringworld.net.RingSettingsHandshake;
import dev.ringworld.net.RingAtlasPregenerationControlPayload;
import dev.ringworld.net.RingAtlasPregenerationStatusPayload;
import dev.ringworld.net.RingAtlasPregenerationStatusRequestPayload;
import dev.ringworld.net.RingTerrainAtlasMetadataPayload;
import dev.ringworld.net.RingTerrainAtlasRequestPayload;
import dev.ringworld.net.RingTerrainAtlasRevisionPayload;
import dev.ringworld.net.RingTerrainAtlasTilePayload;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRenderProfile;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Client entrypoint. Rendering and pose mixins consume ClientRingState. */
public final class RingWorldClient implements ClientModInitializer {
    /** Repeatable terrain makes visual atlas regressions comparable between runs. */
    private static final String AUTOMATED_TEST_SEED = "-2162056627494116761";
    /** Literal laps above this size turn a topology probe into bulk pregeneration. */
    private static final int FULL_TEST_CIRCUIT_MAX_BLOCKS = 4_096;
    private final MultiplayerTestClient multiplayerTest = new MultiplayerTestClient();
    private final LayoutSwitchTestClient layoutSwitchTest = new LayoutSwitchTestClient();
    private final ProductionLifecycleTestClient productionLifecycleTest =
            new ProductionLifecycleTestClient();
    private final RingProjectionCaptureClient projectionCapture =
            new RingProjectionCaptureClient();
    private final RingVisualParityCaptureClient visualParityCapture =
            new RingVisualParityCaptureClient();
    private final CurvedObjectCaptureClient curvedObjectCapture =
            new CurvedObjectCaptureClient();
    private final AtlasPregenerationUiTestClient atlasPregenerationUiTest =
            new AtlasPregenerationUiTestClient();
    private final RingWorldCreationUiTestClient creationUiTest =
            new RingWorldCreationUiTestClient();
    private final RingMapCompassCaptureClient mapCompassCapture =
            new RingMapCompassCaptureClient();
    private boolean testScreenOpened;
    private boolean testWorldStarted;
    private boolean testPerformanceProfileApplied;
    private int testWorldTicks;
    private boolean testScreenshotSaved;
    private boolean testBedPresentationProbeComplete;
    private boolean testSeamMoveSent;
    private boolean testSeamScreenshotSaved;
    private boolean testSeamBlockActionSent;
    private int testSeamPrefetchTicks;
    private int testSeamInteractionWaitTicks;
    private int testSeamSettleTicks;
    private boolean testSeamEntityProjected;
    private double testFirstSeamBoundary = Double.NaN;
    private int testSecondCircuitGateTicks;
    private int testSecondCircuitPrefetchTicks;
    private int testSecondCircuitWaitTicks;
    private int testSecondCircuitSettleTicks;
    private boolean testSecondCircuitSetupTeleportSent;
    private double testSecondSeamBoundary = Double.NaN;
    private boolean testSecondCircuitScreenshotSaved;
    private boolean testSecondCircuitCameraArmed;
    private float testSecondCircuitStartYaw;
    private float testSecondCircuitStartPitch;
    private float testSeamStartYaw;
    private float testSeamStartPitch;
    private boolean testCameraPositioned;
    private boolean testBoundaryMetricsActive;
    private boolean testBoundaryScreenshotSaved;
    private int testBoundaryTicks;
    private boolean testSkyDayCommandSent;
    private boolean testSkyClockNormalized;
    private boolean testSkyDayScreenshotSaved;
    private boolean testSkyDuskCommandSent;
    private boolean testSkyDuskScreenshotSaved;
    private boolean testSkyNightCommandSent;
    private boolean testSkyNightScreenshotSaved;
    private boolean testSkyRainCommandSent;
    private boolean testSkyRainScreenshotSaved;
    private boolean testSkyClearCommandSent;
    private boolean testSkyCycleComplete;
    private boolean testRingVisibilityCaptureArmed;
    private boolean testRingVisibilityTangentScreenshotSaved;
    private boolean testRingVisibilityUpCaptureArmed;
    private boolean testRingVisibilityScreenshotSaved;
    private boolean testRingVisibilityAtlasWaitLogged;
    private int testRingVisibilityAtlasWaitTicks;
    private int testSkySettleTicks;
    private long testLastFrameNanos;
    private long testTotalFrameNanos;
    private long testMaxFrameNanos;
    private int testFrameSamples;
    private int testSlowFrames;
    private int diagnosticJoinTicks;
    private boolean diagnosticJoinScreenshotSaved;

    @Override
    public void onInitializeClient() {
        RenderPipelines.register(RingSurfaceTextureRenderer.pipeline());
        RingClientPayloadTransport.configure(new FabricRingClientPayloadTransport());
        ClientRingState.configureCacheDirectory(
                FabricLoader.getInstance().getGameDir().resolve("ringworld-cache"));
        ClientPlayNetworking.registerGlobalReceiver(RingSettingsPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.formatVersion() != dev.ringworld.world.RingWorldSettings.FORMAT_VERSION) {
                        var handler = context.client().getConnection();
                        if (handler != null) {
                            handler.getConnection().disconnect(Component.literal(
                                    "Incompatible RingWorld format: server=" + payload.formatVersion()
                                            + ", client=" + dev.ringworld.world.RingWorldSettings.FORMAT_VERSION));
                        }
                        return;
                    }
                    // A SkyRenderer instance survives the trip back through
                    // the menus. Destroy the previous world's static GPU ring
                    // before installing another session, even when both worlds
                    // happen to use identical dimensions.
                    if (!RingSettingsHandshake.hasMatchingPayloadFingerprint(payload)) {
                        var handler = context.client().getConnection();
                        if (handler != null) {
                            handler.getConnection().disconnect(Component.literal(
                                    "RingWorld layout fingerprint mismatch."));
                        }
                        return;
                    }
                    long fingerprint = RingSettingsHandshake.fingerprintFor(payload);
                    if (!RingClientPayloadTransport.canSend(RingSettingsAckPayload.ID)
                            || !RingClientPayloadTransport.canSend(RingTerrainAtlasRequestPayload.ID)
                            || !RingClientPayloadTransport.canSend(RingAtlasPregenerationStatusRequestPayload.ID)
                            || !RingClientPayloadTransport.canSend(RingAtlasPregenerationControlPayload.ID)) {
                        var handler = context.client().getConnection();
                        if (handler != null) {
                            handler.getConnection().disconnect(Component.literal(
                                    "Server RingWorld feature channels are missing or out of date."));
                        }
                        return;
                    }
                    clearRingSession();
                    ClientRingState.set(
                            new RingGeometry(payload.width(), payload.circumference()),
                            payload.wallHeight(), payload.surfaceReferenceY(),
                            payload.terrainNoiseMapping(), payload.wallStyle(), payload.skyProfile(),
                            fingerprint);
                    RingClientPayloadTransport.send(RingSettingsHandshake.acknowledgementFor(payload));
                }));
        ClientPlayNetworking.registerGlobalReceiver(RingSkyProfilePayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    try {
                        ClientRingState.setSkyProfile(payload.profile());
                    } catch (IllegalArgumentException exception) {
                        var handler = context.client().getConnection();
                        if (handler != null) {
                            handler.getConnection().disconnect(Component.literal(
                                    "Invalid RingWorld sky profile from server."));
                        }
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(RingTerrainAtlasMetadataPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    boolean cacheComplete = ClientRingState.installTerrainAtlas(payload);
                    // This short-lived capture validates live object/terrain
                    // alignment and intentionally does not download the LOD
                    // atlas while its two frames are settling.
                    if (Boolean.getBoolean(CurvedObjectCaptureClient.ENABLE_PROPERTY)) return;
                    if (!RingClientPayloadTransport.canSend(RingTerrainAtlasRequestPayload.ID)) {
                        var handler = context.client().getConnection();
                        if (handler != null) {
                            handler.getConnection().disconnect(Component.literal(
                                    "Server RingWorld terrain-atlas protocol is missing or out of date."));
                        }
                        return;
                    }
                    RingClientPayloadTransport.send(new RingTerrainAtlasRequestPayload(
                            payload.worldHash(), ClientRingState.terrainAtlasDurableRevision(), cacheComplete));
                }));
        ClientPlayNetworking.registerGlobalReceiver(RingTerrainAtlasTilePayload.ID, (payload, context) ->
                context.client().execute(() -> ClientRingState.applyTerrainAtlasTile(
                        payload.worldHash(), payload.tileX(), payload.tileZ(), payload.data())));
        ClientPlayNetworking.registerGlobalReceiver(RingTerrainAtlasRevisionPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientRingState.commitTerrainAtlasRevision(
                        payload.worldHash(), payload.revision())));
        ClientPlayNetworking.registerGlobalReceiver(RingAtlasPregenerationStatusPayload.ID, (payload, context) ->
                context.client().execute(() -> AtlasPregenerationClientState.install(context.client(), payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                // Fabric may fire this callback on Netty's local I/O thread.
                // Cache saves and GPU teardown must stay on the client thread;
                // otherwise disconnect can race the normal teardown mixin's
                // final atlas save over the same temporary file.
                client.execute(RingWorldClient::clearRingSession));
        LevelRenderEvents.END_MAIN.register(context -> {
            recordTestFrame();
            projectionCapture.frameRendered();
            visualParityCapture.frameRendered();
            atlasPregenerationUiTest.frameRendered();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (creationUiTest.startMenuIfEnabled(client)) {
                creationUiTest.tick(client);
                return;
            }
            if (client.player != null) ClientRingState.updateCameraPosition(client.player.getX());
            // This short-lived renderer capture deliberately postpones its
            // cache write until disconnect so a periodic async save cannot
            // race the forced teardown save over the same temporary file.
            if (!Boolean.getBoolean(CurvedObjectCaptureClient.ENABLE_PROPERTY)) {
                ClientRingState.saveTerrainAtlasIfDue(false);
            }
            if (productionLifecycleTest.tick(client)) return;
            if (layoutSwitchTest.tick(client)) return;
            if (multiplayerTest.tick(client)) return;
            if (projectionCapture.tick(client)) return;
            if (visualParityCapture.tick(client)) return;
            if (curvedObjectCapture.tick(client)) return;
            if (mapCompassCapture.startWorldIfEnabled(client)) return;
            if (mapCompassCapture.tick(client)) return;
            if (atlasPregenerationUiTest.tick(client)) return;
            saveDiagnosticJoinScreenshot(client);
            startAutomatedTestWorld(client);
        });
    }

    /**
     * Releases all client-owned state for the previous RingWorld session.
     * Called from both Fabric's network lifecycle and Minecraft's local-world
     * teardown path because an integrated-server exit does not always deliver
     * the play-connection disconnect event before the next world is opened.
     */
    public static void clearRingSession() {
        RingWorldClientSession.clear();
    }

    /**
     * Opt-in development capture used to verify a real saved-world join
     * without enabling the destructive automated traversal sequence.
     */
    private void saveDiagnosticJoinScreenshot(Minecraft client) {
        if (!Boolean.getBoolean("ringworld.captureJoinFrame")
                || diagnosticJoinScreenshotSaved || client.player == null) return;
        if (RingMinecraftClientAccess.screen(client) instanceof PauseScreen) RingMinecraftClientAccess.setScreen(client, null);
        if (RingMinecraftClientAccess.screen(client) != null || ++diagnosticJoinTicks < 160) return;
        if (!client.levelRenderer.hasRenderedAllSections() && diagnosticJoinTicks < 600) return;

        diagnosticJoinScreenshotSaved = true;
        RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "ringworld-join-diagnostic.png",
                RingMinecraftClientAccess.mainRenderTarget(client), 1,
                message -> RingWorldMod.LOGGER.info("[diagnostic] join screenshot: {}", message.getString()));
    }

    private void startAutomatedTestWorld(Minecraft client) {
        if (atlasPregenerationUiTest.startWorldIfEnabled(client)) return;
        // Once the Atlas fixture has invoked Create World there is a short
        // interval before the integrated client player exists. Do not let the
        // legacy test-mode launcher mistake that interval for an idle title
        // screen and create a second world over the first connection.
        if (atlasPregenerationUiTest.enabled()) return;
        if (!RingWorldConfig.load().testMode()) return;
        if (!testPerformanceProfileApplied) {
            // A representative, stable local profile. Production play still
            // follows the user's own options because this is test-mode only.
            // Exercise the same long-range path used by the current 28-chunk
            // play profile; this catches flat-frustum regressions that a short
            // smoke-test distance cannot reveal.
            client.options.renderDistance().set(
                    RingWorldConfig.load().testViewDistanceChunks());
            client.options.simulationDistance().set(5);
            client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
            client.options.pauseOnLostFocus = false;
            client.debugEntries.setStatus(DebugScreenEntries.PLAYER_POSITION,
                    atlasPregenerationUiTest.enabled()
                            ? DebugScreenEntryStatus.IN_OVERLAY
                            : DebugScreenEntryStatus.ALWAYS_ON);
            testPerformanceProfileApplied = true;
        }
        if (client.level != null) {
            // The development client can lose foreground focus while Gradle
            // hands it off to the desktop app. Do not let that one-time pause
            // turn the captured smoke-test frame into a menu screenshot.
            if (RingMinecraftClientAccess.screen(client) instanceof PauseScreen) RingMinecraftClientAccess.setScreen(client, null);
            if (RingMinecraftClientAccess.screen(client) != null) return;
            saveAutomatedScreenshot(client);
            runAutomatedBedPresentationProbe(client);
            runAutomatedSeamTraversal(client);
            runAutomatedSecondCircuit(client);
            runAutomatedBoundaryStress(client);
            runAutomatedSkyCycle(client);
            runAutomatedRingVisibility(client);
            return;
        }
        if (testWorldStarted) return;
        if (!testScreenOpened) {
            CreateWorldScreen.openFresh(client, () -> testScreenOpened = false);
            testScreenOpened = true;
            return;
        }
        if (RingMinecraftClientAccess.screen(client) instanceof CreateWorldScreen screen) {
            WorldCreationUiState creator = screen.getUiState();
            creator.setName("RingWorld Automated Test");
            creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            creator.setAllowCommands(true);
            creator.setSeed(AUTOMATED_TEST_SEED);
            RingWorldConfig config = RingWorldConfig.load();
            RingWorldMod.LOGGER.info("[test] creating a creative {}x{}-chunk test world",
                    config.circumferenceBlocks() / 16, config.widthBlocks() / 16);
            ((CreateWorldScreenInvoker) screen).ringworld$createLevel();
            testWorldStarted = true;
        }
    }

    /**
     * Gives chunk meshing time to settle, then captures the actual rendered
     * framebuffer. This makes the local smoke test inspectable without manual
     * input and, importantly, exercises the curved terrain shader in-game.
     */
    private void saveAutomatedScreenshot(Minecraft client) {
        if (testScreenshotSaved || client.player == null) return;
        testWorldTicks++;
        // The terrain-complete predicate starts true before the first chunk
        // mesh is queued, so also require a real settling interval. In the
        // rare event a renderer never settles, retain a one-minute escape
        // hatch so the test still leaves diagnostic evidence behind.
        if (testWorldTicks < 600) return;
        // The integrated server's initial position exchange can supersede a
        // server-side showcase teleport. Hold the local creative test camera
        // at its observation point long enough for the next movement packet
        // to make both sides agree before capturing.
        if (!testCameraPositioned) {
            client.player.setPos(0.5, Math.max(120.0, client.player.getY()), 0.5);
            client.player.setYRot(90.0f);
            client.player.setXRot(22.0f);
            testCameraPositioned = true;
        }
        if (testWorldTicks < 640) return;
        if (!client.levelRenderer.hasRenderedAllSections() && testWorldTicks < 1_200) return;
        testScreenshotSaved = true;
        resetFrameMetrics();
        RingWorldMod.LOGGER.info("[test] renderer camera: x={}, y={}, z={}, yaw={}, pitch={}",
                client.player.getX(), client.player.getY(), client.player.getZ(),
                client.player.getYRot(), client.player.getXRot());
        RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "ringworld-automated.png", RingMinecraftClientAccess.mainRenderTarget(client), 1,
                message -> RingWorldMod.LOGGER.info("[test] renderer screenshot: {}", message.getString()));
    }

    /**
     * Exercises the exact local getter used by vanilla's sleeping-data
     * callback without writing a bed position to the integrated server or
     * changing the player's pose. The full sleep/wake lifecycle remains a
     * manual multiplayer check because it requires a real night and damage
     * source, while this probe catches the client-chart regression that used
     * to place a seam traveller at raw canonical X.
     */
    private void runAutomatedBedPresentationProbe(Minecraft client) {
        if (!testScreenshotSaved || testBedPresentationProbeComplete || client.player == null) return;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;

        BlockPos canonicalBed = new BlockPos(geometry.circumferenceBlocks() - 2,
                (int) Math.floor(client.player.getY()), (int) Math.floor(client.player.getZ()));
        int expectedPresentationX = (int) Math.round(geometry.nearestImageX(
                canonicalBed.getX(), client.player.getX()));
        client.player.setSleepingPos(canonicalBed);
        Optional<BlockPos> projectedBed = client.player.getSleepingPos();
        boolean passed = expectedPresentationX != canonicalBed.getX()
                && projectedBed.map(BlockPos::getX).filter(x -> x == expectedPresentationX).isPresent();
        client.player.clearSleepingPos();
        testBedPresentationProbeComplete = true;
        RingWorldMod.LOGGER.info(
                "[test] seam bed presentation result={} canonical={} projected={} expectedX={}",
                passed, canonicalBed, projectedBed.orElse(null), expectedPresentationX);
    }

    /** Drives one small, packet-backed crossing after the server arms the seam test. */
    private void runAutomatedSeamTraversal(Minecraft client) {
        if (!testScreenshotSaved || !testRingVisibilityScreenshotSaved || client.player == null) return;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;
        if (!testSeamMoveSent && Double.isNaN(testFirstSeamBoundary)) {
            double candidate = Math.ceil(client.player.getX() / geometry.circumferenceBlocks())
                    * geometry.circumferenceBlocks();
            if (client.player.getX() >= candidate - 8.5
                    && client.player.getX() <= candidate) {
                testFirstSeamBoundary = candidate;
            }
        }
        if (!testSeamMoveSent && !Double.isNaN(testFirstSeamBoundary)) {
            if (!testSeamEntityProjected) {
                double expectedItemX = geometry.nearestImageX(2.5, client.player.getX());
                for (var entity : client.level.entitiesForRendering()) {
                    if (entity instanceof ItemEntity item
                            && item.getItem().is(RingWorldVanillaFixtureRegistries.item("diamond"))
                            && Math.abs(item.getX() - expectedItemX) < 1.0) {
                        testSeamEntityProjected = true;
                        RingWorldMod.LOGGER.info("[test] seam entity projected into local chart at x={}", item.getX());
                        break;
                    }
                }
            }
            // A walking player naturally gives chunk streaming time to load
            // the far side of the seam. Reproduce that condition before the
            // motion probe so the screenshot tests continuity, not a test-only
            // long teleport followed immediately by a crossing.
            testSeamPrefetchTicks++;
            if (!testSeamEntityProjected || testSeamPrefetchTicks < 100
                    || (!client.levelRenderer.hasRenderedAllSections() && testSeamPrefetchTicks < 300)) return;
            // Do not count the deliberate server setup teleport as the short
            // packet-backed seam crossing this test is about to exercise.
            ClientRingState.resetCameraContinuity(client.player.getX());
            testSeamStartYaw = client.player.getYRot();
            testSeamStartPitch = client.player.getXRot();
            testSeamMoveSent = true;
            RingWorldMod.LOGGER.info("[test] beginning smooth client movement across the circumference seam at yaw={}, pitch={}",
                    testSeamStartYaw, testSeamStartPitch);
            return;
        }
        double firstTargetX = testFirstSeamBoundary + 2.0;
        if (testSeamMoveSent && client.player.getX() < firstTargetX) {
            double nextX = Math.min(firstTargetX, client.player.getX() + 0.25);
            client.player.setPos(nextX, client.player.getY(), client.player.getZ());
            return;
        }
        if (testSeamMoveSent && !testSeamScreenshotSaved
                && client.player.getX() >= testFirstSeamBoundary
                && ClientRingState.cameraSeamCrossings() > 0) {
            if (!testSeamBlockActionSent && client.gameMode != null) {
                double logicalBlockX = geometry.nearestImageX(2.0, client.player.getX());
                BlockPos logicalTestBlock = new BlockPos(
                        (int)Math.floor(logicalBlockX), 119, 0);
                var state = client.level.getBlockState(logicalTestBlock);
                if (!state.is(RingWorldVanillaFixtureRegistries.block("gold_block"))) {
                    testSeamInteractionWaitTicks++;
                    if (testSeamInteractionWaitTicks == 1
                            || testSeamInteractionWaitTicks % 200 == 0) {
                        BlockPos canonicalTestBlock = new BlockPos(2, 119, 0);
                        RingWorldMod.LOGGER.warn(
                                "[test] waiting for seam block update: presentationX={}, "
                                        + "cameraChart={}, crossings={}, logicalState={}, "
                                        + "canonicalState={}",
                                client.player.getX(),
                                ClientRingState.cameraPosition().chartIndex(),
                                ClientRingState.cameraSeamCrossings(),
                                state.getBlock(),
                                client.level.getBlockState(canonicalTestBlock).getBlock());
                    }
                    return;
                }
                client.gameMode.startDestroyBlock(logicalTestBlock, Direction.UP);
                testSeamBlockActionSent = true;
                RingWorldMod.LOGGER.info("[test] sent creative block action across the seam at {}", logicalTestBlock);
            }
            // Let canonical chunk-zero packets populate this presentation chart
            // before capturing the seam view.
            if (++testSeamSettleTicks < 60) return;
            testSeamScreenshotSaved = true;
            double averageFrameMs = testFrameSamples == 0 ? 0.0
                    : testTotalFrameNanos / 1_000_000.0 / testFrameSamples;
            RingWorldMod.LOGGER.info("[test] frame pacing samples={}, averageMs={}, maxMs={}, over50Ms={}",
                    testFrameSamples, averageFrameMs, testMaxFrameNanos / 1_000_000.0, testSlowFrames);
            RingWorldMod.LOGGER.info("[test] seam landed at presentationX={}, chart={}, client seam crossings={}",
                    client.player.getX(), ClientRingState.cameraPosition().chartIndex(),
                    ClientRingState.cameraSeamCrossings());
            RingWorldMod.LOGGER.info("[test] seam camera yaw={} (delta={}), pitch={} (delta={}), correction packets={}",
                    client.player.getYRot(), client.player.getYRot() - testSeamStartYaw,
                    client.player.getXRot(), client.player.getXRot() - testSeamStartPitch,
                    ClientRingState.seamCorrectionPackets());
            RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "ringworld-seam.png", RingMinecraftClientAccess.mainRenderTarget(client), 1,
                    message -> RingWorldMod.LOGGER.info("[test] seam renderer screenshot: {}", message.getString()));
            client.debugEntries.setStatus(
                    DebugScreenEntries.PLAYER_POSITION, DebugScreenEntryStatus.IN_OVERLAY);
        }
    }

    /**
     * Traverses the rest of the small development circumference and crosses
     * the same physical seam a second time. The fast middle section pauses
     * whenever terrain meshing falls behind; the actual seam approach remains
     * the same quarter-block-per-tick movement used by the first crossing.
     * Only the non-seam middle uses a circumference-derived, bounded step and
     * a high flight lane so large-ring tests do not spend minutes clipping
     * through ordinary mountain terrain.
     */
    private void runAutomatedSecondCircuit(Minecraft client) {
        if (!testSeamScreenshotSaved || testSecondCircuitScreenshotSaved || client.player == null) return;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;
        // Keep the seam chunks and test entities resident until the server's
        // 240-tick projectile/AI/fluid/explosion observation window closes.
        // Starting the four-block-per-tick lap immediately made a valid probe
        // disappear through ordinary chunk unloading.
        if (++testSecondCircuitGateTicks < 300) return;

        boolean sampledLargeCircuit =
                geometry.circumferenceBlocks() > FULL_TEST_CIRCUIT_MAX_BLOCKS;
        if (sampledLargeCircuit && !testSecondCircuitSetupTeleportSent) {
            int canonicalApproachX = geometry.circumferenceBlocks() - 8;
            client.getConnection().sendCommand(
                    "tp @s " + canonicalApproachX + " 120 0.5");
            testSecondCircuitSetupTeleportSent = true;
            RingWorldMod.LOGGER.info(
                    "[test] sampling large-ring far-side chart before the second natural seam at canonical x={}",
                    canonicalApproachX);
            return;
        }
        if (sampledLargeCircuit && Double.isNaN(testSecondSeamBoundary)) {
            double canonicalApproachX = geometry.circumferenceBlocks() - 8.0;
            if (Math.abs(geometry.shortestCircumferenceDelta(
                    client.player.getX(), canonicalApproachX)) >= 1.0) {
                return;
            }
            testSecondSeamBoundary = geometry.nextPositiveSeamX(client.player.getX());
            RingWorldMod.LOGGER.info(
                    "[test] large-ring second seam uses presentation boundary={} from x={}",
                    testSecondSeamBoundary, client.player.getX());
        } else if (!sampledLargeCircuit && Double.isNaN(testSecondSeamBoundary)) {
            testSecondSeamBoundary = testFirstSeamBoundary + geometry.circumferenceBlocks();
        }
        double secondSeamBoundary = testSecondSeamBoundary;
        double approachX = secondSeamBoundary - 8.0;
        double targetX = secondSeamBoundary + 2.0;
        if (client.player.getX() < approachX) {
            double fastStep = Math.min(8.0,
                    Math.max(4.0, geometry.circumferenceBlocks() / 2_048.0));
            double lookAhead = Math.max(48.0, fastStep * 8.0);
            if (testSecondCircuitGateTicks % 600 == 0) {
                RingWorldMod.LOGGER.info(
                        "[test] second-circuit progress x={}/{}, step={}",
                        client.player.getX(), approachX, fastStep);
            }
            int nextChunkX = ((int)Math.floor(client.player.getX() + lookAhead)) >> 4;
            int chunkZ = ((int)Math.floor(client.player.getZ())) >> 4;
            if (client.level.getChunkSource().getChunk(nextChunkX, chunkZ, ChunkStatus.FULL, false) == null) {
                if (++testSecondCircuitWaitTicks % 200 == 0) {
                    RingWorldMod.LOGGER.info("[test] second-circuit streaming wait at x={}, requested chunk={},{}",
                            client.player.getX(), nextChunkX, chunkZ);
                }
                return;
            }
            testSecondCircuitWaitTicks = 0;
            double flightY = Math.max(client.player.getY(),
                    client.level.getMaxY() - 16.0);
            client.player.setPos(Math.min(approachX, client.player.getX() + fastStep),
                    flightY, client.player.getZ());
            return;
        }
        if (client.player.getX() < targetX) {
            if (Math.abs(client.player.getY() - 120.0) > 0.01) {
                client.player.setPos(client.player.getX(), 120.0, client.player.getZ());
                return;
            }
            if (!testSecondCircuitCameraArmed) {
                testSecondCircuitCameraArmed = true;
                testSecondCircuitStartYaw = client.player.getYRot();
                testSecondCircuitStartPitch = client.player.getXRot();
                ClientRingState.resetCameraContinuity(client.player.getX());
            }
            if (++testSecondCircuitPrefetchTicks < 100
                    || (!client.levelRenderer.hasRenderedAllSections() && testSecondCircuitPrefetchTicks < 400)) return;
            client.player.setPos(Math.min(targetX, client.player.getX() + 0.25),
                    client.player.getY(), client.player.getZ());
            return;
        }
        if (++testSecondCircuitSettleTicks < 60) return;

        double projectedMovingEntityX = Double.NaN;
        for (var entity : client.level.entitiesForRendering()) {
            if (entity instanceof ItemEntity item && item.getItem().is(RingWorldVanillaFixtureRegistries.item("emerald"))) {
                projectedMovingEntityX = item.getX();
                break;
            }
        }
        testSecondCircuitScreenshotSaved = true;
        RingWorldMod.LOGGER.info("[test] second seam landed at presentationX={}, chart={}, client seam crossings={}",
                client.player.getX(), ClientRingState.cameraPosition().chartIndex(),
                ClientRingState.cameraSeamCrossings());
        RingWorldMod.LOGGER.info("[test] second seam camera yaw={} (delta={}), pitch={} (delta={}), correction packets={}",
                client.player.getYRot(), client.player.getYRot() - testSecondCircuitStartYaw,
                client.player.getXRot(), client.player.getXRot() - testSecondCircuitStartPitch,
                ClientRingState.seamCorrectionPackets());
        RingWorldMod.LOGGER.info("[test] moving entity projected near second client chart={}, x={}",
                Math.abs(projectedMovingEntityX - client.player.getX()) < 8.0,
                projectedMovingEntityX);
        RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "ringworld-second-wrap.png", RingMinecraftClientAccess.mainRenderTarget(client), 1,
                message -> RingWorldMod.LOGGER.info("[test] second-wrap renderer screenshot: {}", message.getString()));
    }

    private void resetFrameMetrics() {
        testLastFrameNanos = 0L;
        testTotalFrameNanos = 0L;
        testMaxFrameNanos = 0L;
        testFrameSamples = 0;
        testSlowFrames = 0;
    }

    private void runAutomatedBoundaryStress(Minecraft client) {
        if (!testSeamScreenshotSaved || testBoundaryScreenshotSaved || client.player == null) return;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || client.player.getZ() > geometry.minWidthZ() + 8.0) return;
        if (!testBoundaryMetricsActive) {
            testBoundaryMetricsActive = true;
            resetFrameMetrics();
            RingWorldMod.LOGGER.info("[test] measuring rim-adjacent asynchronous chunk loading");
        }
        if (++testBoundaryTicks < 300) return;
        testBoundaryScreenshotSaved = true;
        testBoundaryMetricsActive = false;
        double averageFrameMs = testFrameSamples == 0 ? 0.0
                : testTotalFrameNanos / 1_000_000.0 / testFrameSamples;
        RingWorldMod.LOGGER.info("[test] boundary frame pacing samples={}, averageMs={}, maxMs={}, over50Ms={}",
                testFrameSamples, averageFrameMs, testMaxFrameNanos / 1_000_000.0, testSlowFrames);
        RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "ringworld-boundary.png", RingMinecraftClientAccess.mainRenderTarget(client), 1,
                message -> RingWorldMod.LOGGER.info("[test] boundary renderer screenshot: {}", message.getString()));
    }

    /** Captures fixed-sun tone states plus rainy-noon lightmap exposure. */
    private void runAutomatedSkyCycle(Minecraft client) {
        if (!testScreenshotSaved || testSkyCycleComplete || client.player == null
                || client.getConnection() == null) return;

        // Keep the celestial test camera deterministic even if the integrated
        // server finishes its boundary probe and sends its final playable pose.
        client.player.setXRot(-90.0F);
        client.player.setYRot(0.0F);

        if (!testSkyClockNormalized) {
            client.getConnection().sendCommand("tick rate 20");
            client.getConnection().sendCommand("gamerule advance_time false");
            testSkyClockNormalized = true;
            testSkySettleTicks = 0;
            RingWorldMod.LOGGER.info("[test] normalized and paused the sky capture clock");
            return;
        }
        if (!testSkyDayCommandSent) {
            client.getConnection().sendCommand("time set 6000");
            testSkyDayCommandSent = true;
            testSkySettleTicks = 0;
            RingWorldMod.LOGGER.info("[test] fixed-sun noon capture armed");
            return;
        }
        if (!testSkyDayScreenshotSaved) {
            if (++testSkySettleTicks < 80) return;
            testSkyDayScreenshotSaved = true;
            RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "ringworld-fixed-sun-day.png",
                    RingMinecraftClientAccess.mainRenderTarget(client), 1,
                    message -> RingWorldMod.LOGGER.info("[test] fixed-sun noon screenshot: {}", message.getString()));
            return;
        }
        if (!testSkyDuskCommandSent) {
            client.getConnection().sendCommand("time set 14008");
            testSkyDuskCommandSent = true;
            testSkySettleTicks = 0;
            RingWorldMod.LOGGER.info("[test] warm dimming dusk capture armed");
            return;
        }
        if (!testSkyDuskScreenshotSaved) {
            if (++testSkySettleTicks < 80) return;
            testSkyDuskScreenshotSaved = true;
            RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "ringworld-tone-dusk.png",
                    RingMinecraftClientAccess.mainRenderTarget(client), 1,
                    message -> RingWorldMod.LOGGER.info("[test] dusk tone screenshot: {}", message.getString()));
            return;
        }
        if (!testSkyNightCommandSent) {
            client.getConnection().sendCommand("time set 18000");
            testSkyNightCommandSent = true;
            testSkySettleTicks = 0;
            RingWorldMod.LOGGER.info("[test] cool dimming midnight capture armed");
            return;
        }
        if (!testSkyNightScreenshotSaved) {
            if (++testSkySettleTicks < 80) return;
            testSkyNightScreenshotSaved = true;
            RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "ringworld-tone-night.png",
                    RingMinecraftClientAccess.mainRenderTarget(client), 1,
                    message -> RingWorldMod.LOGGER.info(
                            "[test] midnight tone screenshot: {}", message.getString()));
            return;
        }
        if (!testSkyRainCommandSent) {
            client.getConnection().sendCommand("time set 6000");
            client.getConnection().sendCommand("weather rain");
            testSkyRainCommandSent = true;
            testSkySettleTicks = 0;
            RingWorldMod.LOGGER.info("[test] rainy-noon lightmap capture armed");
            return;
        }
        if (!testSkyRainScreenshotSaved) {
            if (++testSkySettleTicks < 100) return;
            testSkyRainScreenshotSaved = true;
            RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "ringworld-weather-rain.png",
                    RingMinecraftClientAccess.mainRenderTarget(client), 1,
                    message -> RingWorldMod.LOGGER.info(
                            "[test] rainy-noon screenshot: {}", message.getString()));
            return;
        }
        if (!testSkyClearCommandSent) {
            client.getConnection().sendCommand("weather clear");
            testSkyClearCommandSent = true;
            testSkySettleTicks = 0;
            RingWorldMod.LOGGER.info("[test] clear-weather restore armed after sky captures");
            return;
        }
        if (++testSkySettleTicks < 100) return;
        testSkyCycleComplete = true;
        testSkySettleTicks = 0;
        RingWorldMod.LOGGER.info("[test] clear weather settled after sky captures");
    }

    /**
     * Captures both projection extremes for the complete-ring surface:
     * tangentially along the intrinsic circumference and radially straight up.
     */
    private void runAutomatedRingVisibility(Minecraft client) {
        if (!testSkyCycleComplete || testRingVisibilityScreenshotSaved
                || client.player == null || client.getConnection() == null) return;
        var atlas = ClientRingState.terrainAtlas();
        if (atlas == null || !atlas.isComplete()) {
            if (!testRingVisibilityAtlasWaitLogged) {
                testRingVisibilityAtlasWaitLogged = true;
                RingWorldMod.LOGGER.info(
                        "[test] waiting for the complete terrain atlas before live/LOD capture");
            }
            testRingVisibilityAtlasWaitTicks++;
            if (!RingWorldConfig.load().pregenerateTerrainAtlas()
                    && testRingVisibilityAtlasWaitTicks >= 600) {
                testRingVisibilityScreenshotSaved = true;
                client.options.renderDistance().set(6);
                client.getConnection().sendCommand("gamerule advance_time true");
                RingWorldMod.LOGGER.warn(
                        "[test] skipped live/LOD capture after {} ticks because atlas "
                                + "pregeneration is disabled; continuing topology/rim probes at {}/{} cells",
                        testRingVisibilityAtlasWaitTicks,
                        atlas == null ? 0 : atlas.presentCount(),
                        atlas == null ? 0 : atlas.cellCount());
            }
            return;
        }
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;
        RingRenderProfile profile = RingRenderProfile.create(geometry,
                RingWorldConfig.load().testViewDistanceChunks() * 16.0);

        if (!testRingVisibilityTangentScreenshotSaved) {
            // Aim tangentially at the nominal live/LOD edge. This is the
            // direction in which a large cylinder most quickly runs through
            // Minecraft's ordinary level far plane.
            double targetDistance = profile.effectiveViewDistanceBlocks();
            double targetHeight = atlas.sample(
                    client.player.getX() + targetDistance,
                    client.player.getZ()).height();
            float targetPitch = (float)geometry.pitchDegreesToIntrinsic(
                    client.player.getY(), targetHeight, targetDistance, 0.0);
            client.player.setXRot(targetPitch);
            client.player.setYRot(90.0F);
            if (!testRingVisibilityCaptureArmed) {
                testRingVisibilityCaptureArmed = true;
                testSkySettleTicks = 0;
                client.getConnection().sendCommand("time set 6000");
                RingWorldMod.LOGGER.info(
                        "[test] {}-chunk tangent live/LOD capture armed at pitch={}, "
                                + "distance={}, surfaceY={}",
                        RingWorldConfig.load().testViewDistanceChunks(),
                        targetPitch, targetDistance, targetHeight);
                return;
            }
            if (++testSkySettleTicks < 80) return;
            testRingVisibilityTangentScreenshotSaved = true;
            testSkySettleTicks = 0;
            RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "ringworld-visible-arch.png",
                    RingMinecraftClientAccess.mainRenderTarget(client), 1,
                    message -> RingWorldMod.LOGGER.info(
                            "[test] tangent complete-ring screenshot: {}", message.getString()));
            return;
        }

        // The radial view crosses the largest physical diameter and previously
        // appeared to render farther than the tangent view. Keep it as a
        // separate acceptance capture instead of assuming one camera direction
        // proves that the whole cylinder survives projection.
        client.player.setXRot(-90.0F);
        client.player.setYRot(90.0F);
        if (!testRingVisibilityUpCaptureArmed) {
            testRingVisibilityUpCaptureArmed = true;
            testSkySettleTicks = 0;
            RingWorldMod.LOGGER.info(
                    "[test] radial-up complete-ring capture armed; diameter={} blocks",
                    geometry.radius() * 2.0);
            return;
        }
        if (++testSkySettleTicks < 80) return;
        testRingVisibilityScreenshotSaved = true;
        // The remainder of the full multiplayer regression is intentionally
        // run at the lighter stable profile. The high-distance path has now
        // been rendered and captured; keeping every chunk for two fast circuits
        // would test disk generation speed instead of seam playability.
        client.options.renderDistance().set(6);
        client.getConnection().sendCommand("gamerule advance_time true");
        RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, "ringworld-visible-up.png",
                RingMinecraftClientAccess.mainRenderTarget(client), 1,
                message -> RingWorldMod.LOGGER.info(
                        "[test] radial-up complete-ring screenshot: {}", message.getString()));
    }

    private void recordTestFrame() {
        if (!RingWorldConfig.load().testMode() || !testScreenshotSaved
                || (testSeamScreenshotSaved && !testBoundaryMetricsActive)) return;
        long now = System.nanoTime();
        if (testLastFrameNanos != 0L) {
            long elapsed = now - testLastFrameNanos;
            testTotalFrameNanos += elapsed;
            testMaxFrameNanos = Math.max(testMaxFrameNanos, elapsed);
            testFrameSamples++;
            if (elapsed > 50_000_000L) testSlowFrames++;
        }
        testLastFrameNanos = now;
    }
}
