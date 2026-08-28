package dev.ringworld.platform.neoforge.compat.create610;

import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.mounted.MountedContraption;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.ringworld.RingWorldMod;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.compat.ClientWorldLifecycle;
import dev.ringworld.client.compat.Screenshot;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.platform.neoforge.compat.create610.mixin.RingCreate610MixinPlugin;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Isolated exact-tuple graphical and durable gameplay qualification fixture. */
public final class RingCreate610ClientFixture {
    public static final String ENABLE_PROPERTY = "ringworld.createCompatClient";
    public static final String MODE_PROPERTY = "ringworld.createCompatClientMode";
    private static final String WORLD_NAME = "RingWorld Create Compatibility";
    private static final int Y = 120;
    private static final int TIMEOUT_TICKS = 3_600;
    private static final RingCreate610ClientFixture INSTANCE = new RingCreate610ClientFixture();

    private boolean worldScreenOpened;
    private boolean worldStarted;
    private boolean setupRequested;
    private volatile boolean setupReady;
    private volatile String asynchronousFailure;
    private volatile boolean forwardVerified;
    private volatile boolean reverseVerified;
    private volatile boolean forwardVerificationPending;
    private volatile boolean reverseVerificationPending;
    private volatile int forwardVerificationAttempts;
    private volatile int reverseVerificationAttempts;
    private volatile boolean forwardSecondClickReady;
    private volatile boolean reverseSecondClickReady;
    private volatile boolean connectorReadyCheckPending;
    private volatile boolean transferredItemFrozen;
    private volatile boolean durableReloadVerified;
    private volatile boolean contraptionRemoved;
    private boolean contraptionRemovalRequested;
    private boolean offContraptionRequested;
    private volatile boolean offContraptionReady;
    private boolean offLowCaptured;
    private volatile UUID movingContraptionId;
    private volatile UUID highRouteContraptionId;
    private volatile UUID highRouteVehicleId;
    private volatile UUID lowRouteContraptionId;
    private volatile UUID lowRouteVehicleId;
    private RouteBaseline highRouteBaseline;
    private RouteBaseline lowRouteBaseline;
    private boolean routeMovementRequested;
    private boolean routeCrossed;
    private boolean highRouteCleanupRequested;
    private volatile boolean highRouteCleanupComplete;
    private boolean lowRouteSetupRequested;
    private volatile boolean lowRouteSetupReady;
    private int routePhase;
    private int routePhaseTicks;
    private int ticks;
    private int stage;
    private int stageTicks;
    private boolean reopenRequested;
    private boolean disconnectCleared;
    private boolean forwardFirstClicked;
    private boolean forwardSecondClicked;
    private boolean reverseFirstClicked;
    private boolean reverseSecondClicked;
    private boolean highTeleportRequested;
    private boolean reopenedHighTeleportRequested;
    private boolean chartTransitionPending;
    private double chartTransitionTargetX;
    private int highChartHop;
    private int lowChartHop;
    private boolean lowTransitionStarted;
    private long renderedFrames;
    private long framesOver50Millis;
    private long maxFrameNanos;
    private long lastFrameNanos;

    private RingCreate610ClientFixture() { }

    public static RingCreate610ClientFixture instance() { return INSTANCE; }

