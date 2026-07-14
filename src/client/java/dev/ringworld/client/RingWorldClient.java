package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.client.render.RingHandoffFogRenderer;
import dev.ringworld.net.RingSettingsPayload;
import dev.ringworld.net.RingSettingsAckPayload;
import dev.ringworld.net.RingTerrainAtlasMetadataPayload;
import dev.ringworld.net.RingTerrainAtlasRequestPayload;
import dev.ringworld.net.RingTerrainAtlasTilePayload;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingGeometry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.debug.DebugHudEntries;
import net.minecraft.client.gui.hud.debug.DebugHudEntryVisibility;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.option.InactivityFpsLimit;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.world.chunk.ChunkStatus;

/** Client entrypoint. Rendering and pose mixins consume ClientRingState. */
public final class RingWorldClient implements ClientModInitializer {
    /** Repeatable terrain makes visual atlas regressions comparable between runs. */
    private static final String AUTOMATED_TEST_SEED = "-2162056627494116761";
    private final MultiplayerTestClient multiplayerTest = new MultiplayerTestClient();
    private boolean testScreenOpened;
    private boolean testWorldStarted;
    private boolean testPerformanceProfileApplied;
    private int testWorldTicks;
    private boolean testScreenshotSaved;
    private boolean testSeamMoveSent;
    private boolean testSeamScreenshotSaved;
    private boolean testSeamBlockActionSent;
    private int testSeamPrefetchTicks;
    private int testSeamSettleTicks;
    private boolean testSeamEntityProjected;
    private int testSecondCircuitPrefetchTicks;
    private int testSecondCircuitWaitTicks;
    private int testSecondCircuitSettleTicks;
    private boolean testSecondCircuitScreenshotSaved;
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
    private boolean testRingVisibilityCaptureArmed;
    private boolean testRingVisibilityScreenshotSaved;
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
        ClientPlayNetworking.registerGlobalReceiver(RingSettingsPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.formatVersion() != dev.ringworld.world.RingWorldSettings.FORMAT_VERSION) {
                        var handler = context.client().getNetworkHandler();
                        if (handler != null) {
                            handler.getConnection().disconnect(Text.literal(
                                    "Incompatible RingWorld format: server=" + payload.formatVersion()
                                            + ", client=" + dev.ringworld.world.RingWorldSettings.FORMAT_VERSION));
                        }
                        return;
                    }
                    ClientRingState.set(new RingGeometry(payload.width(), payload.circumference()));
                    if (!ClientPlayNetworking.canSend(RingSettingsAckPayload.ID)) {
                        var handler = context.client().getNetworkHandler();
                        if (handler != null) {
                            handler.getConnection().disconnect(Text.literal(
                                    "Server does not support the RingWorld settings acknowledgement."));
                        }
                        return;
                    }
                    ClientPlayNetworking.send(new RingSettingsAckPayload(
                            payload.width(), payload.circumference(), payload.formatVersion()));
                }));
        ClientPlayNetworking.registerGlobalReceiver(RingTerrainAtlasMetadataPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    boolean cacheComplete = ClientRingState.installTerrainAtlas(payload);
                    if (!ClientPlayNetworking.canSend(RingTerrainAtlasRequestPayload.ID)) return;
                    ClientPlayNetworking.send(new RingTerrainAtlasRequestPayload(
                            payload.worldHash(), cacheComplete));
                }));
        ClientPlayNetworking.registerGlobalReceiver(RingTerrainAtlasTilePayload.ID, (payload, context) ->
                context.client().execute(() -> ClientRingState.applyTerrainAtlasTile(
                        payload.worldHash(), payload.tileX(), payload.tileZ(), payload.data())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientRingState.clear();
            RingHandoffFogRenderer.clear();
        });
        WorldRenderEvents.END_MAIN.register(context -> {
            RingHandoffFogRenderer.render(context);
            recordTestFrame();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) ClientRingState.updateCameraPosition(client.player.getX());
            ClientRingState.saveTerrainAtlasIfDue(false);
            if (multiplayerTest.tick(client)) return;
            saveDiagnosticJoinScreenshot(client);
            startAutomatedTestWorld(client);
        });
    }

    /**
     * Opt-in development capture used to verify a real saved-world join
     * without enabling the destructive automated traversal sequence.
     */
    private void saveDiagnosticJoinScreenshot(MinecraftClient client) {
        if (!Boolean.getBoolean("ringworld.captureJoinFrame")
                || diagnosticJoinScreenshotSaved || client.player == null) return;
        if (client.currentScreen instanceof GameMenuScreen) client.setScreen(null);
        if (client.currentScreen != null || ++diagnosticJoinTicks < 160) return;
        if (!client.worldRenderer.isTerrainRenderComplete() && diagnosticJoinTicks < 600) return;

        diagnosticJoinScreenshotSaved = true;
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "ringworld-join-diagnostic.png",
                client.getFramebuffer(), 1,
                message -> RingWorldMod.LOGGER.info("[diagnostic] join screenshot: {}", message.getString()));
    }

    private void startAutomatedTestWorld(MinecraftClient client) {
        if (!RingWorldConfig.load().testMode()) return;
        if (!testPerformanceProfileApplied) {
            // A representative, stable local profile. Production play still
            // follows the user's own options because this is test-mode only.
            // Exercise the same long-range path used by the current 28-chunk
            // play profile; this catches flat-frustum regressions that a short
            // smoke-test distance cannot reveal.
            client.options.getViewDistance().setValue(28);
            client.options.getSimulationDistance().setValue(5);
            client.options.getInactivityFpsLimit().setValue(InactivityFpsLimit.MINIMIZED);
            client.options.pauseOnLostFocus = false;
            client.debugHudEntryList.setEntryVisibility(
                    DebugHudEntries.PLAYER_POSITION, DebugHudEntryVisibility.ALWAYS_ON);
            testPerformanceProfileApplied = true;
        }
        if (client.world != null) {
            // The development client can lose foreground focus while Gradle
            // hands it off to the desktop app. Do not let that one-time pause
            // turn the captured smoke-test frame into a menu screenshot.
            if (client.currentScreen instanceof GameMenuScreen) client.setScreen(null);
            if (client.currentScreen != null) return;
            saveAutomatedScreenshot(client);
            runAutomatedSeamTraversal(client);
            runAutomatedSecondCircuit(client);
            runAutomatedBoundaryStress(client);
            runAutomatedSkyCycle(client);
            runAutomatedRingVisibility(client);
            return;
        }
        if (testWorldStarted) return;
        if (!testScreenOpened) {
            CreateWorldScreen.show(client, () -> testScreenOpened = false);
            testScreenOpened = true;
            return;
        }
        if (client.currentScreen instanceof CreateWorldScreen screen) {
            WorldCreator creator = screen.getWorldCreator();
            creator.setWorldName("RingWorld Automated Test");
            creator.setGameMode(WorldCreator.Mode.CREATIVE);
            creator.setCheatsEnabled(true);
            creator.setSeed(AUTOMATED_TEST_SEED);
            RingWorldMod.LOGGER.info("[test] creating a creative 100x20-chunk test world");
            ((CreateWorldScreenInvoker) screen).ringworld$createLevel();
            testWorldStarted = true;
        }
    }

    /**
     * Gives chunk meshing time to settle, then captures the actual rendered
     * framebuffer. This makes the local smoke test inspectable without manual
     * input and, importantly, exercises the curved terrain shader in-game.
     */
    private void saveAutomatedScreenshot(MinecraftClient client) {
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
            client.player.setPosition(0.5, Math.max(120.0, client.player.getY()), 0.5);
            client.player.setYaw(90.0f);
            client.player.setPitch(22.0f);
            testCameraPositioned = true;
        }
        if (testWorldTicks < 640) return;
        if (!client.worldRenderer.isTerrainRenderComplete() && testWorldTicks < 1_200) return;
        testScreenshotSaved = true;
        resetFrameMetrics();
        RingWorldMod.LOGGER.info("[test] renderer camera: x={}, y={}, z={}, yaw={}, pitch={}",
                client.player.getX(), client.player.getY(), client.player.getZ(),
                client.player.getYaw(), client.player.getPitch());
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "ringworld-automated.png", client.getFramebuffer(), 1,
                message -> RingWorldMod.LOGGER.info("[test] renderer screenshot: {}", message.getString()));
    }

    /** Drives one small, packet-backed crossing after the server arms the seam test. */
    private void runAutomatedSeamTraversal(MinecraftClient client) {
        if (!testScreenshotSaved || !testRingVisibilityScreenshotSaved || client.player == null) return;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;
        if (!testSeamMoveSent && client.player.getX() >= geometry.circumferenceBlocks() - 8.5) {
            if (!testSeamEntityProjected) {
                for (var entity : client.world.getEntities()) {
                    if (entity instanceof ItemEntity item
                            && item.getStack().isOf(Items.DIAMOND)
                            && item.getX() > geometry.circumferenceBlocks()) {
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
                    || (!client.worldRenderer.isTerrainRenderComplete() && testSeamPrefetchTicks < 300)) return;
            // Do not count the deliberate server setup teleport as the short
            // packet-backed seam crossing this test is about to exercise.
            ClientRingState.resetCameraContinuity(client.player.getX());
            testSeamStartYaw = client.player.getYaw();
            testSeamStartPitch = client.player.getPitch();
            testSeamMoveSent = true;
            RingWorldMod.LOGGER.info("[test] beginning smooth client movement across the circumference seam at yaw={}, pitch={}",
                    testSeamStartYaw, testSeamStartPitch);
            return;
        }
        if (testSeamMoveSent && client.player.getX() < geometry.circumferenceBlocks() + 2.0) {
            double nextX = Math.min(geometry.circumferenceBlocks() + 2.0, client.player.getX() + 0.25);
            client.player.setPosition(nextX, client.player.getY(), client.player.getZ());
            return;
        }
        if (testSeamMoveSent && !testSeamScreenshotSaved
                && client.player.getX() >= geometry.circumferenceBlocks()
                && ClientRingState.cameraSeamCrossings() > 0) {
            if (!testSeamBlockActionSent && client.interactionManager != null) {
                BlockPos logicalTestBlock = new BlockPos(
                        geometry.circumferenceBlocks() + 2, 119, 0);
                if (!client.world.getBlockState(logicalTestBlock).isOf(Blocks.GOLD_BLOCK)) return;
                client.interactionManager.attackBlock(logicalTestBlock, Direction.UP);
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
                    client.player.getYaw(), client.player.getYaw() - testSeamStartYaw,
                    client.player.getPitch(), client.player.getPitch() - testSeamStartPitch,
                    ClientRingState.seamCorrectionPackets());
            ScreenshotRecorder.saveScreenshot(client.runDirectory, "ringworld-seam.png", client.getFramebuffer(), 1,
                    message -> RingWorldMod.LOGGER.info("[test] seam renderer screenshot: {}", message.getString()));
            client.debugHudEntryList.setEntryVisibility(
                    DebugHudEntries.PLAYER_POSITION, DebugHudEntryVisibility.IN_OVERLAY);
        }
    }

    /**
     * Traverses the rest of the small development circumference and crosses
     * the same physical seam a second time. The fast middle section pauses
     * whenever terrain meshing falls behind; the actual seam approach remains
     * the same quarter-block-per-tick movement used by the first crossing.
     */
    private void runAutomatedSecondCircuit(MinecraftClient client) {
        if (!testSeamScreenshotSaved || testSecondCircuitScreenshotSaved || client.player == null) return;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;

        double approachX = geometry.circumferenceBlocks() * 2.0 - 8.0;
        double targetX = geometry.circumferenceBlocks() * 2.0 + 2.0;
        if (client.player.getX() < approachX) {
            int nextChunkX = ((int)Math.floor(client.player.getX() + 48.0)) >> 4;
            int chunkZ = ((int)Math.floor(client.player.getZ())) >> 4;
            if (client.world.getChunkManager().getChunk(nextChunkX, chunkZ, ChunkStatus.FULL, false) == null) {
                if (++testSecondCircuitWaitTicks % 200 == 0) {
                    RingWorldMod.LOGGER.info("[test] second-circuit streaming wait at x={}, requested chunk={},{}",
                            client.player.getX(), nextChunkX, chunkZ);
                }
                return;
            }
            testSecondCircuitWaitTicks = 0;
            client.player.setPosition(Math.min(approachX, client.player.getX() + 4.0),
                    client.player.getY(), client.player.getZ());
            return;
        }
        if (client.player.getX() < targetX) {
            if (++testSecondCircuitPrefetchTicks < 100
                    || (!client.worldRenderer.isTerrainRenderComplete() && testSecondCircuitPrefetchTicks < 400)) return;
            client.player.setPosition(Math.min(targetX, client.player.getX() + 0.25),
                    client.player.getY(), client.player.getZ());
            return;
        }
        if (++testSecondCircuitSettleTicks < 60) return;

        double projectedMovingEntityX = Double.NaN;
        for (var entity : client.world.getEntities()) {
            if (entity instanceof ItemEntity item && item.getStack().isOf(Items.EMERALD)) {
                projectedMovingEntityX = item.getX();
                break;
            }
        }
        testSecondCircuitScreenshotSaved = true;
        RingWorldMod.LOGGER.info("[test] second seam landed at presentationX={}, chart={}, client seam crossings={}",
                client.player.getX(), ClientRingState.cameraPosition().chartIndex(),
                ClientRingState.cameraSeamCrossings());
        RingWorldMod.LOGGER.info("[test] second seam camera yaw={} (delta={}), pitch={} (delta={}), correction packets={}",
                client.player.getYaw(), client.player.getYaw() - testSeamStartYaw,
                client.player.getPitch(), client.player.getPitch() - testSeamStartPitch,
                ClientRingState.seamCorrectionPackets());
        RingWorldMod.LOGGER.info("[test] moving entity projected into second client chart={}, x={}",
                projectedMovingEntityX > geometry.circumferenceBlocks() * 2.0, projectedMovingEntityX);
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "ringworld-second-wrap.png", client.getFramebuffer(), 1,
                message -> RingWorldMod.LOGGER.info("[test] second-wrap renderer screenshot: {}", message.getString()));
    }

    private void resetFrameMetrics() {
        testLastFrameNanos = 0L;
        testTotalFrameNanos = 0L;
        testMaxFrameNanos = 0L;
        testFrameSamples = 0;
        testSlowFrames = 0;
    }

    private void runAutomatedBoundaryStress(MinecraftClient client) {
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
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "ringworld-boundary.png", client.getFramebuffer(), 1,
                message -> RingWorldMod.LOGGER.info("[test] boundary renderer screenshot: {}", message.getString()));
    }

    /** Captures the fixed sun at noon and its shadow-panel eclipse at midnight. */
    private void runAutomatedSkyCycle(MinecraftClient client) {
        if (!testScreenshotSaved || testSkyNightScreenshotSaved || client.player == null
                || client.getNetworkHandler() == null) return;

        // Keep the celestial test camera deterministic even if the integrated
        // server finishes its boundary probe and sends its final playable pose.
        client.player.setPitch(-90.0F);
        client.player.setYaw(0.0F);

        if (!testSkyClockNormalized) {
            client.getNetworkHandler().sendChatCommand("tick rate 20");
            client.getNetworkHandler().sendChatCommand("gamerule advance_time false");
            testSkyClockNormalized = true;
            testSkySettleTicks = 0;
            RingWorldMod.LOGGER.info("[test] normalized and paused the sky capture clock");
            return;
        }
        if (!testSkyDayCommandSent) {
            client.getNetworkHandler().sendChatCommand("time set 6000");
            testSkyDayCommandSent = true;
            testSkySettleTicks = 0;
            RingWorldMod.LOGGER.info("[test] fixed-sun noon capture armed");
            return;
        }
        if (!testSkyDayScreenshotSaved) {
            if (++testSkySettleTicks < 80) return;
            testSkyDayScreenshotSaved = true;
            ScreenshotRecorder.saveScreenshot(client.runDirectory, "ringworld-fixed-sun-day.png",
                    client.getFramebuffer(), 1,
                    message -> RingWorldMod.LOGGER.info("[test] fixed-sun noon screenshot: {}", message.getString()));
            return;
        }
        if (!testSkyDuskCommandSent) {
            client.getNetworkHandler().sendChatCommand("time set 14008");
            testSkyDuskCommandSent = true;
            testSkySettleTicks = 0;
            RingWorldMod.LOGGER.info("[test] moving shadow-slab dusk capture armed");
            return;
        }
        if (!testSkyDuskScreenshotSaved) {
            if (++testSkySettleTicks < 80) return;
            testSkyDuskScreenshotSaved = true;
            ScreenshotRecorder.saveScreenshot(client.runDirectory, "ringworld-shadow-dusk.png",
                    client.getFramebuffer(), 1,
                    message -> RingWorldMod.LOGGER.info("[test] shadow-slab dusk screenshot: {}", message.getString()));
            return;
        }
        if (!testSkyNightCommandSent) {
            client.getNetworkHandler().sendChatCommand("time set 18000");
            testSkyNightCommandSent = true;
            testSkySettleTicks = 0;
            RingWorldMod.LOGGER.info("[test] shadow-panel midnight capture armed");
            return;
        }
        if (++testSkySettleTicks < 80) return;
        testSkyNightScreenshotSaved = true;
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "ringworld-shadow-night.png",
                client.getFramebuffer(), 1,
                message -> RingWorldMod.LOGGER.info("[test] shadow-panel midnight screenshot: {}", message.getString()));
    }

    /** Captures the full atmospheric Arch from one apparent base toward the zenith. */
    private void runAutomatedRingVisibility(MinecraftClient client) {
        if (!testSkyNightScreenshotSaved || testRingVisibilityScreenshotSaved
                || client.player == null || client.getNetworkHandler() == null) return;
        // At the 28-chunk edge of the 100-chunk test ring, real terrain has
        // curved roughly fifty degrees above the flat horizon. Looking along
        // that rise directly exercises the formerly incorrect flat frustum.
        client.player.setPitch(-52.0F);
        client.player.setYaw(90.0F);
        if (!testRingVisibilityCaptureArmed) {
            testRingVisibilityCaptureArmed = true;
            testSkySettleTicks = 0;
            client.getNetworkHandler().sendChatCommand("time set 6000");
            RingWorldMod.LOGGER.info("[test] 28-chunk upward terrain/Arch capture armed");
            return;
        }
        if (++testSkySettleTicks < 80) return;
        testRingVisibilityScreenshotSaved = true;
        // The remainder of the full multiplayer regression is intentionally
        // run at the lighter stable profile. The high-distance path has now
        // been rendered and captured; keeping every chunk for two fast circuits
        // would test disk generation speed instead of seam playability.
        client.options.getViewDistance().setValue(6);
        client.getNetworkHandler().sendChatCommand("gamerule advance_time true");
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "ringworld-visible-arch.png",
                client.getFramebuffer(), 1,
                message -> RingWorldMod.LOGGER.info("[test] visible Arch screenshot: {}", message.getString()));
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
