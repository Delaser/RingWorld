package dev.ringworld.platform.neoforge.compat.create610;

import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import dev.ringworld.RingWorldMod;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.compat.ClientWorldLifecycle;
import dev.ringworld.client.compat.Screenshot;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.platform.neoforge.compat.create610.mixin.RingCreate610MixinPlugin;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTopology;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Disposable exact-tuple reproducer for a real glued Mechanical Bearing assembly. */
public final class RingCreate610BearingFixture {
    public static final String ENABLE_PROPERTY = "ringworld.createCompatBearing";
    public static final String ROUTE_PROPERTY = "ringworld.createCompatBearingRoute";
    public static final String BACKEND_PROPERTY = "ringworld.createCompatBearingBackend";
    private static final String WORLD_NAME = "RingWorld Create Bearing Matrix";
    private static final int Y = 120;
    private static final int TIMEOUT_TICKS = 5_000;
    private static final RingCreate610BearingFixture INSTANCE = new RingCreate610BearingFixture();

    private final List<Float> clientAngles = new ArrayList<>();
    private boolean worldScreenOpened;
    private boolean worldStarted;
    private boolean setupRequested;
    private volatile boolean setupReady;
    private volatile boolean gluePlacementReady;
    private volatile long gluePlacementGameTime = Long.MIN_VALUE;
    private volatile long glueIndexedGameTime = Long.MIN_VALUE;
    private volatile boolean powerRequestPending;
    private volatile boolean assemblyReady;
    private volatile boolean assemblyCheckPending;
    private volatile boolean disassemblyReady;
    private volatile boolean disassemblyCheckPending;
    private volatile String asynchronousFailure;
    private volatile UUID contraptionUuid;
    private volatile int serverEntityId = -1;
    private volatile float serverAngle;
    private volatile float serverSpeed;
    private volatile int capturedBlockCount;
    private volatile String preAssemblyInventory;
    private volatile String capturedInventory;
    private volatile String restoredInventory;
    private volatile String glueEvidence;
    private volatile String movedBlockEntityTypes;
    private Entity clientEntityIdentity;
    private int clientEntityId = -1;
    private int visualIdentity = -1;
    private int visualCreates;
    private int visualDeletes;
    private int stage;
    private int stageTicks;
    private int ticks;
    private int captures;
    private float lastCapturedAngle = Float.NaN;
    private int cameraPoseIndex;
    private int cameraPoseTicks;
    private boolean cameraPoseRequested;
    private volatile boolean cameraPoseReady;
    private boolean lifecycleCameraRequested;
    private int lifecycleCameraTicks;
    private boolean seamCrossingObserved;
    private int chartHop;
    private boolean speedChangeRequested;
    private boolean reversalRequested;
    private float speedChangeAngle;
    private float reversalAngle;
    private boolean restartRequested;
    private volatile boolean reassemblyReady;
    private boolean reassemblyServerCheckPending;
    private boolean reassemblyClientBound;
    private float reassemblyBaselineAngle;
    private boolean disconnectRequested;
    private boolean disconnectCleared;
    private boolean reopenRequested;
    private volatile boolean reopenedServerReady;
    private boolean reopenedServerCheckPending;
    private boolean reopenedClientBound;
    private boolean reopenedCaptureDone;
    private float reopenedBaselineAngle;
    private volatile boolean finalStopReady;
    private volatile boolean finalStopCheckPending;
    private volatile long restorationWaitGameTime = Long.MIN_VALUE;
    private boolean finalStopCameraRequested;
    private volatile boolean finalStopCameraReady;
    private int finalStopCameraTicks;
    private int lifecycleGeneration = 1;
    private boolean backendChecked;

    private RingCreate610BearingFixture() { }

    public static RingCreate610BearingFixture instance() { return INSTANCE; }