    public boolean startWorldIfEnabled(Minecraft client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || client.level != null || worldStarted) return false;
        if (offMode()) {
            if (!(client.screen instanceof TitleScreen)) return true;
            RingCreate610ClientDiagnostics.reset();
            worldStarted = true;
            client.createWorldOpenFlows().openWorld(WORLD_NAME,
                    () -> finish(client, false, "OFF copied-world open cancelled"));
            return true;
        }
        if (!worldScreenOpened) {
            if (!(client.screen instanceof TitleScreen)) return true;
            CreateWorldScreen.openFresh(client, client.screen);
            worldScreenOpened = true;
            return true;
        }
        if (client.screen instanceof CreateWorldScreen screen) {
            WorldCreationUiState creator = screen.getUiState();
            creator.setName(WORLD_NAME);
            creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            creator.setAllowCommands(true);
            creator.setSeed("-2162056627494116761");
            RingCreate610ClientDiagnostics.reset();
            ((CreateWorldScreenInvoker) screen).ringworld$createLevel();
            worldStarted = true;
        }
        return true;
    }

    public boolean tick(Minecraft client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return false;
        if (startWorldIfEnabled(client)) return true;
        client.options.pauseOnLostFocus = false;
        if (client.screen instanceof PauseScreen) client.setScreen(null);
        ticks++;
        if (ticks > TIMEOUT_TICKS) return finish(client, false, "timeout stage=" + stage);
        if (asynchronousFailure != null) return finish(client, false, asynchronousFailure);
        if (client.player == null || client.level == null || client.screen != null) {
            if (!offMode() && stage == 9) waitForDisconnect(client);
            else if (!offMode() && stage == 10) reopenWorld(client);
            stageTicks++;
            return true;
        }
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || geometry.circumferenceBlocks() != 2048) return true;
        if (chartTransitionPending) {
            if (Math.abs(client.player.getX() - chartTransitionTargetX) <= 4.0) {
                chartTransitionPending = false;
                ClientRingState.updateCameraPosition(client.player.getX());
            }
        } else {
            ClientRingState.updateCameraPosition(client.player.getX());
        }
        return offMode() ? tickOff(client, geometry) : tickDefault(client, geometry);
    }

    public void frameRendered() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            long duration = now - lastFrameNanos;
            maxFrameNanos = Math.max(maxFrameNanos, duration);
            if (duration > 50_000_000L) framesOver50Millis++;
        }
        lastFrameNanos = now;
        renderedFrames++;
    }

    private boolean tickDefault(Minecraft client, RingGeometry geometry) {
        if (setupReady && stage >= 1 && stage <= 6) requestFreezeTransferredItem(client);
        switch (stage) {
            case 0 -> requestSetup(client);
            case 1 -> runForwardFirstClick(client, geometry);
            case 2 -> runForwardPreviewAndSecondClick(client, geometry);
            case 3 -> waitForwardAndMoveLow(client, geometry);
            case 4 -> runReverseFirstClick(client);
            case 5 -> runReversePreviewAndSecondClick(client);
            case 6 -> waitReverseTransferAndCaptureHigh(client, geometry);
            case 7 -> sweepContraptionAndCapture(client, geometry);
            case 8 -> requestDisconnect(client);
            case 9 -> waitForDisconnect(client);
            case 10 -> reopenWorld(client);
            case 11 -> verifyReopenedHighChart(client, geometry);
            case 12 -> verifyReopenedLowChartAndFinish(client, geometry);
            default -> { return finish(client, false, "invalid stage=" + stage); }
        }
        stageTicks++;
        return true;
    }

    private boolean tickOff(Minecraft client, RingGeometry geometry) {
        stageTicks++;
        if (stage == 0) {
            if (!offContraptionReady) {
                requestOffContraption(client);
                return true;
            }
            if (!highTeleportRequested) {
                highTeleportRequested = true;
                highChartHop = 0;
            }
            if (!driveToHighChart(client, geometry)) return true;
            if (stageTicks < 80 || !client.levelRenderer.hasRenderedAllSections()) return true;
            if (BackendManager.isBackendOn()) return finish(client, false, "OFF launch selected a live backend");
            if (RingCreate610MixinPlugin.appliedServerMixinCount() != 4
                    || RingCreate610MixinPlugin.appliedClientMixinCount() != 4) {
                return finish(client, false, "OFF mixin-counts server="
                        + RingCreate610MixinPlugin.appliedServerMixinCount() + " client="
                        + RingCreate610MixinPlugin.appliedClientMixinCount());
            }
            if (RingCreate610ClientDiagnostics.snapshot().curvedEmbeddingTransforms() != 0) {
                return finish(client, false, "OFF path invoked ContraptionVisual embedding");
            }
            requestDurableReloadVerification(client);
            if (!durableReloadVerified || !verifyClientControllerChart(client, geometry, true)) return true;
            if (!captureWithEntityProof(client, "ringworld-create-off-high-translucent",
                    movingContraptionId, "off-vanilla", "none", "high",
                    "opaque+translucent+static")) return true;
            lowChartHop = 0;
            advance(1);
            return true;
        }
        if (!driveToLowChart(client, geometry) || stageTicks < 80
                || !verifyClientControllerChart(client, geometry, false)) return true;
        if (!offLowCaptured) {
            offLowCaptured = true;
            if (!captureWithEntityProof(client, "ringworld-create-off-low-opaque",
                    movingContraptionId, "off-vanilla", "none", "low",
                    "opaque+translucent+static")) return true;
        }
        if (!contraptionRemoved) {
            requestContraptionRemoval(client);
            return true;
        }
        if (stageTicks < 280) return true;
        return finish(client, true, summary("off vanillaFallback=true flywheelEmbedding=zero durable=true"));
    }

    private void requestOffContraption(Minecraft client) {
        if (offContraptionRequested || client.getSingleplayerServer() == null) return;
        offContraptionRequested = true;
        var server = client.getSingleplayerServer();
        server.execute(() -> runAsync("OFF contraption setup", () -> {
            RingGeometry geometry = RingWorldServer.geometryFor(server.overworld());
            movingContraptionId = addContraption(server.overworld(), geometry);
            offContraptionReady = true;
            RingWorldMod.LOGGER.info("[create-compat-client] OFF vanilla-path contraption ready");
        }));
    }

    private void requestSetup(Minecraft client) {
        if (setupRequested) return;
        setupRequested = true;
        chartTransitionPending = true;
        chartTransitionTargetX = 2047.0;
        ClientRingState.resetCameraContinuity(chartTransitionTargetX);
        var server = client.getSingleplayerServer();
        UUID playerId = client.player.getUUID();
        server.execute(() -> runAsync("initial setup", () -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !(player.level() instanceof ServerLevel level))
                throw new IllegalStateException("missing integrated player");
            RingGeometry geometry = RingWorldServer.geometryFor(level);
            RingCreate610ServerFixture.prepareClientQualification(level, player);
            MountedIds mounted = addMountedContraption(level, geometry, true, 88);
            highRouteContraptionId = mounted.contraptionId();
            highRouteVehicleId = mounted.vehicleId();
            player.teleportTo(level, geometry.circumferenceBlocks() - 1.0,
                    120.0, 76.5, Set.of(), 90.0F, 15.0F);
            setupReady = true;
            RingWorldMod.LOGGER.info("[create-compat-client] server qualification fixture ready");
        }));
        advance(1);
    }

    private void runForwardFirstClick(Minecraft client, RingGeometry geometry) {
        BlockPos first = new BlockPos(geometry.circumferenceBlocks() - 3, Y,
                RingCreate610ServerFixture.FORWARD_CLICK_Z);
        if (stageTicks % 100 == 0) {
            RingWorldMod.LOGGER.info(
                    "[create-compat-client] stage1 setup={} playerX={} chartPending={} "
                            + "firstState={} held={}",
                    setupReady, client.player.getX(), chartTransitionPending,
                    client.level.getBlockState(first).getBlock(),
                    client.player.getMainHandItem());
        }
        if (!setupReady || !driveToHighChart(client, geometry)) return;
        if (stageTicks < 100) return;
        if (!client.level.getBlockState(first).hasProperty(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)) return;
        if (!forwardFirstClicked) {
            forwardFirstClicked = click(client, first);
            if (!forwardFirstClicked) {
                finish(client, false, "forward first client prediction did not consume action");
                return;
            }
            stageTicks = 0;
            RingWorldMod.LOGGER.info("[create-compat-client] real click forward first SUCCESS pos={}", first);
        }
        if (stageTicks >= 12) advance(2);
    }

    private void runForwardPreviewAndSecondClick(Minecraft client, RingGeometry geometry) {
        BlockPos first = new BlockPos(geometry.circumferenceBlocks() - 3, Y,
                RingCreate610ServerFixture.FORWARD_CLICK_Z);
        BlockPos second = new BlockPos(geometry.circumferenceBlocks() + 1, Y,
                RingCreate610ServerFixture.FORWARD_CLICK_Z);
        client.hitResult = hit(second);
        RingCreate610ClientDiagnostics.Snapshot diagnostics = RingCreate610ClientDiagnostics.snapshot();
        if (stageTicks % 100 == 0) {
            RingWorldMod.LOGGER.info(
                    "[create-compat-client] stage2 playerX={} secondState={} components={} preview={}",
                    client.player.getX(), client.level.getBlockState(second).getBlock(),
                    client.player.getMainHandItem().getComponents(), diagnostics);
            logServerClickState(client, RingCreate610ServerFixture.FORWARD_CLICK_Z,
                    "forward first-click state");
        }
        if (!second.equals(diagnostics.previewSecond()) || !first.equals(diagnostics.previewFirst())
                || !diagnostics.previewCanConnect()) return;
        if (!forwardSecondClickReady) {
            requestSecondClickReady(client, true);
            return;
        }
        if (!forwardSecondClicked) {
            forwardSecondClicked = click(client, second);
            if (!forwardSecondClicked) {
                finish(client, false, "forward second client prediction did not consume action");
                return;
            }
            verifyClickedBeltAsync(client, RingCreate610ServerFixture.FORWARD_CLICK_Z,
                    "forward real-click belt", true);
            RingWorldMod.LOGGER.info(
                    "[create-compat-client] preview forward first={} second={} canConnect=true; second click SUCCESS",
                    first, second);
        }
        advance(3);
    }

    private void waitForwardAndMoveLow(Minecraft client, RingGeometry geometry) {
        if (!forwardVerified && stageTicks % 5 == 0) {
            verifyClickedBeltAsync(client, RingCreate610ServerFixture.FORWARD_CLICK_Z,
                    "forward real-click belt", true);
        }
        if (!forwardVerified) return;
        if (!lowTransitionStarted) {
            lowTransitionStarted = true;
            lowChartHop = 0;
        }
        if (driveToLowChart(client, geometry)) advance(4);
    }

    private void runReverseFirstClick(Minecraft client) {
        if (Math.abs(client.player.getX() - 1.5) > 4.0 || stageTicks < 12) return;
        BlockPos first = new BlockPos(1, Y, RingCreate610ServerFixture.REVERSE_CLICK_Z);
        if (stageTicks < 100) return;
        if (!reverseFirstClicked) {
            reverseFirstClicked = click(client, first);
            if (!reverseFirstClicked) {
                finish(client, false, "reverse first client prediction did not consume action");
                return;
            }
            stageTicks = 0;
            RingWorldMod.LOGGER.info("[create-compat-client] real click reverse first SUCCESS pos={}", first);
        }
        if (stageTicks >= 24) advance(5);
    }

    private void runReversePreviewAndSecondClick(Minecraft client) {
        BlockPos first = new BlockPos(1, Y, RingCreate610ServerFixture.REVERSE_CLICK_Z);
        BlockPos second = new BlockPos(-3, Y, RingCreate610ServerFixture.REVERSE_CLICK_Z);
        client.hitResult = hit(second);
        RingCreate610ClientDiagnostics.Snapshot diagnostics = RingCreate610ClientDiagnostics.snapshot();
        if (!second.equals(diagnostics.previewSecond()) || !first.equals(diagnostics.previewFirst())
                || !diagnostics.previewCanConnect()) return;
        if (!reverseSecondClickReady) {
            requestSecondClickReady(client, false);
            return;
        }
        if (!reverseSecondClicked) {
            reverseSecondClicked = click(client, second);
            if (!reverseSecondClicked) {
                finish(client, false, "reverse second client prediction did not consume action");
                return;
            }
            verifyClickedBeltAsync(client, RingCreate610ServerFixture.REVERSE_CLICK_Z,
                    "reverse real-click belt", false);
            RingWorldMod.LOGGER.info(
                    "[create-compat-client] preview reverse first={} second={} canConnect=true; second click SUCCESS",
                    first, second);
        }
        advance(6);
    }

    private void waitReverseTransferAndCaptureHigh(Minecraft client, RingGeometry geometry) {
        if (!reverseVerified && stageTicks % 5 == 0) {
            verifyClickedBeltAsync(client, RingCreate610ServerFixture.REVERSE_CLICK_Z,
                    "reverse real-click belt", false);
        }
        if (!reverseVerified) return;
        requestFreezeTransferredItem(client);
        if (!transferredItemFrozen) return;
        if (!highTeleportRequested) {
            highTeleportRequested = true;
            highChartHop = 0;
        }
        if (!driveToHighChart(client, geometry)) return;
        if (stageTicks < 80 || !client.levelRenderer.hasRenderedAllSections()
                || !verifyClientControllerChart(client, geometry, true)) return;
        if (!captureWithEntityProof(client,
                "ringworld-create-default-high-opaque-translucent",
                highRouteContraptionId, "high-static", "none", "high",
                "opaque+translucent+static")) return;
        advance(7);
    }

    private void sweepContraptionAndCapture(Minecraft client, RingGeometry geometry) {
        routePhaseTicks++;
        if (routePhaseTicks > 500) {
            finish(client, false, "mounted route timeout phase=" + routePhase);
            return;
        }
        switch (routePhase) {
            case 0 -> {
                highRouteBaseline = establishRouteBaseline(client,
                        highRouteContraptionId, highRouteVehicleId, "high");
                if (highRouteBaseline == null) return;
                RingWorldMod.LOGGER.info("[create-compat-client] high mounted route baseline {}",
                        highRouteBaseline.describe());
                advanceRoutePhase(1);
            }
            case 1 -> {
                if (!verifyRouteStable(client, highRouteBaseline, "high positive crossing")) return;
                if (routePhaseTicks < 20) return;
                requestRouteMovement(client, highRouteContraptionId, highRouteVehicleId,
                        0.4, "high positive");
                Entity contraption = highRouteBaseline.contraptionObject();
                Entity vehicle = highRouteBaseline.vehicleObject();
                if (!routeCrossed && contraption.getX() > geometry.circumferenceBlocks() + 0.25
                        && vehicle.getX() > geometry.circumferenceBlocks() + 0.25) {
                    routeCrossed = true;
                    RingWorldMod.LOGGER.info(
                            "[create-compat-client] high mounted route crossed continuously {} vehicleX={} contraptionX={}",
                            highRouteBaseline.describe(), vehicle.getX(), contraption.getX());
                    advanceRoutePhase(2);
                }
            }
            case 2 -> {
                if (!verifyRouteStable(client, highRouteBaseline, "high post-crossing window")) return;
                if (routePhaseTicks == 20 && !captureWithEntityProof(client,
                        "ringworld-create-default-moving-high", highRouteContraptionId,
                        "high-mounted", "positive", "high",
                        "opaque+translucent+moving")) return;
                if (routePhaseTicks < 40) return;
                RingWorldMod.LOGGER.info(
                        "[create-compat-client] high mounted route PASS uninterruptedTicks={} {}",
                        routePhaseTicks, highRouteBaseline.describe());
                lowChartHop = 0;
                advanceRoutePhase(3);
            }
            case 3 -> {
                // The high route is now out of scope. Any removal while travelling through
                // distant waypoints is expected range-exit evidence, never seam evidence.
                if (!driveToLowChart(client, geometry) || routePhaseTicks < 80
                        || !client.levelRenderer.hasRenderedAllSections()
                        || !verifyClientControllerChart(client, geometry, false)) return;
                if (!highRouteCleanupRequested) {
                    logExpectedRangeExit(highRouteBaseline);
                    requestRouteCleanup(client, highRouteContraptionId, highRouteVehicleId,
                            true, "old high route after chart relocation");
                }
                if (!highRouteCleanupComplete) return;
                advanceRoutePhase(4);
            }
            case 4 -> {
                requestLowRouteSetup(client, geometry);
                if (!lowRouteSetupReady) return;
                if (highRouteContraptionId.equals(lowRouteContraptionId)
                        || highRouteVehicleId.equals(lowRouteVehicleId)) {
                    finish(client, false, "low route reused a high-route UUID");
                    return;
                }
                lowRouteBaseline = establishRouteBaseline(client,
                        lowRouteContraptionId, lowRouteVehicleId, "low");
                if (lowRouteBaseline == null) return;
                RingWorldMod.LOGGER.info("[create-compat-client] low mounted route distinct baseline {}",
                        lowRouteBaseline.describe());
                advanceRoutePhase(5);
            }
            case 5 -> {
                if (!verifyRouteStable(client, lowRouteBaseline, "low negative crossing")) return;
                if (routePhaseTicks < 20) return;
                requestRouteMovement(client, lowRouteContraptionId, lowRouteVehicleId,
                        -0.4, "low negative");
                Entity contraption = lowRouteBaseline.contraptionObject();
                Entity vehicle = lowRouteBaseline.vehicleObject();
                if (!routeCrossed && contraption.getX() < -0.25 && vehicle.getX() < -0.25) {
                    routeCrossed = true;
                    RingWorldMod.LOGGER.info(
                            "[create-compat-client] low mounted route crossed continuously {} vehicleX={} contraptionX={}",
                            lowRouteBaseline.describe(), vehicle.getX(), contraption.getX());
                    advanceRoutePhase(6);
                }
            }
            case 6 -> {
                if (!verifyRouteStable(client, lowRouteBaseline, "low post-crossing window")) return;
                if (routePhaseTicks == 20 && !captureWithEntityProof(client,
                        "ringworld-create-default-moving-low", lowRouteContraptionId,
                        "low-mounted", "negative", "low",
                        "opaque+translucent+moving")) return;
                if (routePhaseTicks < 40) return;
                RingWorldMod.LOGGER.info(
                        "[create-compat-client] low mounted route PASS uninterruptedTicks={} {}",
                        routePhaseTicks, lowRouteBaseline.describe());
                advanceRoutePhase(7);
            }
            case 7 -> {
                requestContraptionRemoval(client);
                if (!contraptionRemoved || routePhaseTicks < 200) return;
                RingCreate610ClientDiagnostics.Snapshot diagnostics = RingCreate610ClientDiagnostics.snapshot();
                if (diagnostics.curvedEmbeddingTransforms() == 0
                        || diagnostics.nonFiniteEmbeddingMatrices() != 0
                        || diagnostics.firstEmbeddingMatrix() == null
                        || diagnostics.lastEmbeddingMatrix() == null) {
                    finish(client, false, "invalid live embedding diagnostics " + diagnostics);
                    return;
                }
                RingWorldMod.LOGGER.info(
                        "[create-compat-client] embedding finite=true transforms={} first={} last={}",
                        diagnostics.curvedEmbeddingTransforms(), diagnostics.firstEmbeddingMatrix(),
                        diagnostics.lastEmbeddingMatrix());
                advance(8);
            }
            default -> finish(client, false, "invalid mounted route phase=" + routePhase);
        }
    }

    private static Entity findClientEntity(Minecraft client, UUID expectedId) {
        if (expectedId == null || client.level == null) return null;
        for (var entity : client.level.entitiesForRendering()) {
            if (expectedId.equals(entity.getUUID())) return entity;
        }
        return null;
    }

    private void requestDisconnect(Minecraft client) {
        RingWorldMod.LOGGER.info("[create-compat-client] requesting normal durable save-and-disconnect");
        ClientWorldLifecycle.disconnect(client, Component.literal("RingWorld Create durable qualification"));
        advance(9);
    }

    private void waitForDisconnect(Minecraft client) {
        if (client.level != null || client.getSingleplayerServer() != null) return;
        disconnectCleared = ClientRingState.sessionCleared();
        if (disconnectCleared) advance(10);
    }

    private void reopenWorld(Minecraft client) {
        if (client.level != null || client.getSingleplayerServer() != null) return;
        if (!reopenRequested) {
            reopenRequested = true;
            RingWorldMod.LOGGER.info("[create-compat-client] reopening durable world clientStateCleared={}", disconnectCleared);
            client.createWorldOpenFlows().openWorld(WORLD_NAME,
                    () -> finish(client, false, "durable reopen cancelled"));
            advance(11);
        }
    }

    private void verifyReopenedHighChart(Minecraft client, RingGeometry geometry) {
        if (!reopenedHighTeleportRequested) {
            reopenedHighTeleportRequested = true;
            highChartHop = 0;
        }
        if (!driveToHighChart(client, geometry)) return;
        if (stageTicks < 100 || !client.levelRenderer.hasRenderedAllSections()) return;
        requestDurableReloadVerification(client);
        if (!durableReloadVerified || !verifyClientControllerChart(client, geometry, true)) return;
        captureWithoutEntityTarget(client, "ringworld-create-default-reopened-high",
                "durable-controller", "none", "high", "durable+gameplay");
        lowChartHop = 0;
        advance(12);
    }

    private void verifyReopenedLowChartAndFinish(Minecraft client, RingGeometry geometry) {
        if (!driveToLowChart(client, geometry) || stageTicks < 80
                || !verifyClientControllerChart(client, geometry, false)) return;
        captureWithoutEntityTarget(client, "ringworld-create-default-reopened-low",
                "durable-controller", "none", "low", "durable+gameplay");
        if (RingCreate610MixinPlugin.appliedServerMixinCount() != 4
                || RingCreate610MixinPlugin.appliedClientMixinCount() != 4) {
            finish(client, false, "mixin-counts server="
                    + RingCreate610MixinPlugin.appliedServerMixinCount() + " client="
                    + RingCreate610MixinPlugin.appliedClientMixinCount());
            return;
        }
        finish(client, true, summary("default realClicks=both-directions preview=both-charts transfer=true durable=true controllerCharts=both"));
    }

    private void requestFreezeTransferredItem(Minecraft client) {
        if (transferredItemFrozen || stageTicks % 5 != 0) return;
        var server = client.getSingleplayerServer();
        server.execute(() -> runAsync("belt transfer", () -> transferredItemFrozen =
                RingCreate610ServerFixture.freezeTransferredItemAfterSeam(server.overworld())));
    }

    private void requestDurableReloadVerification(Minecraft client) {
        if (durableReloadVerified || stageTicks % 10 != 0) return;
        var server = client.getSingleplayerServer();
        server.execute(() -> runAsync("durable reload", () -> {
            RingCreate610ServerFixture.verifyDurableReload(server.overworld());
            durableReloadVerified = true;
        }));
    }

    private void verifyClickedBeltAsync(Minecraft client, int z, String label, boolean forward) {
        if (forward ? forwardVerificationPending : reverseVerificationPending) return;
        if (forward) forwardVerificationPending = true;
        else reverseVerificationPending = true;
        var server = client.getSingleplayerServer();
        UUID playerId = client.player.getUUID();
        server.execute(() -> {
            try {
                RingCreate610ServerFixture.verifyClickedBelt(server.overworld(), z, label);
                if (forward) forwardVerified = true; else reverseVerified = true;
                RingWorldMod.LOGGER.info("[create-compat-client] {} canonical=true", label);
            } catch (IllegalStateException pendingPacket) {
                // The client prediction and its server packet are processed on separate queues.
                int attempts = forward ? ++forwardVerificationAttempts : ++reverseVerificationAttempts;
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if ((attempts == 1 || attempts % 20 == 0) && player != null) {
                    RingWorldMod.LOGGER.info("[create-compat-client] {} pending attempt={} {}",
                            label, attempts, RingCreate610ServerFixture.describeClickedBeltState(
                                    server.overworld(), player, z));
                }
            } finally {
                if (forward) forwardVerificationPending = false;
                else reverseVerificationPending = false;
            }
        });
    }

    private static void logServerClickState(Minecraft client, int z, String label) {
        var server = client.getSingleplayerServer();
        UUID playerId = client.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) RingWorldMod.LOGGER.info("[create-compat-client] {} {}", label,
                    RingCreate610ServerFixture.describeClickedBeltState(
                            server.overworld(), player, z));
        });
    }

    private void requestSecondClickReady(Minecraft client, boolean forward) {
        if (connectorReadyCheckPending) return;
        connectorReadyCheckPending = true;
        var server = client.getSingleplayerServer();
        UUID playerId = client.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            boolean ready = player != null
                    && RingCreate610ServerFixture.connectorSecondClickReady(player);
            if (forward) forwardSecondClickReady = ready;
            else reverseSecondClickReady = ready;
            connectorReadyCheckPending = false;
        });
    }

    private static boolean verifyClientControllerChart(Minecraft client, RingGeometry geometry, boolean highChart) {
        int lap = highChart ? geometry.circumferenceBlocks() : 0;
        int expectedBeltControllerX = highChart ? geometry.circumferenceBlocks() - 3 : -3;
        int expectedTankControllerX = highChart ? geometry.circumferenceBlocks() - 1 : -1;
        BlockEntity beltEntity = client.level.getBlockEntity(new BlockPos(lap, Y, RingCreate610ServerFixture.SEAM_BELT_Z));
        BlockEntity tankEntity = client.level.getBlockEntity(new BlockPos(lap, Y, RingCreate610ServerFixture.SEAM_TANK_Z));
        if (!(beltEntity instanceof RingCreate610BeltAccess belt)
                || !(tankEntity instanceof IMultiBlockEntityContainer tank)) return false;
        if (belt.getController().getX() != expectedBeltControllerX
                || tank.getController().getX() != expectedTankControllerX) return false;
        BlockPos beltNbt = NbtUtils.readBlockPos(beltEntity.saveWithFullMetadata(client.level.registryAccess()), "Controller").orElse(null);
        BlockPos tankNbt = NbtUtils.readBlockPos(tankEntity.saveWithFullMetadata(client.level.registryAccess()), "Controller").orElse(null);
        boolean canonicalWrites = beltNbt != null && tankNbt != null
                && beltNbt.getX() >= 0 && beltNbt.getX() < geometry.circumferenceBlocks()
                && tankNbt.getX() >= 0 && tankNbt.getX() < geometry.circumferenceBlocks();
        if (canonicalWrites) RingWorldMod.LOGGER.info(
                "[create-compat-client] client controller chart={} beltField={} tankField={} canonicalWrite={}/{}",
                highChart ? "high" : "low", belt.getController(), tank.getController(), beltNbt, tankNbt);
        return canonicalWrites;
    }

    private RouteBaseline establishRouteBaseline(
            Minecraft client, UUID contraptionUuid, UUID vehicleUuid, String chart) {
        Entity contraption = findClientEntity(client, contraptionUuid);
        Entity vehicle = findClientEntity(client, vehicleUuid);
        if (!(contraption instanceof OrientedContraptionEntity) || !(vehicle instanceof Minecart)) return null;
        if (client.level.getEntity(contraption.getId()) != contraption
                || client.level.getEntity(vehicle.getId()) != vehicle) return null;
        int visualIdentity = RingCreate610ClientDiagnostics.visualIdentity(contraption.getId());
        int visualCreates = RingCreate610ClientDiagnostics.visualCreateCount(contraption.getId());
        if (visualIdentity < 0 || visualCreates < 1) return null;
        return new RouteBaseline(chart, contraptionUuid, contraption.getId(), contraption,
                vehicleUuid, vehicle.getId(), vehicle, visualIdentity, visualCreates,
                RingCreate610ClientDiagnostics.visualDeleteCount(contraption.getId()),
                RingCreate610ClientDiagnostics.entityLeaveCount(contraption.getId()),
                RingCreate610ClientDiagnostics.entityLeaveCount(vehicle.getId()));
    }

    private boolean verifyRouteStable(Minecraft client, RouteBaseline baseline, String phase) {
        Entity contraptionByUuid = findClientEntity(client, baseline.contraptionUuid());
        Entity vehicleByUuid = findClientEntity(client, baseline.vehicleUuid());
        boolean stable = contraptionByUuid == baseline.contraptionObject()
                && vehicleByUuid == baseline.vehicleObject()
                && client.level.getEntity(baseline.contraptionId()) == baseline.contraptionObject()
                && client.level.getEntity(baseline.vehicleId()) == baseline.vehicleObject()
                && baseline.contraptionUuid().equals(baseline.contraptionObject().getUUID())
                && baseline.vehicleUuid().equals(baseline.vehicleObject().getUUID())
                && !baseline.contraptionObject().isRemoved()
                && !baseline.vehicleObject().isRemoved()
                && RingCreate610ClientDiagnostics.visualIdentity(baseline.contraptionId())
                        == baseline.visualIdentity()
                && RingCreate610ClientDiagnostics.visualCreateCount(baseline.contraptionId())
                        == baseline.visualCreates()
                && RingCreate610ClientDiagnostics.visualDeleteCount(baseline.contraptionId())
                        == baseline.visualDeletes()
                && RingCreate610ClientDiagnostics.entityLeaveCount(baseline.contraptionId())
                        == baseline.contraptionLeaves()
                && RingCreate610ClientDiagnostics.entityLeaveCount(baseline.vehicleId())
                        == baseline.vehicleLeaves();
        if (!stable) finish(client, false, phase + " lost identity/visual continuity baseline="
                + baseline.describe() + " current=" + describeCurrentRoute(client, baseline));
        return stable;
    }

    private static String describeCurrentRoute(Minecraft client, RouteBaseline baseline) {
        Entity contraption = client.level.getEntity(baseline.contraptionId());
        Entity vehicle = client.level.getEntity(baseline.vehicleId());
        return "contraption=" + describeEntity(contraption)
                + " vehicle=" + describeEntity(vehicle)
                + " visual=" + RingCreate610ClientDiagnostics.visualIdentity(baseline.contraptionId())
                + "/" + RingCreate610ClientDiagnostics.visualCreateCount(baseline.contraptionId())
                + "/" + RingCreate610ClientDiagnostics.visualDeleteCount(baseline.contraptionId())
                + " leaves=" + RingCreate610ClientDiagnostics.entityLeaveCount(baseline.contraptionId())
                + "/" + RingCreate610ClientDiagnostics.entityLeaveCount(baseline.vehicleId());
    }

    private static String describeEntity(Entity entity) {
        if (entity == null) return "null";
        return entity.getId() + "/" + entity.getUUID() + "/object="
                + System.identityHashCode(entity) + "/removed=" + entity.isRemoved()
                + "/x=" + entity.getX();
    }

    private void requestRouteMovement(
            Minecraft client, UUID contraptionId, UUID vehicleId, double velocity, String label) {
        if (routeMovementRequested) return;
        routeMovementRequested = true;
        var server = client.getSingleplayerServer();
        server.execute(() -> runAsync(label + " mounted route movement", () -> {
            Entity contraption = server.overworld().getEntity(contraptionId);
            Entity vehicle = server.overworld().getEntity(vehicleId);
            if (!(contraption instanceof OrientedContraptionEntity) || !(vehicle instanceof Minecart)) {
                throw new IllegalStateException(label + " mounted Create route missing");
            }
            vehicle.setDeltaMovement(velocity, 0.0, 0.0);
            RingWorldMod.LOGGER.info(
                    "[create-compat-client] {} mounted route started by ordinary Minecart ticks "
                            + "vehicle={} contraption={} velocity={}",
                    label, vehicle.getUUID(), contraption.getUUID(), velocity);
        }));
    }

    private void requestLowRouteSetup(Minecraft client, RingGeometry geometry) {
        if (lowRouteSetupRequested) return;
        lowRouteSetupRequested = true;
        var server = client.getSingleplayerServer();
        server.execute(() -> runAsync("low mounted route setup", () -> {
            MountedIds mounted = addMountedContraption(server.overworld(), geometry, false, 86);
            lowRouteContraptionId = mounted.contraptionId();
            lowRouteVehicleId = mounted.vehicleId();
            lowRouteSetupReady = true;
            RingWorldMod.LOGGER.info(
                    "[create-compat-client] distinct low mounted route created vehicle={} contraption={}",
                    lowRouteVehicleId, lowRouteContraptionId);
        }));
    }

    private void requestRouteCleanup(
            Minecraft client, UUID contraptionId, UUID vehicleId, boolean highRoute, String label) {
        if (highRoute && highRouteCleanupRequested) return;
        if (highRoute) highRouteCleanupRequested = true;
        var server = client.getSingleplayerServer();
        server.execute(() -> runAsync(label + " cleanup", () -> {
            Entity contraption = server.overworld().getEntity(contraptionId);
            if (contraption != null) contraption.discard();
            Entity vehicle = server.overworld().getEntity(vehicleId);
            if (vehicle != null) vehicle.discard();
            if (highRoute) highRouteCleanupComplete = true;
            RingWorldMod.LOGGER.info("[create-compat-client] {} discarded after qualification scope", label);
        }));
    }

    private static void logExpectedRangeExit(RouteBaseline baseline) {
        RingWorldMod.LOGGER.info(
                "[create-compat-client] chart relocation range-exit only baseline={} "
                        + "leaveCounts={}/{} visualDeletes={} (identities are retired and never reused)",
                baseline.describe(),
                RingCreate610ClientDiagnostics.entityLeaveCount(baseline.contraptionId()),
                RingCreate610ClientDiagnostics.entityLeaveCount(baseline.vehicleId()),
                RingCreate610ClientDiagnostics.visualDeleteCount(baseline.contraptionId()));
    }

    private void requestContraptionRemoval(Minecraft client) {
        if (contraptionRemovalRequested) return;
        contraptionRemovalRequested = true;
        var server = client.getSingleplayerServer();
        UUID id = movingContraptionId;
        server.execute(() -> runAsync("contraption cleanup", () -> {
            if (id != null && server.overworld().getEntity(id) instanceof OrientedContraptionEntity entity) {
                entity.discard();
            }
            Entity mounted = server.overworld().getEntity(lowRouteContraptionId);
            if (mounted != null) mounted.discard();
            Entity vehicle = server.overworld().getEntity(lowRouteVehicleId);
            if (vehicle != null) vehicle.discard();
            contraptionRemoved = true;
            RingWorldMod.LOGGER.info("[create-compat-client] qualified contraptions removed before durable save");
        }));
    }

    private static UUID addContraption(ServerLevel level, RingGeometry geometry) {
        MountedContraption contraption = new MountedContraption();
        contraption.anchor = BlockPos.ZERO;
        for (int x = -4; x <= 4; x++) for (int y = 0; y <= 2; y++) {
            BlockPos local = new BlockPos(x, y, 0);
            contraption.getBlocks().put(local, new StructureTemplate.StructureBlockInfo(local,
                    (x + y & 1) == 0 ? Blocks.COPPER_BLOCK.defaultBlockState()
                            : Blocks.TINTED_GLASS.defaultBlockState(), null));
        }
        contraption.bounds = new net.minecraft.world.phys.AABB(-4, 0, 0, 5, 3, 1);
        OrientedContraptionEntity entity = OrientedContraptionEntity.createAtYaw(level, contraption, Direction.SOUTH, 37.0F);
        entity.setPos(geometry.circumferenceBlocks() - 6.0, 122.0, 80.5);
        if (!level.addFreshEntity(entity)) throw new IllegalStateException("moving contraption was not added");
        return entity.getUUID();
    }

    private static MountedIds addMountedContraption(
            ServerLevel level, RingGeometry geometry, boolean positiveDirection, int z) {
        for (int offset = -16; offset <= 16; offset++) {
            int x = geometry.wrapBlockX(geometry.circumferenceBlocks() + offset);
            level.setBlockAndUpdate(new BlockPos(x, 121, z), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(x, 122, z), Blocks.RAIL.defaultBlockState()
                    .setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
        }
        double startX = positiveDirection ? geometry.circumferenceBlocks() - 6.0 : 6.0;
        Minecart vehicle = new Minecart(level, startX, 122.0, z + 0.5);
        if (!level.addFreshEntity(vehicle)) throw new IllegalStateException("mounted root vehicle was not added");

        MountedContraption contraption = new MountedContraption();
        contraption.anchor = BlockPos.ZERO;
        for (int x = -4; x <= 4; x++) for (int y = 0; y <= 2; y++) {
            BlockPos local = new BlockPos(x, y, 0);
            contraption.getBlocks().put(local, new StructureTemplate.StructureBlockInfo(local,
                    (x + y & 1) == 0 ? Blocks.COPPER_BLOCK.defaultBlockState()
                            : Blocks.TINTED_GLASS.defaultBlockState(), null));
        }
        contraption.bounds = new net.minecraft.world.phys.AABB(-4, 0, 0, 5, 3, 1);
        OrientedContraptionEntity mounted = OrientedContraptionEntity.createAtYaw(
                level, contraption, Direction.SOUTH, positiveDirection ? 23.0F : 337.0F);
        mounted.setPos(vehicle.getX(), vehicle.getY(), vehicle.getZ());
        if (!level.addFreshEntity(mounted)) throw new IllegalStateException("mounted contraption was not added");
        if (!mounted.startRiding(vehicle, true)) throw new IllegalStateException("mounted contraption did not ride root vehicle");
        return new MountedIds(mounted.getUUID(), vehicle.getUUID());
    }

    private record MountedIds(UUID contraptionId, UUID vehicleId) { }

    private record RouteBaseline(
            String chart,
            UUID contraptionUuid, int contraptionId, Entity contraptionObject,
            UUID vehicleUuid, int vehicleId, Entity vehicleObject,
            int visualIdentity, int visualCreates, int visualDeletes,
            int contraptionLeaves, int vehicleLeaves) {
        String describe() {
            return "chart=" + chart
                    + " contraption=" + contraptionId + "/" + contraptionUuid
                    + "/object=" + System.identityHashCode(contraptionObject)
                    + " vehicle=" + vehicleId + "/" + vehicleUuid
                    + "/object=" + System.identityHashCode(vehicleObject)
                    + " visual=" + visualIdentity + "/creates=" + visualCreates
                    + "/deletes=" + visualDeletes
                    + " leaves=" + contraptionLeaves + "/" + vehicleLeaves;
        }
    }

    private static boolean click(Minecraft client, BlockPos position) {
        InteractionResult result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit(position));
        return result.consumesAction();
    }

    private static BlockHitResult hit(BlockPos position) {
        return new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false);
    }

    private static void teleportPlayer(Minecraft client, double x, double y, double z, float yaw, float pitch) {
        var server = client.getSingleplayerServer();
        UUID playerId = client.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) player.teleportTo(server.overworld(), x, y, z, Set.of(), yaw, pitch);
        });
    }

    private boolean driveToHighChart(Minecraft client, RingGeometry geometry) {
        double x = client.player.getX();
        if (Math.abs(x - (geometry.circumferenceBlocks() - 3.5)) <= 4.0) {
            chartTransitionPending = false;
            return true;
        }
        if (highChartHop == 0 && x < 100.0) {
            highChartHop = 1;
            chartTransitionPending = false;
            teleportPlayer(client, 800.0, 124.0, 76.5, 90.0F, 5.0F);
        } else if (highChartHop == 1 && Math.abs(x - 800.0) < 8.0) {
            highChartHop = 2;
            teleportPlayer(client, 1600.0, 124.0, 76.5, 90.0F, 5.0F);
        } else if (highChartHop == 2 && Math.abs(x - 1600.0) < 8.0) {
            highChartHop = 3;
            teleportPlayer(client, geometry.circumferenceBlocks() - 1.0,
                    120.0, 76.5, 90.0F, 15.0F);
        }
        return false;
    }

    private boolean driveToLowChart(Minecraft client, RingGeometry geometry) {
        double x = client.player.getX();
        if (Math.abs(x - 1.5) <= 4.0) return true;
        if (lowChartHop == 0 && x > 1900.0) {
            lowChartHop = 1;
            teleportPlayer(client, 1600.0, 124.0, 76.5, -90.0F, 5.0F);
        } else if (lowChartHop == 1 && Math.abs(x - 1600.0) < 8.0) {
            lowChartHop = 2;
            teleportPlayer(client, 800.0, 124.0, 76.5, -90.0F, 5.0F);
        } else if (lowChartHop == 2 && Math.abs(x - 800.0) < 8.0) {
            lowChartHop = 3;
            teleportPlayer(client, geometry.circumferenceBlocks() - 1.0,
                    120.0, 76.5, -90.0F, 15.0F);
        }
        return false;
    }

    private void advanceRoutePhase(int nextPhase) {
        routePhase = nextPhase;
        routePhaseTicks = 0;
        routeMovementRequested = false;
        routeCrossed = false;
    }

    private void advance(int nextStage) { stage = nextStage; stageTicks = 0; }

    private boolean captureWithEntityProof(
            Minecraft client, String name, UUID targetId, String route,
            String direction, String chart, String state) {
        Entity target = findClientEntity(client, targetId);
        if (!(target instanceof OrientedContraptionEntity)
                || client.level.getEntity(target.getId()) != target || target.isRemoved()) {
            finish(client, false, "capture target OCE missing from ClientLevel/render membership "
                    + "name=" + name + " target=" + targetId);
            return false;
        }
        RingWorldMod.LOGGER.info(
                "[create-compat-client] capture-proof name={} relative=screenshots/{}.png "
                        + "backendMode={} route={} direction={} chart={} state={} "
                        + "targetOceId={} targetOceUuid={} targetObject={} "
                        + "clientLevel=true renderMembership=true removed=false visualIdentity={}",
                name, name, offMode() ? "off" : "default", route, direction, chart, state,
                target.getId(), target.getUUID(), System.identityHashCode(target),
                RingCreate610ClientDiagnostics.visualIdentity(target.getId()));
        capture(client, name);
        return true;
    }

    private static void captureWithoutEntityTarget(
            Minecraft client, String name, String route,
            String direction, String chart, String state) {
        RingWorldMod.LOGGER.info(
                "[create-compat-client] capture-proof name={} relative=screenshots/{}.png "
                        + "backendMode=default route={} direction={} chart={} state={} "
                        + "targetOce=none clientLevel=n/a renderMembership=n/a",
                name, name, route, direction, chart, state);
        capture(client, name);
    }

    private static void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1,
                message -> RingWorldMod.LOGGER.info("[create-compat-client] screenshot {} {}", name, message.getString()));
    }

    private String summary(String detail) {
        RingCreate610ClientDiagnostics.Snapshot diagnostics = RingCreate610ClientDiagnostics.snapshot();
        String backend = Backend.REGISTRY.getIdOrThrow(BackendManager.currentBackend()).toString();
        return detail + " backend=" + backend
                + " serverMixins=" + RingCreate610MixinPlugin.appliedServerMixinCount()
                + " clientMixins=" + RingCreate610MixinPlugin.appliedClientMixinCount()
                + " attachedReads=" + diagnostics.attachedControllerReads()
                + " detachedReads=" + diagnostics.detachedControllerReads()
                + " deferredRepairs=" + diagnostics.deferredControllerRepairs()
                + " curvedEmbeddingTransforms=" + diagnostics.curvedEmbeddingTransforms()
                + " nonFiniteMatrices=" + diagnostics.nonFiniteEmbeddingMatrices()
                + " frames=" + renderedFrames + " framesOver50ms=" + framesOver50Millis
                + " maxFrameMs=" + String.format(java.util.Locale.ROOT, "%.3f", maxFrameNanos / 1_000_000.0);
    }

    private static boolean offMode() { return "off".equals(System.getProperty(MODE_PROPERTY, "default")); }

    private void runAsync(String label, Runnable action) {
        try { action.run(); }
        catch (Throwable failure) {
            asynchronousFailure = label + ": " + failure;
            RingWorldMod.LOGGER.error("[create-compat-client] async failure {}", label, failure);
        }
    }

    private static boolean finish(Minecraft client, boolean passed, String detail) {
        RingWorldMod.LOGGER.info("[create-compat-client] result={} {}", passed, detail);
        client.stop();
        return true;
    }
}