    public boolean startWorldIfEnabled(Minecraft client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || client.level != null || worldStarted) return false;
        if (!worldScreenOpened) {
            if (!(client.screen instanceof TitleScreen)) return true;
            CreateWorldScreen.openFresh(client, client.screen);
            worldScreenOpened = true;
            return true;
        }
        if (client.screen instanceof CreateWorldScreen screen) {
            WorldCreationUiState creator = screen.getUiState();
            creator.setName(WORLD_NAME + " " + route().id);
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
        if (++ticks > TIMEOUT_TICKS) return finish(client, false, "timeout stage=" + stage);
        if (ticks % 200 == 0) {
            RingWorldMod.LOGGER.info(
                    "[create-bearing] heartbeat ticks={} stage={} stageTicks={} level={} player={} screen={} backend={} "
                            + "finalStopCamera={}/{}/{} finalStop={}/{}",
                    ticks, stage, stageTicks, client.level != null, client.player != null,
                    client.screen == null ? "none" : client.screen.getClass().getName(), backend(),
                    finalStopCameraRequested, finalStopCameraReady, finalStopCameraTicks,
                    finalStopReady, finalStopCheckPending);
        }
        if (asynchronousFailure != null) return finish(client, false, asynchronousFailure);
        if (client.player == null || client.level == null || client.screen != null) {
            if (stage == 9) waitForDisconnect(client);
            else if (stage == 10) reopenWorld(client);
            return true;
        }
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || geometry.circumferenceBlocks() != 2048) return true;
        if (!backendChecked) {
            backendChecked = true;
            String requested = requestedBackend();
            if (!"default".equals(requested) && !backend().equals("flywheel:" + requested)) {
                return finish(client, false, "capability-rejected requested=flywheel:" + requested
                        + " actual=" + backend());
            }
        }
        ClientRingState.updateCameraPosition(client.player.getX());
        stageTicks++;
        switch (stage) {
            case 0 -> establishChartAndRequestSetup(client, geometry);
            case 1 -> waitForLiveRotatingBearing(client);
            case 2 -> sampleAndCapture(client);
            case 3 -> changeSpeedMagnitude(client);
            case 4 -> reverseDirection(client);
            case 5 -> requestOrdinaryStopAndDisassembly(client);
            case 6 -> verifyFirstDisassemblyAndRestart(client);
            case 7 -> waitForReassembledGeneration(client);
            case 8 -> requestDurableDisconnect(client);
            case 9 -> waitForDisconnect(client);
            case 10 -> reopenWorld(client);
            case 11 -> verifyReopenedGenerationAndStop(client);
            case 12 -> verifyFinalDisassemblyAndFinish(client);
            default -> { return finish(client, false, "invalid stage=" + stage); }
        }
        return true;
    }

    public void frameRendered() { }

    private void establishChartAndRequestSetup(Minecraft client, RingGeometry geometry) {
        if (!driveToRouteChart(client, geometry)) return;
        requestSetup(client, geometry);
    }

    private void requestSetup(Minecraft client, RingGeometry geometry) {
        if (setupRequested) return;
        setupRequested = true;
        var server = client.getSingleplayerServer();
        UUID playerUuid = client.player.getUUID();
        server.execute(() -> runAsync("bearing setup", () -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) throw new IllegalStateException("missing integrated player");
            RingGeometry serverGeometry = RingWorldServer.geometryFor(level);
            BlockPos bearingPos = bearingPos(serverGeometry);
            preparePlatform(level, bearingPos);
            exerciseTankConnectivityLifecycle(level, bearingPos);
            Map<BlockPos, BlockState> expected = placeAssembly(level, bearingPos);
            List<Edge> glueEdges = glueEdges(bearingPos);
            for (Edge edge : glueEdges) {
                SuperGlueEntity glue = new SuperGlueEntity(level, SuperGlueEntity.span(edge.first(), edge.second()));
                if (!level.addFreshEntity(glue)) throw new IllegalStateException("could not add glue " + edge);
            }
            preAssemblyInventory = inventory(expected);

            BlockPos negative = negativeControlPos(bearingPos);
            BlockState negativeState = level.getBlockState(negative);
            if (negativeState != Blocks.COPPER_BLOCK.defaultBlockState()) {
                throw new IllegalStateException("negative control was not placed exactly");
            }
            gluePlacementReady = true;
            gluePlacementGameTime = level.getGameTime();
            RingWorldMod.LOGGER.info(
                    "[create-bearing] placed unpowered bearing={} preInventory={} glueEdges={} "
                            + "negativeControl={} waitingForGlueIndex=true",
                    bearingPos, preAssemblyInventory, glueEdges,
                    blockName(level.getBlockState(negative)));
        }));
        advance(1);
    }

    private boolean driveToRouteChart(Minecraft client, RingGeometry geometry) {
        double x = client.player.getX();
        double target = cameraPresentationX(geometry);
        if (Math.abs(x - target) <= 4.0) return true;
        if (route() == Route.HIGH) {
            if (chartHop == 0 && x < 100.0) {
                chartHop = 1;
                teleportPlayer(client, 800.0, 76.0, 0.0F);
            } else if (chartHop == 1 && Math.abs(x - 800.0) < 8.0) {
                chartHop = 2;
                teleportPlayer(client, 1600.0, 76.0, 0.0F);
            } else if (chartHop == 2 && Math.abs(x - 1600.0) < 8.0) {
                chartHop = 3;
                teleportPlayer(client, target, 76.0, 0.0F);
            }
        } else {
            teleportPlayer(client, target, 76.0, 0.0F);
        }
        return false;
    }

    private static void teleportPlayer(Minecraft client, double x, double z, float yaw) {
        var server = client.getSingleplayerServer();
        UUID playerUuid = client.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player != null) player.teleportTo(server.overworld(), x, Y + 5.0, z,
                    Set.of(), yaw, 8.0F);
        });
    }

    private void waitForLiveRotatingBearing(Minecraft client) {
        if (!setupReady) {
            requestPowerAfterGlueIndexed(client);
            return;
        }
        if (!assemblyReady) {
            requestAssemblyAfterKineticInitialization(client);
            return;
        }
        if (contraptionUuid == null) return;
        Entity entity = findRenderedEntity(client, contraptionUuid);
        if (stageTicks % 100 == 0) {
            Entity byId = client.level.getEntity(serverEntityId);
            VisualizationManager manager = VisualizationManager.get(client.level);
            int controlledCount = 0;
            for (Entity candidate : client.level.entitiesForRendering()) {
                if (candidate instanceof ControlledContraptionEntity) controlledCount++;
            }
            RingWorldMod.LOGGER.info(
                    "[create-bearing] waiting client entity uuid={} serverId={} byId={} "
                            + "renderedByUuid={} controlledRendered={} alive={} canVisualize={} "
                            + "manager={} entityVisualCount={} backend={} visual={}/{}/{}",
                    contraptionUuid, serverEntityId, describe(byId), describe(entity), controlledCount,
                    byId != null && byId.isAlive(),
                    byId != null && VisualizationHelper.canVisualize(byId), manager != null,
                    manager == null ? -1 : manager.entities().visualCount(), backend(),
                    RingCreate610ClientDiagnostics.visualIdentity(serverEntityId),
                    RingCreate610ClientDiagnostics.visualCreateCount(serverEntityId),
                    RingCreate610ClientDiagnostics.visualDeleteCount(serverEntityId));
        }
        if (!(entity instanceof ControlledContraptionEntity controlled)) return;
        int currentVisual = RingCreate610ClientDiagnostics.visualIdentity(entity.getId());
        int creates = RingCreate610ClientDiagnostics.visualCreateCount(entity.getId());
        if (offMode()) {
            if (currentVisual != -1 || creates != 0
                    || RingCreate610ClientDiagnostics.visualDeleteCount(entity.getId()) != 0) return;
        } else if (currentVisual < 0 || creates != 1) return;
        clientEntityIdentity = entity;
        clientEntityId = entity.getId();
        visualIdentity = currentVisual;
        visualCreates = creates;
        visualDeletes = RingCreate610ClientDiagnostics.visualDeleteCount(entity.getId());
        clientAngles.add(controlled.getAngle(1.0F));
        RingWorldMod.LOGGER.info(
                "[create-bearing] live baseline entity={}/{} object={} visual={}/creates={}/deletes={} "
                        + "axis={} clientAngle={} renderMembership=true",
                entity.getId(), entity.getUUID(), System.identityHashCode(entity), visualIdentity,
                visualCreates, visualDeletes, controlled.getRotationAxis(), controlled.getAngle(1.0F));
        advance(2);
    }

    private void requestPowerAfterGlueIndexed(Minecraft client) {
        if (!gluePlacementReady || powerRequestPending) return;
        powerRequestPending = true;
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            try {
                runAsync("indexed glue verification", () -> {
                    ServerLevel level = server.overworld();
                    if (level.getGameTime() < gluePlacementGameTime + 2L) return;
                    BlockPos bearing = bearingPos(RingWorldServer.geometryFor(level));
                    List<Edge> edges = glueEdges(bearing);
                    if (!allGlueEdges(level, edges)) {
                        glueIndexedGameTime = Long.MIN_VALUE;
                        if (level.getGameTime() < gluePlacementGameTime + 100L) return;
                        assertGlueEdges(level, edges, true);
                    }
                    if (glueIndexedGameTime == Long.MIN_VALUE) {
                        glueIndexedGameTime = level.getGameTime();
                        return;
                    }
                    if (level.getGameTime() < glueIndexedGameTime + 2L) return;
                    assertGlueEdges(level, edges, true);
                    placePowerSource(level, bearing);
                    setupReady = true;
                    RingWorldMod.LOGGER.info(
                            "[create-bearing] indexed glue verified edges={} stableTicks={} ordinary power enabled",
                            edges.size(), level.getGameTime() - glueIndexedGameTime);
                });
            } finally {
                powerRequestPending = false;
            }
        });
    }

    private void requestAssemblyAfterKineticInitialization(Minecraft client) {
        if (assemblyCheckPending) return;
        assemblyCheckPending = true;
        var server = client.getSingleplayerServer();
        server.execute(() -> runAsync("powered bearing assembly", () -> {
            try {
                ServerLevel level = server.overworld();
                BlockPos bearingPos = bearingPos(RingWorldServer.geometryFor(level));
                BlockEntity bearing = requireBlockEntity(level, bearingPos,
                        "com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity");
                float kineticSpeed = ((Number) invoke(bearing, "getSpeed")).floatValue();
                if (kineticSpeed == 0.0F) return;
                ControlledContraptionEntity entity = (ControlledContraptionEntity) invoke(
                        bearing, "getMovedContraption");
                boolean ordinaryPowerAssembly = entity != null;
                if (entity == null) {
                    invoke(bearing, "assemble");
                    entity = (ControlledContraptionEntity) invoke(bearing, "getMovedContraption");
                }
                if (entity == null || !(entity.getContraption() instanceof BearingContraption)) {
                    throw new IllegalStateException("real BearingContraption was not assembled after power");
                }
                Map<BlockPos, BlockState> expected = expectedStates(bearingPos);
                BlockPos negative = negativeControlPos(bearingPos);
                BlockState negativeState = Blocks.COPPER_BLOCK.defaultBlockState();
                assertCaptured(level, entity, expected, negative, negativeState);
                List<Edge> edges = glueEdges(bearingPos);
                assertGlueEdges(level, edges, false);
                contraptionUuid = entity.getUUID();
                serverEntityId = entity.getId();
                capturedBlockCount = entity.getContraption().getBlocks().size();
                capturedInventory = capturedInventory(entity, expected);
                movedBlockEntityTypes = movedBlockEntityTypes(entity);
                if (capturedBlockCount < 24 || distinctMovedBlockEntityTypes(entity) < 2) {
                    throw new IllegalStateException("complex bearing capture was incomplete blocks="
                            + capturedBlockCount + " blockEntityTypes=" + movedBlockEntityTypes);
                }
                glueEvidence = "preEdges=" + edges + ",duringWorldGlue=0,negative="
                        + negative + "=" + blockName(negativeState);
                serverSpeed = (float) invoke(bearing, "getAngularSpeed");
                assemblyReady = true;
                RingWorldMod.LOGGER.info(
                        "[create-bearing] assembled backend-request=default bearing={} entity={}/{} "
                                + "type={} blocks={} kineticSpeed={} angularSpeed={} "
                                + "ordinaryPowerAssembly={} preInventory={} capturedInventory={} "
                                + "movedBlockEntityTypes={} glue={} negativeControl={}",
                        bearingPos, entity.getId(), entity.getUUID(),
                        entity.getContraption().getClass().getName(), capturedBlockCount,
                        kineticSpeed, serverSpeed, ordinaryPowerAssembly, preAssemblyInventory,
                        capturedInventory, movedBlockEntityTypes, glueEvidence,
                        blockName(level.getBlockState(negative)));
            } finally {
                assemblyCheckPending = false;
            }
        }));
    }

    private void sampleAndCapture(Minecraft client) {
        ControlledContraptionEntity controlled = requireStableClientEntity(client);
        if (controlled == null) return;
        float angle = controlled.getAngle(1.0F);
        if (clientAngles.isEmpty() || angularDistance(clientAngles.get(clientAngles.size() - 1), angle) >= 8.0F) {
            clientAngles.add(angle);
            pollServerSample(client);
        }
        double[] extent = rotatedXExtent(controlled);
        boolean crosses = route() == Route.HIGH
                ? extent[1] >= ClientRingState.geometry().circumferenceBlocks()
                : route() == Route.LOW ? extent[0] < 0.0 : false;
        if (crosses && !seamCrossingObserved) {
            seamCrossingObserved = true;
            RingWorldMod.LOGGER.info(
                    "[create-bearing] rotated seam extent route={} angle={} minX={} maxX={} "
                            + "canonicalAnchorX={} presentationEntityX={} sourceOwnership=canonical",
                    route().id, angle, extent[0], extent[1],
                    bearingPos(ClientRingState.geometry()).getX(), controlled.getX());
        }
        List<RingCreate610ClientDiagnostics.EntityTransformSample> transforms =
                RingCreate610ClientDiagnostics.entityTransformSamples(clientEntityId);
        if ((!offMode() && transforms.size() < 3) || distinctAngles(clientAngles) < 3) return;
        CameraPose pose = CameraPose.values()[cameraPoseIndex];
        if (!cameraPoseRequested) {
            requestCameraPose(client, pose);
            cameraPoseRequested = true;
            return;
        }
        if (!cameraPoseReady) return;
        RingCreate610FixtureProjection.Aim aim = aimAtTarget(client, controlled, pose.yawOffset);
        orientClient(client, aim.yaw(), aim.pitch());
        cameraPoseTicks++;
        if (cameraPoseTicks < 18 || angularDistance(angle, pose.targetAngle) > 5.0F) return;
        RingCreate610FixtureProjection.Projection projection = aim.projection();
        int viewportWidth = client.getMainRenderTarget().width;
        int viewportHeight = client.getMainRenderTarget().height;
        PixelRoi poseSanityRoi = pose.roi.roi(viewportWidth, viewportHeight);
        if (pose.expectedVisible) {
            if (!projection.centerInViewport()
                    || !projection.intersectsViewport(viewportWidth, viewportHeight)
                    || projection.pointsInViewport() < 8
                    || projection.width() < pose.minimumProjectedWidth
                    || projection.height() < 6.0) {
                finish(client, false, "visible camera projection missed target pose=" + pose.id
                        + " projection=" + projection.logValue());
                return;
            }
        } else if (projection.intersectsViewport(viewportWidth, viewportHeight)) {
            finish(client, false, "expected-offscreen camera still intersects target pose=" + pose.id
                    + " projection=" + projection.logValue());
            return;
        }
        RingCreate610ClientDiagnostics.EntityTransformSample transform = offMode()
                ? null : transforms.get(transforms.size() - 1);
        String name = "ringworld-create-bearing-" + backend().replace(':', '-') + "-"
                + route().id + "-" + pose.id;
        RingWorldMod.LOGGER.info(
                "[create-bearing] capture-proof name={} relative=screenshots/{}.png "
                        + "backend={} chart={} direction={} view={} distance={} state=glued+rotating "
                        + "entity={}/{} object={} visual={} clientAngle={} serverAngle={} rpm={} "
                        + "transformIndex={} transformAngle={} matrix={} rotatedMinX={} rotatedMaxX={} "
                        + "camera={}/{}/{} yaw={} pitch={} expectedVisible={} projectedBounds={} poseSanityRoi={} "
                        + "renderMembership=true removed=false",
                name, name, backend(), route().chart, route().direction, pose.id, pose.distance,
                controlled.getId(), controlled.getUUID(), System.identityHashCode(controlled),
                visualIdentity, angle, serverAngle, serverSpeed,
                transform == null ? -1 : transform.transformIndex(),
                transform == null ? Float.NaN : transform.angle(),
                transform == null ? "none" : transform.matrix(), extent[0], extent[1], client.player.getX(),
                client.player.getY(), client.player.getZ(), client.player.getYRot(),
                client.player.getXRot(), pose.expectedVisible, projection.logValue(), poseSanityRoi.logValue());
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1,
                message -> RingWorldMod.LOGGER.info(
                        "[create-bearing] screenshot {} {}", name, message.getString()));
        lastCapturedAngle = angle;
        captures++;
        cameraPoseIndex++;
        cameraPoseTicks = 0;
        cameraPoseRequested = false;
        cameraPoseReady = false;
        if (cameraPoseIndex == CameraPose.values().length) {
            if (route() != Route.NORMAL && !seamCrossingObserved) {
                finish(client, false, "rotated extent never crossed seam route=" + route().id);
                return;
            }
            RingWorldMod.LOGGER.info(
                    "[create-bearing] frustum sweep PASS route={} poses={} identityContinuous=true "
                            + "seamCrossingObserved={} expectedRangeExit=false",
                    route().id, captures, seamCrossingObserved);
            advance(3);
        }
    }

    private void requestCameraPose(Minecraft client, CameraPose pose) {
        var server = client.getSingleplayerServer();
        UUID playerUuid = client.player.getUUID();
        RingGeometry geometry = ClientRingState.geometry();
        server.execute(() -> runAsync("camera pose " + pose.id, () -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) throw new IllegalStateException("missing player for camera pose");
            BlockPos bearing = bearingPos(RingWorldServer.geometryFor(server.overworld()));
            double cameraX = new RingTopology(geometry).canonicalBlockX(
                    (int) Math.floor(cameraPresentationX(geometry))) + 0.5;
            double cameraZ = bearing.getZ() - pose.distance;
            player.teleportTo(server.overworld(), cameraX, Y + 5.0, cameraZ,
                    Set.of(), 0.0F, 0.0F);
            cameraPoseReady = true;
            RingWorldMod.LOGGER.info(
                    "[create-bearing] camera-pose route={} view={} serverCanonicalX={} z={} yawOffset={} "
                            + "trackingRangeBlocks=320",
                    route().id, pose.id, cameraX, cameraZ, pose.yawOffset);
        }));
    }

    private static void orientClient(Minecraft client, float yaw, float pitch) {
        client.player.setYRot(yaw);
        client.player.yRotO = yaw;
        client.player.setYHeadRot(yaw);
        client.player.setXRot(pitch);
        client.player.xRotO = pitch;
    }

    private static RingCreate610FixtureProjection.Aim aimAtTarget(
            Minecraft client, ControlledContraptionEntity entity, double yawOffset) {
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        return RingCreate610FixtureProjection.aim(
                ClientRingState.geometry(), camera, targetPoints(entity), yawOffset,
                client.getMainRenderTarget().width, client.getMainRenderTarget().height, 70.0);
    }

    private static List<Vec3> targetPoints(ControlledContraptionEntity entity) {
        List<Vec3> points = new ArrayList<>();
        for (BlockPos local : entity.getContraption().getBlocks().keySet()) {
            for (int x = 0; x <= 1; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = 0; z <= 1; z++) {
                        points.add(entity.toGlobalVector(
                                new Vec3(local.getX() + x, local.getY() + y, local.getZ() + z), 1.0F));
                    }
                }
            }
        }
        return List.copyOf(points);
    }

    private static double[] rotatedXExtent(ControlledContraptionEntity entity) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (BlockPos local : entity.getContraption().getBlocks().keySet()) {
            for (int x = 0; x <= 1; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = 0; z <= 1; z++) {
                        Vec3 world = entity.toGlobalVector(
                                new Vec3(local.getX() + x, local.getY() + y, local.getZ() + z), 1.0F);
                        min = Math.min(min, world.x);
                        max = Math.max(max, world.x);
                    }
                }
            }
        }
        return new double[]{min, max};
    }

    private void requestOrdinaryStopAndDisassembly(Minecraft client) {
        ControlledContraptionEntity controlled = requireStableClientEntity(client);
        if (controlled == null || stageTicks < 10) return;
        float clientAngle = controlled.getAngle(1.0F);
        if (angularDistance(clientAngle, 0.0F) > 7.0F) return;
        var server = client.getSingleplayerServer();
        server.execute(() -> runAsync("ordinary motor stop", () -> {
            ServerLevel level = server.overworld();
            BlockPos bearingPos = bearingPos(RingWorldServer.geometryFor(level));
            BlockEntity motor = requireBlockEntity(
                    level, bearingPos.below(), "com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity");
            BlockEntity bearing = requireBlockEntity(
                    level, bearingPos, "com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity");
            serverAngle = (float) invoke(bearing, "getInterpolatedAngle", float.class, 1.0F);
            serverSpeed = (float) invoke(bearing, "getAngularSpeed");
            Object generatedSpeed = field(motor, "generatedSpeed");
            invoke(generatedSpeed, "setValue", int.class, 0);
            RingWorldMod.LOGGER.info(
                    "[create-bearing] ordinary motor control stop requested speedBefore={} angle={}",
                    serverSpeed, serverAngle);
        }));
        advance(6);
    }

    private void changeSpeedMagnitude(Minecraft client) {
        ControlledContraptionEntity controlled = requireStableClientEntity(client);
        if (controlled == null) return;
        if (!speedChangeRequested) {
            speedChangeRequested = true;
            speedChangeAngle = controlled.getAngle(1.0F);
            setMotorSpeed(client, 32, "magnitude-change");
            return;
        }
        if (stageTicks % 5 == 0) pollServerSample(client);
        float angle = controlled.getAngle(1.0F);
        if (Math.abs(serverSpeed) < 9.5F || stageTicks < 20
                || angularDistance(speedChangeAngle, angle) < 35.0F) return;
        RingWorldMod.LOGGER.info(
                "[create-bearing] speed magnitude PASS route={} before=4.8 after={} "
                        + "angleBefore={} angleAfter={} identityContinuous=true",
                route().id, serverSpeed, speedChangeAngle, angle);
        advance(4);
    }

    private void reverseDirection(Minecraft client) {
        ControlledContraptionEntity controlled = requireStableClientEntity(client);
        if (controlled == null) return;
        if (!reversalRequested) {
            reversalRequested = true;
            reversalAngle = controlled.getAngle(1.0F);
            setMotorSpeed(client, -32, "direction-reversal");
            return;
        }
        if (stageTicks % 5 == 0) pollServerSample(client);
        float angle = controlled.getAngle(1.0F);
        float signedDelta = signedAngleDelta(reversalAngle, angle);
        if (serverSpeed > -9.5F || stageTicks < 20 || signedDelta > -35.0F) return;
        RingWorldMod.LOGGER.info(
                "[create-bearing] direction reversal PASS route={} speed={} angleBefore={} "
                        + "angleAfter={} signedDelta={} identityContinuous=true",
                route().id, serverSpeed, reversalAngle, angle, signedDelta);
        advance(5);
    }

    private void setMotorSpeed(Minecraft client, int speed, String label) {
        var server = client.getSingleplayerServer();
        server.execute(() -> runAsync("motor " + label, () -> {
            ServerLevel level = server.overworld();
            BlockEntity motor = requireBlockEntity(
                    level, bearingPos(RingWorldServer.geometryFor(level)).below(),
                    "com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity");
            Object generatedSpeed = field(motor, "generatedSpeed");
            invoke(generatedSpeed, "setValue", int.class, speed);
            RingWorldMod.LOGGER.info(
                    "[create-bearing] ordinary motor control label={} requestedSpeed={}",
                    label, speed);
        }));
    }

    private void verifyFirstDisassemblyAndRestart(Minecraft client) {
        if (!disassemblyReady && !disassemblyCheckPending && stageTicks % 5 == 0) {
            requestRestorationVerification(client, "first-disassembly");
        }
        if (!disassemblyReady) return;
        if (!restartRequested) {
            restartRequested = true;
            disassemblyReady = false;
            restorationWaitGameTime = Long.MIN_VALUE;
            setMotorSpeed(client, 16, "ordinary-restart-reassembly");
            RingWorldMod.LOGGER.info(
                    "[create-bearing] lifecycle generation=1 restored=true requesting ordinary reassembly");
            advance(7);
        }
    }

    private void requestRestorationVerification(Minecraft client, String phase) {
        disassemblyCheckPending = true;
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            try {
                runAsync(phase + " verification", () -> {
                    ServerLevel level = server.overworld();
                    BlockPos bearingPos = bearingPos(RingWorldServer.geometryFor(level));
                    BlockEntity bearing = requireBlockEntity(
                            level, bearingPos,
                            "com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity");
                    if (restorationWaitGameTime == Long.MIN_VALUE) {
                        restorationWaitGameTime = level.getGameTime();
                    }
                    Map<BlockPos, BlockState> expected = expectedStates(bearingPos);
                    BlockPos negative = negativeControlPos(bearingPos);
                    List<Edge> edges = glueEdges(bearingPos);
                    boolean complete = !(boolean) invoke(bearing, "isRunning")
                            && invoke(bearing, "getMovedContraption") == null
                            && restoredStatesMatch(level, expected)
                            && level.getBlockState(negative) == Blocks.COPPER_BLOCK.defaultBlockState()
                            && allGlueEdges(level, edges);
                    if (!complete && level.getGameTime() < restorationWaitGameTime + 200L) return;
                    if (!complete) {
                        throw new IllegalStateException("restoration did not settle within 200 ticks "
                                + "running=" + invoke(bearing, "isRunning")
                                + " moved=" + invoke(bearing, "getMovedContraption")
                                + " restoredStates=" + restoredStatesMatch(level, expected)
                                + " glue=" + allGlueEdges(level, edges));
                    }
                    assertGlueEdges(level, edges, true);
                    restoredInventory = inventory(expected);
                    disassemblyReady = true;
                    RingWorldMod.LOGGER.info(
                            "[create-bearing] {} restoredInventory={} glueEdges={} negativeControl={} "
                                    + "capturedEntityGone={}",
                            phase, restoredInventory, edges, blockName(level.getBlockState(negative)),
                            level.getEntity(contraptionUuid) == null);
                });
            } finally {
                disassemblyCheckPending = false;
            }
        });
    }

    private void waitForReassembledGeneration(Minecraft client) {
        if (!reassemblyReady && !reassemblyServerCheckPending && stageTicks % 5 == 0) {
            reassemblyServerCheckPending = true;
            var server = client.getSingleplayerServer();
            UUID previousUuid = contraptionUuid;
            server.execute(() -> {
                try {
                    runAsync("ordinary reassembly", () -> {
                        ServerLevel level = server.overworld();
                        BlockPos bearingPos = bearingPos(RingWorldServer.geometryFor(level));
                        BlockEntity bearing = requireBlockEntity(
                                level, bearingPos,
                                "com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity");
                        Object moved = invoke(bearing, "getMovedContraption");
                        if (!(moved instanceof ControlledContraptionEntity entity)
                                || previousUuid.equals(entity.getUUID())) return;
                        verifyActiveAssembly(level, bearingPos, entity);
                        contraptionUuid = entity.getUUID();
                        serverEntityId = entity.getId();
                        serverSpeed = (float) invoke(bearing, "getAngularSpeed");
                        lifecycleGeneration = 2;
                        reassemblyReady = true;
                        RingWorldMod.LOGGER.info(
                                "[create-bearing] lifecycle generation=2 ordinaryReassembly=true "
                                        + "previousUuid={} entity={}/{} blocks={} speed={}",
                                previousUuid, entity.getId(), entity.getUUID(),
                                entity.getContraption().getBlocks().size(), serverSpeed);
                    });
                } finally {
                    reassemblyServerCheckPending = false;
                }
            });
        }
        if (!reassemblyReady) return;
        Entity entity = findRenderedEntity(client, contraptionUuid);
        if (!(entity instanceof ControlledContraptionEntity controlled)) return;
        int currentVisual = RingCreate610ClientDiagnostics.visualIdentity(entity.getId());
        if ((!offMode() && currentVisual < 0) || (offMode() && currentVisual != -1)) return;
        if (!reassemblyClientBound) {
            bindClientGeneration(entity, currentVisual);
            reassemblyBaselineAngle = controlled.getAngle(1.0F);
            reassemblyClientBound = true;
            return;
        }
        controlled = requireStableClientEntity(client);
        if (controlled == null || stageTicks < 40
                || angularDistance(reassemblyBaselineAngle, controlled.getAngle(1.0F)) < 25.0F
                || (!offMode()
                && RingCreate610ClientDiagnostics.entityTransformSamples(clientEntityId).size() < 3)) return;
        if (!prepareLifecycleCapture(client, controlled)) return;
        captureLifecycleProof(client, controlled, "reassembled-active", "generation=2");
        resetLifecycleCamera();
        RingWorldMod.LOGGER.info(
                "[create-bearing] lifecycle generation=2 stable=true angleBefore={} angleAfter={} "
                        + "activeSaveReady=true",
                reassemblyBaselineAngle, controlled.getAngle(1.0F));
        advance(8);
    }

    private void requestDurableDisconnect(Minecraft client) {
        if (disconnectRequested || stageTicks < 10) return;
        ControlledContraptionEntity controlled = requireStableClientEntity(client);
        if (controlled == null) return;
        disconnectRequested = true;
        RingWorldMod.LOGGER.info(
                "[create-bearing] requesting durable save-and-disconnect generation={} entity={}/{} "
                        + "angle={} speed={} assemblyActive=true",
                lifecycleGeneration, controlled.getId(), controlled.getUUID(),
                controlled.getAngle(1.0F), serverSpeed);
        ClientWorldLifecycle.disconnect(client,
                Component.literal("RingWorld Create bearing durable qualification"));
        advance(9);
    }

    private void waitForDisconnect(Minecraft client) {
        if (client.level != null || client.getSingleplayerServer() != null) return;
        disconnectCleared = ClientRingState.sessionCleared();
        if (!disconnectCleared) return;
        RingWorldMod.LOGGER.info(
                "[create-bearing] durable disconnect complete clientStateCleared=true");
        advance(10);
    }

    private void reopenWorld(Minecraft client) {
        if (client.level != null || client.getSingleplayerServer() != null || reopenRequested) return;
        reopenRequested = true;
        String worldName = WORLD_NAME + " " + route().id;
        RingWorldMod.LOGGER.info("[create-bearing] reopening durable world name={} route={}",
                worldName, route().id);
        client.createWorldOpenFlows().openWorld(worldName,
                () -> finish(client, false, "durable bearing reopen cancelled"));
        advance(11);
    }

    private void verifyReopenedGenerationAndStop(Minecraft client) {
        if (finalStopReady) {
            disassemblyReady = false;
            disassemblyCheckPending = false;
            restorationWaitGameTime = Long.MIN_VALUE;
            advance(12);
            return;
        }
        if (!reopenedServerReady && !reopenedServerCheckPending && stageTicks % 5 == 0) {
            reopenedServerCheckPending = true;
            var server = client.getSingleplayerServer();
            UUID previousUuid = contraptionUuid;
            server.execute(() -> {
                try {
                    runAsync("durable reopen", () -> {
                        ServerLevel level = server.overworld();
                        BlockPos bearingPos = bearingPos(RingWorldServer.geometryFor(level));
                        BlockEntity bearing = requireBlockEntity(
                                level, bearingPos,
                                "com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity");
                        Object moved = invoke(bearing, "getMovedContraption");
                        if (!(moved instanceof ControlledContraptionEntity entity)) return;
                        verifyActiveAssembly(level, bearingPos, entity);
                        contraptionUuid = entity.getUUID();
                        serverEntityId = entity.getId();
                        serverSpeed = (float) invoke(bearing, "getAngularSpeed");
                        lifecycleGeneration = 3;
                        reopenedServerReady = true;
                        RingWorldMod.LOGGER.info(
                                "[create-bearing] durable reopen generation=3 previousUuid={} "
                                        + "entity={}/{} blocks={} speed={} canonicalAnchorX={}",
                                previousUuid, entity.getId(), entity.getUUID(),
                                entity.getContraption().getBlocks().size(), serverSpeed,
                                bearingPos.getX());
                    });
                } finally {
                    reopenedServerCheckPending = false;
                }
            });
        }
        if (!reopenedServerReady) return;
        Entity entity = findRenderedEntity(client, contraptionUuid);
        if (!(entity instanceof ControlledContraptionEntity controlled)) return;
        int currentVisual = RingCreate610ClientDiagnostics.visualIdentity(entity.getId());
        if ((!offMode() && currentVisual < 0) || (offMode() && currentVisual != -1)) return;
        if (!reopenedClientBound) {
            bindClientGeneration(entity, currentVisual);
            reopenedBaselineAngle = controlled.getAngle(1.0F);
            reopenedClientBound = true;
            return;
        }
        controlled = requireStableClientEntity(client);
        if (controlled == null || stageTicks < 40
                || angularDistance(reopenedBaselineAngle, controlled.getAngle(1.0F)) < 25.0F
                || (!offMode()
                && RingCreate610ClientDiagnostics.entityTransformSamples(clientEntityId).size() < 3)) return;
        if (!reopenedCaptureDone) {
            if (!prepareLifecycleCapture(client, controlled)) return;
            captureLifecycleProof(client, controlled, "durable-reopened-active", "generation=3");
            resetLifecycleCamera();
            reopenedCaptureDone = true;
            RingWorldMod.LOGGER.info(
                    "[create-bearing] durable reopen visible=true complete=true angleBefore={} angleAfter={} "
                            + "clientStateWasCleared={}",
                    reopenedBaselineAngle, controlled.getAngle(1.0F), disconnectCleared);
        }
        if (!finalStopCameraRequested) {
            finalStopCameraRequested = true;
            requestFinalStopCamera(client);
            return;
        }
        if (!finalStopCameraReady || ++finalStopCameraTicks < 20) return;
        requestFinalStopAtServerOrientation(client);
        if (!finalStopReady) return;
        disassemblyReady = false;
        disassemblyCheckPending = false;
        restorationWaitGameTime = Long.MIN_VALUE;
        advance(12);
    }

    private void requestFinalStopCamera(Minecraft client) {
        var server = client.getSingleplayerServer();
        UUID playerUuid = client.player.getUUID();
        server.execute(() -> runAsync("durable final near camera", () -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) throw new IllegalStateException("missing player for final stop camera");
            BlockPos bearing = bearingPos(RingWorldServer.geometryFor(server.overworld()));
            player.teleportTo(server.overworld(), bearing.getX() + 0.5, Y + 5.0,
                    bearing.getZ() - 28.0, Set.of(), 0.0F, 8.0F);
            finalStopCameraReady = true;
            RingWorldMod.LOGGER.info(
                    "[create-bearing] durable final control moved near bearing route={} distance=28",
                    route().id);
        }));
    }

    private void requestFinalStopAtServerOrientation(Minecraft client) {
        if (finalStopReady || finalStopCheckPending) return;
        finalStopCheckPending = true;
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            try {
                runAsync("durable final stop", () -> {
                    ServerLevel level = server.overworld();
                    BlockPos bearingPos = bearingPos(RingWorldServer.geometryFor(level));
                    BlockEntity bearing = requireBlockEntity(
                            level, bearingPos,
                            "com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity");
                    float angle = (float) invoke(bearing, "getInterpolatedAngle", float.class, 1.0F);
                    if (level.getGameTime() % 20L == 0L) {
                        RingWorldMod.LOGGER.info(
                                "[create-bearing] durable final alignment sample angle={} gameTime={}",
                                angle, level.getGameTime());
                    }
                    // The integrated client/server task cadence can observe every other
                    // 4.8-degree bearing step. Create still owns the actual alignment and
                    // disassembly; this wider request window is followed by exact restoration.
                    if (angularDistance(angle, 0.0F) > 15.0F) return;
                    BlockEntity motor = requireBlockEntity(
                            level, bearingPos.below(),
                            "com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity");
                    float speedBefore = (float) invoke(bearing, "getAngularSpeed");
                    Object generatedSpeed = field(motor, "generatedSpeed");
                    invoke(generatedSpeed, "setValue", int.class, 0);
                    finalStopReady = true;
                    RingWorldMod.LOGGER.info(
                            "[create-bearing] durable final ordinary stop requested serverAngle={} "
                                    + "speedBefore={} createAlignmentOwnsRestoration=true",
                            angle, speedBefore);
                });
            } finally {
                finalStopCheckPending = false;
            }
        });
    }

    private void verifyFinalDisassemblyAndFinish(Minecraft client) {
        if (!disassemblyReady && !disassemblyCheckPending && stageTicks % 5 == 0) {
            requestRestorationVerification(client, "durable-final-disassembly");
        }
        if (!disassemblyReady) return;
        List<RingCreate610ClientDiagnostics.EntityTransformSample> transforms =
                RingCreate610ClientDiagnostics.entityTransformSamples(clientEntityId);
        if (captures != CameraPose.values().length || distinctAngles(clientAngles) < 3) {
            finish(client, false, "insufficient live rotation evidence captures=" + captures
                    + " angles=" + clientAngles + " transforms=" + transforms.size());
            return;
        }
        String expectedBackend = "default".equals(requestedBackend())
                ? "flywheel:indirect" : "flywheel:" + requestedBackend();
        if (!expectedBackend.equals(backend())) {
            finish(client, false, "requested backend mismatch expected=" + expectedBackend
                    + " actual=" + backend());
            return;
        }
        if (RingCreate610MixinPlugin.appliedServerMixinCount() != 4
                || RingCreate610MixinPlugin.appliedClientMixinCount() != 6) {
            finish(client, false, "mixin counts changed server="
                    + RingCreate610MixinPlugin.appliedServerMixinCount() + " client="
                    + RingCreate610MixinPlugin.appliedClientMixinCount());
            return;
        }
        finish(client, true, "backend=" + backend() + " realBearing=true glued=true "
                + "route=" + route().id + " capturedBlocks=" + capturedBlockCount
                + " movedBlockEntityTypes=" + movedBlockEntityTypes + " negativeControl=true "
                + "distinctClientAngles=" + distinctAngles(clientAngles)
                + " transformSamples=" + transforms.size() + " captures=" + captures
                + " visualIdentityContinuous=true speedChange=true reversal=true "
                + "flywheelEmbedding=" + (offMode() ? "zero" : "finite") + " "
                + "ordinaryMotorStop=true reassemblyGeneration=2 durableReopenGeneration=3 "
                + "disassemblyRestored=true");
    }

    private void verifyActiveAssembly(
            ServerLevel level, BlockPos bearingPos, ControlledContraptionEntity entity) {
        Map<BlockPos, BlockState> expected = expectedStates(bearingPos);
        assertCaptured(level, entity, expected, negativeControlPos(bearingPos),
                Blocks.COPPER_BLOCK.defaultBlockState());
        if (entity.getContraption().getBlocks().size() != expected.size()
                || distinctMovedBlockEntityTypes(entity) < 2) {
            throw new IllegalStateException("active assembly inventory changed blocks="
                    + entity.getContraption().getBlocks().size()
                    + " blockEntities=" + movedBlockEntityTypes(entity));
        }
    }

    private void bindClientGeneration(Entity entity, int currentVisual) {
        clientEntityIdentity = entity;
        clientEntityId = entity.getId();
        visualIdentity = currentVisual;
        visualCreates = RingCreate610ClientDiagnostics.visualCreateCount(entity.getId());
        visualDeletes = RingCreate610ClientDiagnostics.visualDeleteCount(entity.getId());
        RingWorldMod.LOGGER.info(
                "[create-bearing] client generation={} bound entity={}/{} object={} visual={}/creates={}/deletes={} "
                        + "renderMembership=true",
                lifecycleGeneration, entity.getId(), entity.getUUID(),
                System.identityHashCode(entity), visualIdentity, visualCreates, visualDeletes);
    }

    private void captureLifecycleProof(
            Minecraft client, ControlledContraptionEntity controlled, String phase, String state) {
        List<RingCreate610ClientDiagnostics.EntityTransformSample> transforms =
                RingCreate610ClientDiagnostics.entityTransformSamples(controlled.getId());
        RingCreate610ClientDiagnostics.EntityTransformSample transform = offMode()
                ? null : transforms.get(transforms.size() - 1);
        RingCreate610FixtureProjection.Aim aim = aimAtTarget(client, controlled, 0.0);
        orientClient(client, aim.yaw(), aim.pitch());
        RingCreate610FixtureProjection.Projection projection = aim.projection();
        PixelRoi poseSanityRoi = PixelRoiKind.CENTER.roi(
                client.getMainRenderTarget().width, client.getMainRenderTarget().height);
        if (!projection.centerInViewport()
                || !projection.intersectsViewport(
                client.getMainRenderTarget().width, client.getMainRenderTarget().height)
                || projection.pointsInViewport() != projection.totalPoints()
                || projection.width() < 120.0 || projection.height() < 6.0) {
            throw new IllegalStateException(
                    "lifecycle capture does not contain whole target phase=" + phase
                            + " projection=" + projection.logValue());
        }
        String name = "ringworld-create-bearing-" + backend().replace(':', '-') + "-"
                + route().id + "-" + phase;
        RingWorldMod.LOGGER.info(
                "[create-bearing] lifecycle-proof name={} relative=screenshots/{}.png backend={} "
                        + "route={} phase={} state={} entity={}/{} object={} visual={} angle={} speed={} "
                        + "transformIndex={} transformAngle={} matrix={} expectedVisible=true "
                        + "projectedBounds={} poseSanityRoi={} camera={}/{}/{} yaw={} pitch={} "
                        + "renderMembership=true removed=false",
                name, name, backend(), route().id, phase, state, controlled.getId(),
                controlled.getUUID(), System.identityHashCode(controlled), visualIdentity,
                controlled.getAngle(1.0F), serverSpeed,
                transform == null ? -1 : transform.transformIndex(),
                transform == null ? Float.NaN : transform.angle(),
                transform == null ? "none" : transform.matrix(), projection.logValue(),
                poseSanityRoi.logValue(),
                client.player.getX(), client.player.getY(), client.player.getZ(),
                client.player.getYRot(), client.player.getXRot());
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1,
                message -> RingWorldMod.LOGGER.info(
                        "[create-bearing] screenshot {} {}", name, message.getString()));
    }

    private boolean prepareLifecycleCapture(
            Minecraft client, ControlledContraptionEntity controlled) {
        if (!lifecycleCameraRequested) {
            lifecycleCameraRequested = true;
            cameraPoseReady = false;
            requestCameraPose(client, CameraPose.CENTER_NEAR);
            return false;
        }
        if (!cameraPoseReady) return false;
        RingCreate610FixtureProjection.Aim aim = aimAtTarget(client, controlled, 0.0);
        orientClient(client, aim.yaw(), aim.pitch());
        return ++lifecycleCameraTicks >= 18;
    }

    private void resetLifecycleCamera() {
        lifecycleCameraRequested = false;
        lifecycleCameraTicks = 0;
        cameraPoseReady = false;
    }

    private void pollServerSample(Minecraft client) {
        var server = client.getSingleplayerServer();
        UUID id = contraptionUuid;
        server.execute(() -> runAsync("server angle sample", () -> {
            ServerLevel level = server.overworld();
            BlockEntity bearing = requireBlockEntity(
                    level, bearingPos(RingWorldServer.geometryFor(level)),
                    "com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity");
            if (!(level.getEntity(id) instanceof ControlledContraptionEntity entity)) {
                throw new IllegalStateException("server bearing entity disappeared during rotation");
            }
            serverAngle = entity.getAngle(1.0F);
            serverSpeed = (float) invoke(bearing, "getAngularSpeed");
            RingWorldMod.LOGGER.info(
                    "[create-bearing] angle-sample entity={}/{} serverAngle={} speed={} running={}",
                    entity.getId(), entity.getUUID(), serverAngle, serverSpeed,
                    (boolean) invoke(bearing, "isRunning"));
        }));
    }

    private ControlledContraptionEntity requireStableClientEntity(Minecraft client) {
        Entity entity = client.level.getEntity(clientEntityId);
        Entity byUuid = findRenderedEntity(client, contraptionUuid);
        boolean stable = entity == clientEntityIdentity && byUuid == clientEntityIdentity
                && !entity.isRemoved()
                && RingCreate610ClientDiagnostics.visualIdentity(clientEntityId) == visualIdentity
                && RingCreate610ClientDiagnostics.visualCreateCount(clientEntityId) == visualCreates
                && RingCreate610ClientDiagnostics.visualDeleteCount(clientEntityId) == visualDeletes;
        if (!stable) {
            finish(client, false, "bearing identity/visual discontinuity id=" + clientEntityId
                    + " expectedObject=" + System.identityHashCode(clientEntityIdentity)
                    + " actual=" + describe(entity) + " rendered=" + describe(byUuid)
                    + " visual=" + RingCreate610ClientDiagnostics.visualIdentity(clientEntityId)
                    + "/" + RingCreate610ClientDiagnostics.visualCreateCount(clientEntityId)
                    + "/" + RingCreate610ClientDiagnostics.visualDeleteCount(clientEntityId));
            return null;
        }
        return entity instanceof ControlledContraptionEntity controlled ? controlled : null;
    }

    private static Map<BlockPos, BlockState> placeAssembly(ServerLevel level, BlockPos bearingPos) {
        BlockState bearing = withProperty(block("create:mechanical_bearing").defaultBlockState(), "facing", "up");
        level.setBlockAndUpdate(bearingPos, bearing);
        Map<BlockPos, BlockState> expected = expectedStates(bearingPos);
        expected.forEach(level::setBlockAndUpdate);
        level.setBlockAndUpdate(negativeControlPos(bearingPos), Blocks.COPPER_BLOCK.defaultBlockState());
        return expected;
    }

    private static void placePowerSource(ServerLevel level, BlockPos bearingPos) {
        BlockState motor = withProperty(
                block("create:creative_motor").defaultBlockState(), "facing", "up");
        level.setBlockAndUpdate(bearingPos.below(), motor);
    }

    private static void exerciseTankConnectivityLifecycle(ServerLevel level, BlockPos bearingPos) {
        BlockPos probe = bearingPos.offset(-7, 0, 7);
        level.setBlockAndUpdate(probe, block("create:fluid_tank").defaultBlockState());
        if (level.getBlockEntity(probe) == null || !level.removeBlock(probe, false)
                || !level.getBlockState(probe).isAir()) {
            throw new IllegalStateException("could not exercise strict tank connectivity target at " + probe);
        }
        RingWorldMod.LOGGER.info(
                "[create-bearing] strict server mixin target lifecycle=fluid-tank-place-remove position={}",
                probe);
    }

    private static Map<BlockPos, BlockState> expectedStates(BlockPos bearingPos) {
        Map<BlockPos, BlockState> expected = new LinkedHashMap<>();
        BlockPos root = bearingPos.above();
        BlockState chassis = block("create:linear_chassis").defaultBlockState();
        chassis = withProperty(chassis, "axis", "y");
        chassis = withProperty(chassis, "sticky_top", "false");
        chassis = withProperty(chassis, "sticky_bottom", "false");
        expected.put(root, chassis);
        Route route = route();
        for (int distance = 1; distance <= 13; distance++) {
            expected.put(root.relative(route.xArm, distance), armState(distance, false));
            expected.put(root.relative(route.zArm, distance), armState(distance, true));
        }
        return expected;
    }

    private static BlockState armState(int distance, boolean secondArm) {
        return switch (distance) {
            case 1 -> Blocks.CUT_COPPER.defaultBlockState();
            case 2 -> Blocks.TINTED_GLASS.defaultBlockState();
            case 3 -> secondArm ? Blocks.BLUE_SHULKER_BOX.defaultBlockState()
                    : Blocks.CHEST.defaultBlockState();
            case 4 -> Blocks.GOLD_BLOCK.defaultBlockState();
            case 5 -> Blocks.MAGENTA_CONCRETE.defaultBlockState();
            case 6 -> Blocks.AMETHYST_BLOCK.defaultBlockState();
            case 7 -> Blocks.LIME_CONCRETE.defaultBlockState();
            case 8 -> Blocks.EMERALD_BLOCK.defaultBlockState();
            case 9 -> Blocks.MAGENTA_STAINED_GLASS.defaultBlockState();
            case 10 -> Blocks.GOLD_BLOCK.defaultBlockState();
            case 11 -> Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
            case 12 -> Blocks.DIAMOND_BLOCK.defaultBlockState();
            case 13 -> secondArm ? Blocks.ORANGE_STAINED_GLASS.defaultBlockState()
                    : Blocks.LIME_CONCRETE.defaultBlockState();
            default -> throw new IllegalArgumentException("unsupported arm distance " + distance);
        };
    }

    private static List<Edge> glueEdges(BlockPos bearingPos) {
        BlockPos root = bearingPos.above();
        List<Edge> edges = new ArrayList<>();
        Route route = route();
        for (Direction direction : List.of(route.xArm, route.zArm)) {
            BlockPos previous = root;
            for (int distance = 1; distance <= 13; distance++) {
                BlockPos next = root.relative(direction, distance);
                edges.add(new Edge(previous, next));
                previous = next;
            }
        }
        return List.copyOf(edges);
    }

    private static BlockPos negativeControlPos(BlockPos bearingPos) {
        Route route = route();
        return bearingPos.above().relative(route.xArm, 5).relative(route.zArm);
    }

    private static void assertCaptured(
            ServerLevel level, ControlledContraptionEntity entity,
            Map<BlockPos, BlockState> expected, BlockPos negative, BlockState negativeState) {
        for (Map.Entry<BlockPos, BlockState> entry : expected.entrySet()) {
            if (!level.getBlockState(entry.getKey()).isAir()) {
                throw new IllegalStateException("glued source remained in world at " + entry.getKey());
            }
            BlockPos local = entry.getKey().subtract(entity.getContraption().anchor);
            var captured = entity.getContraption().getBlocks().get(local);
            if (captured == null || captured.state() != entry.getValue()) {
                throw new IllegalStateException("captured inventory mismatch at " + entry.getKey()
                        + " local=" + local + " captured=" + captured);
            }
        }
        if (level.getBlockState(negative) != negativeState) {
            throw new IllegalStateException("unglued negative control was captured");
        }
    }

    private static void assertGlueEdges(ServerLevel level, List<Edge> edges, boolean expected) {
        for (Edge edge : edges) {
            boolean glued = SuperGlueEntity.isGlued(
                    level, edge.first(), direction(edge.first(), edge.second()), null);
            if (glued != expected) {
                throw new IllegalStateException("glue edge " + edge + " expected=" + expected
                        + " actual=" + glued);
            }
        }
    }

    private static boolean allGlueEdges(ServerLevel level, List<Edge> edges) {
        for (Edge edge : edges) {
            if (!SuperGlueEntity.isGlued(
                    level, edge.first(), direction(edge.first(), edge.second()), null)) return false;
        }
        return true;
    }

    private static boolean restoredStatesMatch(
            ServerLevel level, Map<BlockPos, BlockState> expected) {
        for (Map.Entry<BlockPos, BlockState> entry : expected.entrySet()) {
            if (level.getBlockState(entry.getKey()) != entry.getValue()) return false;
        }
        return true;
    }

    private static Direction direction(BlockPos first, BlockPos second) {
        int dx = second.getX() - first.getX();
        int dy = second.getY() - first.getY();
        int dz = second.getZ() - first.getZ();
        for (Direction direction : Direction.values()) {
            if (direction.getStepX() == dx && direction.getStepY() == dy
                    && direction.getStepZ() == dz) return direction;
        }
        throw new IllegalArgumentException("positions are not adjacent: " + first + " -> " + second);
    }

    private static void preparePlatform(ServerLevel level, BlockPos bearingPos) {
        level.setDayTime(6_000L);
        level.setWeatherParameters(0, 120_000, false, false);
        for (int x = bearingPos.getX() - 32; x <= bearingPos.getX() + 32; x++) {
            for (int z = bearingPos.getZ() - 130; z <= bearingPos.getZ() + 28; z++) {
                if (x < 0 || x >= 2048) continue;
                level.setBlockAndUpdate(new BlockPos(x, Y - 2, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = Y - 1; y <= Y + 20; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static BlockEntity requireBlockEntity(ServerLevel level, BlockPos pos, String className) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null && blockEntity.getClass().getName().equals(className)) return blockEntity;
        throw new IllegalStateException("missing " + className + " at " + pos + ": " + blockEntity);
    }

    private static BlockPos bearingPos(RingGeometry geometry) {
        int x = switch (route()) {
            case NORMAL -> geometry.circumferenceBlocks() / 4 + 14;
            case HIGH -> geometry.circumferenceBlocks() - 10;
            case LOW -> 2;
        };
        return new BlockPos(x, Y, 100);
    }

    private static double cameraPresentationX(RingGeometry geometry) {
        return switch (route()) {
            case NORMAL -> geometry.circumferenceBlocks() / 4.0 + 14.0;
            case HIGH -> geometry.circumferenceBlocks() - 7.0;
            case LOW -> 6.0;
        };
    }

    private static Entity findRenderedEntity(Minecraft client, UUID uuid) {
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity.getUUID().equals(uuid)) return entity;
        }
        return null;
    }

    private static int distinctAngles(List<Float> angles) {
        List<Float> distinct = new ArrayList<>();
        outer: for (float angle : angles) {
            for (float existing : distinct) if (angularDistance(existing, angle) < 5.0F) continue outer;
            distinct.add(angle);
        }
        return distinct.size();
    }

    private static float angularDistance(float first, float second) {
        float delta = Math.abs(first - second) % 360.0F;
        return Math.min(delta, 360.0F - delta);
    }

    private static float signedAngleDelta(float first, float second) {
        float delta = (second - first) % 360.0F;
        if (delta > 180.0F) delta -= 360.0F;
        if (delta < -180.0F) delta += 360.0F;
        return delta;
    }

    private static String capturedInventory(
            ControlledContraptionEntity entity, Map<BlockPos, BlockState> expected) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : expected.entrySet()) {
            BlockPos local = entry.getKey().subtract(entity.getContraption().anchor);
            values.add(entry.getKey() + "->" + local + "=" + blockName(entry.getValue()));
        }
        return values.toString();
    }

    private static String movedBlockEntityTypes(ControlledContraptionEntity entity) {
        List<String> types = new ArrayList<>();
        entity.getContraption().getBlocks().forEach((pos, info) -> {
            if (info.nbt() != null) types.add(pos + "=" + info.nbt().getString("id"));
        });
        return types.toString();
    }

    private static int distinctMovedBlockEntityTypes(ControlledContraptionEntity entity) {
        java.util.HashSet<String> types = new java.util.HashSet<>();
        entity.getContraption().getBlocks().values().forEach(info -> {
            if (info.nbt() != null) types.add(info.nbt().getString("id"));
        });
        return types.size();
    }

    private static String inventory(Map<BlockPos, BlockState> states) {
        List<String> values = new ArrayList<>();
        states.forEach((pos, state) -> values.add(pos + "=" + blockName(state)));
        return values.toString();
    }

    private static String blockName(BlockState state) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock())
                + state.getValues().toString();
    }

    private static Block block(String id) {
        Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse(id));
        if (block == Blocks.AIR) throw new IllegalStateException("missing registered block " + id);
        return block;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withProperty(BlockState state, String name, String value) {
        Property property = state.getProperties().stream()
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "missing property " + name + " on " + state));
        java.util.Optional parsedValue = property.getValue(value);
        if (parsedValue.isEmpty()) {
            throw new IllegalStateException("invalid property " + name + "=" + value + " on " + state);
        }
        Comparable parsed = (Comparable) parsedValue.get();
        return state.setValue(property, parsed);
    }

    private static Object invoke(Object target, String methodName, Class<?> parameterType, Object argument) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterType);
            return method.invoke(target, argument);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not invoke " + methodName + " on " + target, failure);
        }
    }

    private static Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not invoke " + methodName + " on " + target, failure);
        }
    }

    private static Object field(Object target, String fieldName) {
        try {
            var field = target.getClass().getField(fieldName);
            return field.get(target);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not read " + fieldName + " on " + target, failure);
        }
    }

    private static String backend() {
        return Backend.REGISTRY.getIdOrThrow(BackendManager.currentBackend()).toString();
    }

    private static String requestedBackend() {
        return System.getProperty(BACKEND_PROPERTY, "default");
    }

    private static boolean offMode() {
        return "off".equals(requestedBackend());
    }

    private static String describe(Entity entity) {
        return entity == null ? "null" : entity.getId() + "/" + entity.getUUID()
                + "/object=" + System.identityHashCode(entity) + "/removed=" + entity.isRemoved();
    }

    private static Route route() {
        return switch (System.getProperty(ROUTE_PROPERTY, "high")) {
            case "normal" -> Route.NORMAL;
            case "high" -> Route.HIGH;
            case "low" -> Route.LOW;
            default -> throw new IllegalStateException("unknown bearing route "
                    + System.getProperty(ROUTE_PROPERTY));
        };
    }

    private void advance(int next) { stage = next; stageTicks = 0; }

    private void runAsync(String label, Runnable action) {
        try { action.run(); }
        catch (Throwable failure) {
            asynchronousFailure = label + ": " + failure;
            RingWorldMod.LOGGER.error("[create-bearing] async failure {}", label, failure);
        }
    }

    private static boolean finish(Minecraft client, boolean passed, String detail) {
        RingWorldMod.LOGGER.info("[create-bearing] result={} {}", passed, detail);
        client.stop();
        return true;
    }

    private record Edge(BlockPos first, BlockPos second) { }

    private enum CameraPose {
        CENTER_NEAR("center-near", 36.0, 0.0, true, 30.0F, 120.0, PixelRoiKind.CENTER),
        EDGE_RIGHT("edge-right", 36.0, -22.0, true, 90.0F, 60.0, PixelRoiKind.RIGHT),
        LEAVE_RIGHT("leave-right", 36.0, -100.0, false, 150.0F, 0.0, PixelRoiKind.OFFSCREEN),
        REENTER_LEFT("reenter-left", 36.0, 22.0, true, 210.0F, 60.0, PixelRoiKind.LEFT),
        LEAVE_LEFT("leave-left", 36.0, 100.0, false, 270.0F, 0.0, PixelRoiKind.OFFSCREEN),
        REENTER_RIGHT("reenter-right", 36.0, -22.0, true, 330.0F, 60.0, PixelRoiKind.RIGHT),
        FAR_CENTER("far-center", 96.0, 0.0, true, 45.0F, 30.0, PixelRoiKind.CENTER),
        FAR_EDGE("far-edge", 96.0, 25.0, true, 135.0F, 18.0, PixelRoiKind.LEFT);

        private final String id;
        private final double distance;
        private final double yawOffset;
        private final boolean expectedVisible;
        private final float targetAngle;
        private final double minimumProjectedWidth;
        private final PixelRoiKind roi;

        CameraPose(
                String id, double distance, double yawOffset, boolean expectedVisible,
                float targetAngle, double minimumProjectedWidth, PixelRoiKind roi) {
            this.id = id;
            this.distance = distance;
            this.yawOffset = yawOffset;
            this.expectedVisible = expectedVisible;
            this.targetAngle = targetAngle;
            this.minimumProjectedWidth = minimumProjectedWidth;
            this.roi = roi;
        }
    }

    private enum PixelRoiKind {
        LEFT(0.10, 0.55, 0.25, 0.75),
        CENTER(0.25, 0.75, 0.25, 0.75),
        RIGHT(0.45, 0.90, 0.25, 0.75),
        OFFSCREEN(0.40, 0.60, 0.20, 0.40);

        private final double minX;
        private final double maxX;
        private final double minY;
        private final double maxY;

        PixelRoiKind(double minX, double maxX, double minY, double maxY) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }

        PixelRoi roi(int width, int height) {
            return new PixelRoi(
                    (int) Math.floor(width * minX),
                    (int) Math.floor(height * minY),
                    (int) Math.ceil(width * maxX),
                    (int) Math.ceil(height * maxY));
        }
    }

    private record PixelRoi(int minX, int minY, int maxX, int maxY) {
        String logValue() { return minX + "/" + minY + "/" + maxX + "/" + maxY; }
    }

    private enum Route {
        NORMAL("normal", "normal", "none", Direction.WEST, Direction.NORTH),
        HIGH("high", "high", "positive-seam", Direction.WEST, Direction.NORTH),
        LOW("low", "low", "negative-seam", Direction.EAST, Direction.NORTH);

        private final String id;
        private final String chart;
        private final String direction;
        private final Direction xArm;
        private final Direction zArm;

        Route(String id, String chart, String direction, Direction xArm, Direction zArm) {
            this.id = id;
            this.chart = chart;
            this.direction = direction;
            this.xArm = xArm;
            this.zArm = zArm;
        }
    }
}
