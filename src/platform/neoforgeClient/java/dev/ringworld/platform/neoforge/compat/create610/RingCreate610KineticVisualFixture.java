package dev.ringworld.platform.neoforge.compat.create610;

import com.simibubi.create.content.kinetics.base.RotatingInstance;
import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.backend.engine.AbstractInstancer;
import dev.engine_room.flywheel.backend.engine.InstanceHandleImpl;
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.ringworld.RingWorldMod;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.compat.Screenshot;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

/**
 * Disposable D1 fixture for the exact Create/Flywheel standalone block-visual path.
 * This observes public Flywheel storage and exact instance state without adding a mixin.
 */
public final class RingCreate610KineticVisualFixture {
    public static final String ENABLE_PROPERTY = "ringworld.createCompatKineticVisual";
    public static final String ROUTE_PROPERTY = "ringworld.createCompatKineticVisualRoute";
    public static final String BACKEND_PROPERTY = "ringworld.createCompatKineticVisualBackend";
    public static final String PHASE_PROPERTY = "ringworld.createCompatKineticVisualPhase";
    private static final String WORLD_NAME = "RingWorld Create Kinetic Visual";
    private static final int Y = 120;
    private static final int TIMEOUT_TICKS = 3_600;
    private static final float[] CAPTURE_PHASES = {0.0F, 120.0F, 240.0F};
    private static final RingCreate610KineticVisualFixture INSTANCE =
            new RingCreate610KineticVisualFixture();

    private boolean worldScreenOpened;
    private boolean worldStarted;
    private boolean setupRequested;
    private volatile boolean setupReady;
    private volatile String asynchronousFailure;
    private int chartHop;
    private int ticks;
    private int stage;
    private int stageTicks;
    private int poseIndex;
    private int phaseIndex;
    private int poseTicks;
    private boolean poseRequested;
    private volatile boolean poseReady;
    private int captures;
    private int stableVisualIdentity = -1;
    private boolean backendChecked;
    private boolean missingVisualAtBaseline;
    private boolean originRecreationRequested;
    private int originVisualIdentity = -1;
    private int originEmbeddingIdentity = -1;
    private int recreatedVisualIdentity = -1;
    private int recreatedEmbeddingIdentity = -1;
    private boolean removeRequested;
    private volatile boolean removeReady;
    private boolean readdRequested;
    private volatile boolean readdReady;
    private boolean lateGeometryReleased;
    private int lateGeometryVisualIdentity = -1;
    private int lateGeometryEmbeddingIdentity = -1;

    private RingCreate610KineticVisualFixture() { }

    public static RingCreate610KineticVisualFixture instance() { return INSTANCE; }

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
            creator.setName(WORLD_NAME + " " + phase() + " " + route().id + " "
                    + requestedBackend());
            creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            creator.setAllowCommands(true);
            creator.setSeed("-2162056627494116761");
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
        if (asynchronousFailure != null) return finish(client, false, asynchronousFailure);
        if (client.player == null || client.level == null || client.screen != null) return true;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || geometry.circumferenceBlocks() != 2_048) return true;
        if (!backendChecked) {
            backendChecked = true;
            String requested = requestedBackend();
            if (!"default".equals(requested) && !backend().equals("flywheel:" + requested)) {
                return finish(client, false, "capability-rejected requested=flywheel:" + requested
                        + " actual=" + backend());
            }
        }
        configureCamera(client);
        ClientRingState.updateCameraPosition(client.player.getX());
        stageTicks++;
        return switch (stage) {
            case 0 -> establishChartAndSetup(client, geometry);
            case 1 -> waitForVisualAndBegin(client, geometry);
            case 2 -> captureMatrix(client, geometry);
            case 3 -> verifyRenderOriginRecreation(client, geometry);
            case 4 -> verifyRemoval(client, geometry);
            case 5 -> verifyReadd(client, geometry);
            case 6 -> finishAfterLifecycle(client);
            default -> finish(client, false, "invalid stage=" + stage);
        };
    }

    private boolean establishChartAndSetup(Minecraft client, RingGeometry geometry) {
        if (!driveToRouteChart(client, geometry)) return true;
        if (setupRequested) return true;
        if (d2Mode() && !offMode()) {
            RingCreate610ClientDiagnostics.suppressKineticEmbeddingGeometryForFixture(true);
        }
        setupRequested = true;
        var server = client.getSingleplayerServer();
        UUID playerUuid = client.player.getUUID();
        server.execute(() -> runAsync("kinetic visual setup", () -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) throw new IllegalStateException("missing integrated player");
            RingGeometry serverGeometry = RingWorldServer.geometryFor(level);
            BlockPos target = canonicalTarget(serverGeometry);
            prepareArena(level, target);
            placeReproducer(level, target);
            BlockEntity blockEntity = level.getBlockEntity(target);
            if (blockEntity == null) throw new IllegalStateException("missing kinetic target at " + target);
            BlockEntity motor = level.getBlockEntity(target.south());
            if (motor == null) throw new IllegalStateException("missing creative motor at " + target.south());
            setCreativeMotorSpeed(motor, 32);
            float speed = kineticSpeed(blockEntity);
            setupReady = true;
            RingWorldMod.LOGGER.info(
                    "[create-kinetic-d1] setup route={} targetCanonical={} targetType={} state={} "
                            + "serverSpeed={} ownership=canonical",
                    route().id, target, blockEntity.getClass().getName(),
                    blockName(level.getBlockState(target)), speed);
        }));
        advance(1);
        return true;
    }

    private boolean waitForVisualAndBegin(Minecraft client, RingGeometry geometry) {
        if (!setupReady) return true;
        if (d2Mode() && !offMode() && !lateGeometryReleased && !poseRequested) {
            requestCameraPose(client, geometry, CameraPose.CENTER_NEAR);
            poseRequested = true;
            return true;
        }
        if (d2Mode() && !offMode() && !lateGeometryReleased && !poseReady) return true;
        BlockPos target = presentationTarget(geometry, client.player.getX());
        BlockEntity blockEntity = client.level.getBlockEntity(target);
        if (blockEntity == null || kineticSpeed(blockEntity) == 0.0F) {
            if (stageTicks % 100 == 0) {
                BlockPos canonical = canonicalTarget(geometry);
                BlockEntity canonicalEntity = client.level.getBlockEntity(canonical);
                RingWorldMod.LOGGER.info(
                        "[create-kinetic-d1] waiting targetPresentation={} presentationBE={} "
                                + "targetCanonical={} canonicalBE={} playerX={}",
                        target, describeBlockEntity(blockEntity), canonical,
                        describeBlockEntity(canonicalEntity), client.player.getX());
            }
            return true;
        }
        VisualObservation observation = observe(client, target, canonicalTarget(geometry));
        if (!offMode() && observation == null) {
            if (stageTicks < 20) return true;
        }
        if (offMode() && observation != null) {
            return finish(client, false, "OFF unexpectedly created Flywheel visual " + observation);
        }
        if (d2Mode() && !offMode()) {
            if (!lateGeometryReleased) {
                if (observation == null && stageTicks < 600) return true;
                if (observation == null || observation.ownerCurved
                        || observation.ownerIdentityUpdates == 0
                        || observation.ownerCreated != 2 || observation.ownerDeleted != 0
                        || observation.ownerFailedDeletes != 0) {
                    return finish(client, false,
                            "late-geometry identity phase invalid observation=" + observation);
                }
                lateGeometryVisualIdentity = observation.visualIdentity;
                lateGeometryEmbeddingIdentity = observation.ownerEmbeddingIdentity;
                RingCreate610ClientDiagnostics.suppressKineticEmbeddingGeometryForFixture(false);
                lateGeometryReleased = true;
                stageTicks = 0;
                RingWorldMod.LOGGER.info(
                        "[create-kinetic-d2] late-geometry identity visual={} embedding={} "
                                + "owned={} created={} deleted={} failedDeletes={} identityUpdates={}",
                        lateGeometryVisualIdentity, lateGeometryEmbeddingIdentity,
                        observation.ownedCount, observation.ownerCreated,
                        observation.ownerDeleted, observation.ownerFailedDeletes,
                        observation.ownerIdentityUpdates);
                return true;
            }
            if (observation == null || !observation.ownerCurved) {
                if (stageTicks < 40) return true;
                return finish(client, false,
                        "late-geometry child did not transition to curved observation=" + observation);
            }
            if (observation.visualIdentity != lateGeometryVisualIdentity
                    || observation.ownerEmbeddingIdentity != lateGeometryEmbeddingIdentity
                    || observation.ownerCreated != 2 || observation.ownerDeleted != 0
                    || observation.ownerFailedDeletes != 0) {
                return finish(client, false,
                        "late-geometry identity/leak failure observation=" + observation);
            }
            RingWorldMod.LOGGER.info(
                    "[create-kinetic-d2] late-geometry curved PASS visual={} embedding={} "
                            + "owned={} created={} deleted={} failedDeletes={} curvedUpdates={} finite={}",
                    observation.visualIdentity, observation.ownerEmbeddingIdentity,
                    observation.ownedCount, observation.ownerCreated,
                    observation.ownerDeleted, observation.ownerFailedDeletes,
                    observation.ownerCurvedUpdates,
                    observation.ownerFinitePose);
        }
        if (observation != null) stableVisualIdentity = observation.visualIdentity;
        RingWorldMod.LOGGER.info(
                "[create-kinetic-d1] baseline backend={} route={} targetCanonical={} targetPresentation={} "
                        + "clientBlockEntity={} speed={} visual={} rootEnvironment={} matrixIndex={}",
                backend(), route().id, canonicalTarget(geometry), target,
                blockEntity.getClass().getName(), kineticSpeed(blockEntity),
                observation == null ? "none" : observation.visualClass,
                observation == null ? "none" : observation.environmentClass,
                observation == null ? -1 : observation.matrixIndex);
        advance(2);
        return true;
    }

    private boolean captureMatrix(Minecraft client, RingGeometry geometry) {
        CameraPose pose = CameraPose.values()[poseIndex];
        if (!poseRequested) {
            requestCameraPose(client, geometry, pose);
            poseRequested = true;
            return true;
        }
        if (!poseReady) return true;
        poseTicks++;
        BlockPos target = presentationTarget(geometry, client.player.getX());
        BlockEntity blockEntity = client.level.getBlockEntity(target);
        if (blockEntity == null || kineticSpeed(blockEntity) == 0.0F) {
            return finish(client, false, "kinetic target unavailable during capture at " + target);
        }
        VisualObservation observation = observe(client, target, canonicalTarget(geometry));
        if (!offMode()) {
            if (observation == null) {
                if (poseTicks < 60) return true;
                missingVisualAtBaseline = true;
                RingWorldMod.LOGGER.info(
                        "[create-kinetic-d1] classified visual-never-created backend={} route={} "
                                + "view={} targetCanonical={} targetPresentation={} "
                                + "presentationStorageVisual=none canonicalStorageVisual=none",
                        backend(), route().id, pose.id, canonicalTarget(geometry), target);
            }
            if (!missingVisualAtBaseline && poseTicks >= 25 && stableVisualIdentity == -1) {
                // A render-origin move may rebuild block visuals. Bind one settled generation,
                // then require that exact object throughout the three fixed-camera samples.
                stableVisualIdentity = observation.visualIdentity;
            } else if (!missingVisualAtBaseline && poseTicks > 25
                    && observation.visualIdentity != stableVisualIdentity) {
                return finish(client, false,
                        "visual identity changed inside settled pose window old="
                                + stableVisualIdentity + " new=" + observation.visualIdentity);
            }
            if (!missingVisualAtBaseline && (!observation.ownerCurved
                    || !observation.ownerFinitePose || observation.ownedCount < 1
                    || observation.ownerVisualIdentity != observation.visualIdentity)) {
                return finish(client, false, "owned child embedding is not finite/curved "
                        + observation);
            }
            if (!missingVisualAtBaseline && backend().equals("flywheel:indirect")
                    && observation.matrixIndex <= 0) {
                return finish(client, false,
                        "indirect child environment has no uploaded matrix index " + observation);
            }
        }

        ProjectionSet projections = projections(client, geometry, target, pose.yawOffset);
        orientClient(client, projections.aim.yaw(), projections.aim.pitch());
        if (poseTicks < 80) return true;
        if (!projections.curved.intersectsViewport(
                client.getMainRenderTarget().width, client.getMainRenderTarget().height)
                || !projections.curved.centerInViewport()) {
            return finish(client, false, "curved target projection is not visible pose=" + pose.id
                    + " projection=" + projections.curved.logValue());
        }

        float speed = kineticSpeed(blockEntity);
        float phase = kineticPhase(client, speed,
                observation == null ? 22.5F : observation.rotationOffset);
        float desired = CAPTURE_PHASES[phaseIndex];
        if (angularDistance(phase, desired) > 6.0F) return true;
        if (!offMode() && !missingVisualAtBaseline
                && observation.visualIdentity != stableVisualIdentity) {
            return finish(client, false, "visual identity changed inside fixed-camera phase window");
        }
        String name = "ringworld-create-kinetic-" + phase() + "-"
                + backend().replace(':', '-') + "-"
                + route().id + "-" + pose.id + "-phase-" + phaseIndex;
        RingWorldMod.LOGGER.info(
                "[create-kinetic-d1] capture-proof name={} relative=screenshots/{}.png backend={} "
                        + "route={} view={} phaseIndex={} phase={} desiredPhase={} rpm={} "
                        + "targetCanonical={} targetPresentation={} clientBlockEntity={} blockState={} "
                        + "visualClass={} visualIdentity={} storageLookup={} visualPos={} renderOrigin={} "
                        + "instancePosition={} instanceSpeed={} instanceOffset={} instanceVisible={} "
                        + "instanceCount={} visibleInstanceCount={} instanceStates={} "
                        + "environmentClass={} matrixIndex={} camera={}/{}/{} yaw={} pitch={} fov=70 hudHidden=true "
                        + "ownedCount={} ownerCreated={} ownerDeleted={} ownerFailedDeletes={} "
                        + "ownerIdentityUpdates={} "
                        + "ownerCurvedUpdates={} ownerMalformedUpdates={} ownerVisualIdentity={} "
                        + "ownerEmbeddingIdentity={} ownerCurved={} ownerFinitePose={} ownerPose={} "
                        + "curvedBounds={} flatBounds={} referenceBounds={} staticCasingAtTarget=true "
                        + "expectedVisible=true ownership=canonical+transient-presentation",
                name, name, backend(), route().id, pose.id, phaseIndex, phase, desired,
                speed, canonicalTarget(geometry), target,
                blockEntity.getClass().getName(), blockName(blockEntity.getBlockState()),
                observation == null ? "none" : observation.visualClass,
                observation == null ? -1 : observation.visualIdentity,
                observation == null ? "none" : observation.storageLookup,
                observation == null ? "none" : observation.visualPos,
                observation == null ? "none" : observation.renderOrigin,
                observation == null ? "none" : observation.instancePosition(),
                observation == null ? Float.NaN : observation.rotationalSpeed,
                observation == null ? Float.NaN : observation.rotationOffset,
                observation != null && observation.instanceVisible,
                observation == null ? 0 : observation.instanceCount,
                observation == null ? 0 : observation.visibleInstanceCount,
                observation == null ? "none" : observation.instanceStates,
                observation == null ? "none" : observation.environmentClass,
                observation == null ? -1 : observation.matrixIndex,
                client.gameRenderer.getMainCamera().getPosition().x,
                client.gameRenderer.getMainCamera().getPosition().y,
                client.gameRenderer.getMainCamera().getPosition().z,
                client.player.getYRot(), client.player.getXRot(),
                observation == null ? 0 : observation.ownedCount,
                observation == null ? 0 : observation.ownerCreated,
                observation == null ? 0 : observation.ownerDeleted,
                observation == null ? 0 : observation.ownerFailedDeletes,
                observation == null ? 0 : observation.ownerIdentityUpdates,
                observation == null ? 0 : observation.ownerCurvedUpdates,
                observation == null ? 0 : observation.ownerMalformedUpdates,
                observation == null ? -1 : observation.ownerVisualIdentity,
                observation == null ? -1 : observation.ownerEmbeddingIdentity,
                observation != null && observation.ownerCurved,
                observation != null && observation.ownerFinitePose,
                observation == null ? "none" : observation.ownerPose,
                projections.curved.logValue(), projections.flat.logValue(),
                projections.reference.logValue());
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1,
                message -> RingWorldMod.LOGGER.info(
                        "[create-kinetic-d1] screenshot {} {}", name, message.getString()));
        captures++;
        phaseIndex++;
        if (phaseIndex < CAPTURE_PHASES.length) return true;
        phaseIndex = 0;
        poseIndex++;
        poseTicks = 0;
        poseRequested = false;
        poseReady = false;
        if (poseIndex == CameraPose.values().length) advance(3);
        return true;
    }

    private boolean verifyRenderOriginRecreation(Minecraft client, RingGeometry geometry) {
        BlockPos target = presentationTarget(geometry, client.player.getX());
        VisualObservation observation = observe(client, target, canonicalTarget(geometry));
        if (offMode()) {
            RingWorldMod.LOGGER.info(
                    "[create-kinetic-d2] origin-recreation skipped backend=flywheel:off owners=0");
            advance(4);
            return true;
        }
        if (observation == null) return true;
        if (!originRecreationRequested) {
            originRecreationRequested = true;
            originVisualIdentity = observation.visualIdentity;
            originEmbeddingIdentity = observation.ownerEmbeddingIdentity;
            VisualizationManager manager = VisualizationManager.get(client.level);
            if (!(manager instanceof dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl
                    exactManager) || exactManager.getEngineImpl() == null) {
                return finish(client, false, "missing exact engine for origin recreation");
            }
            Object engine = exactManager.getEngineImpl();
            Object value = readField(engine, "renderOrigin");
            if (!(value instanceof BlockPos oldOrigin)) {
                return finish(client, false, "unexpected engine render origin " + value);
            }
            setField(engine, "renderOrigin", oldOrigin.offset(10_000, 0, 0));
            RingWorldMod.LOGGER.info(
                    "[create-kinetic-d2] origin-recreation requested oldOrigin={} forcedOrigin={} "
                            + "visualIdentity={} embeddingIdentity={} owned={} created={} deleted={}",
                    oldOrigin, oldOrigin.offset(10_000, 0, 0), originVisualIdentity,
                    originEmbeddingIdentity, observation.ownedCount,
                    observation.ownerCreated, observation.ownerDeleted);
            return true;
        }
        if (observation.visualIdentity == originVisualIdentity
                || observation.ownerEmbeddingIdentity == originEmbeddingIdentity) {
            if (stageTicks > 120) {
                return finish(client, false, "native render-origin recreation did not replace identities");
            }
            return true;
        }
        if (!observation.ownerCurved || !observation.ownerFinitePose
                || observation.ownerFailedDeletes != 0
                || observation.ownerCreated - observation.ownerDeleted != observation.ownedCount) {
            return finish(client, false, "origin recreation leaked or lost child " + observation);
        }
        recreatedVisualIdentity = observation.visualIdentity;
        recreatedEmbeddingIdentity = observation.ownerEmbeddingIdentity;
        RingWorldMod.LOGGER.info(
                "[create-kinetic-d2] origin-recreation PASS oldVisual={} newVisual={} "
                        + "oldEmbedding={} newEmbedding={} owned={} created={} deleted={} balanced=true",
                originVisualIdentity, recreatedVisualIdentity, originEmbeddingIdentity,
                recreatedEmbeddingIdentity, observation.ownedCount,
                observation.ownerCreated, observation.ownerDeleted);
        advance(4);
        return true;
    }

    private boolean verifyRemoval(Minecraft client, RingGeometry geometry) {
        if (!removeRequested) {
            removeRequested = true;
            var server = client.getSingleplayerServer();
            server.execute(() -> runAsync("kinetic remove", () -> {
                ServerLevel level = server.overworld();
                BlockPos target = canonicalTarget(RingWorldServer.geometryFor(level));
                level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(target.south(), Blocks.AIR.defaultBlockState());
                removeReady = true;
            }));
            return true;
        }
        if (!removeReady) return true;
        BlockPos target = presentationTarget(geometry, client.player.getX());
        if (client.level.getBlockEntity(target) != null) return true;
        RingCreate610KineticEmbeddingOwner.Snapshot snapshot = ownerSnapshot(client, null);
        if (offMode()) {
            RingWorldMod.LOGGER.info(
                    "[create-kinetic-d2] remove PASS backend=flywheel:off owned=0 created=0 deleted=0");
        } else {
            if (snapshot.ownedCount() != 0 || snapshot.created() != snapshot.deleted()
                    || snapshot.failedDeletes() != 0) {
                if (stageTicks < 120) return true;
                return finish(client, false, "remove leaked child embeddings " + snapshot);
            }
            RingWorldMod.LOGGER.info(
                    "[create-kinetic-d2] remove PASS owned=0 created={} deleted={} balanced=true",
                    snapshot.created(), snapshot.deleted());
        }
        advance(5);
        return true;
    }

    private boolean verifyReadd(Minecraft client, RingGeometry geometry) {
        if (!readdRequested) {
            readdRequested = true;
            var server = client.getSingleplayerServer();
            server.execute(() -> runAsync("kinetic readd", () -> {
                ServerLevel level = server.overworld();
                BlockPos target = canonicalTarget(RingWorldServer.geometryFor(level));
                placeReproducer(level, target);
                BlockEntity motor = level.getBlockEntity(target.south());
                if (motor == null) throw new IllegalStateException("readded motor is missing");
                setCreativeMotorSpeed(motor, 32);
                readdReady = true;
            }));
            return true;
        }
        if (!readdReady) return true;
        BlockPos target = presentationTarget(geometry, client.player.getX());
        BlockEntity blockEntity = client.level.getBlockEntity(target);
        if (blockEntity == null || kineticSpeed(blockEntity) == 0.0F) return true;
        VisualObservation observation = observe(client, target, canonicalTarget(geometry));
        if (offMode()) {
            if (observation != null) return finish(client, false, "OFF readd created visual");
            RingWorldMod.LOGGER.info(
                    "[create-kinetic-d2] readd PASS backend=flywheel:off owners=0");
            advance(6);
            return true;
        }
        if (observation == null) return true;
        if ((!observation.ownerCurved || !observation.ownerFinitePose)
                && stageTicks < 120) return true;
        if (observation.visualIdentity == recreatedVisualIdentity
                || observation.ownerEmbeddingIdentity == recreatedEmbeddingIdentity
                || observation.ownerCreated - observation.ownerDeleted != observation.ownedCount
                || observation.ownerFailedDeletes != 0
                || !observation.ownerCurved || !observation.ownerFinitePose) {
            return finish(client, false, "readd identity/leak failure " + observation);
        }
        RingWorldMod.LOGGER.info(
                "[create-kinetic-d2] readd PASS oldVisual={} newVisual={} oldEmbedding={} "
                        + "newEmbedding={} owned={} created={} deleted={} balanced=true",
                recreatedVisualIdentity, observation.visualIdentity,
                recreatedEmbeddingIdentity, observation.ownerEmbeddingIdentity,
                observation.ownedCount, observation.ownerCreated, observation.ownerDeleted);
        advance(6);
        return true;
    }

    private boolean finishAfterLifecycle(Minecraft client) {
        if (stageTicks < 20) return true;
        return finish(client, !missingVisualAtBaseline,
                (missingVisualAtBaseline ? "classification=visual-never-created " : "")
                + "backend=" + backend() + " route=" + route().id
                + " mechanism=encased-cog captures=" + captures
                + " fixedCameraPhases=3 poses=4 phase=" + phase()
                + " productionClientMixins=6");
    }

    private void requestCameraPose(Minecraft client, RingGeometry geometry, CameraPose pose) {
        var server = client.getSingleplayerServer();
        UUID playerUuid = client.player.getUUID();
        server.execute(() -> runAsync("camera pose " + pose.id, () -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) throw new IllegalStateException("missing player for camera pose");
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            double cameraX = cameraCanonicalX(RingWorldServer.geometryFor(server.overworld())) + 0.5;
            double cameraZ = canonicalTarget(geometry).getZ() - pose.distance;
            player.teleportTo(server.overworld(), cameraX, Y, cameraZ,
                    Set.of(), 0.0F, 0.0F);
            poseReady = true;
            RingWorldMod.LOGGER.info(
                    "[create-kinetic-d1] camera-pose route={} view={} canonicalX={} z={} "
                            + "yawOffset={} trackingRangeBlocks=320",
                    route().id, pose.id, cameraX, cameraZ, pose.yawOffset);
        }));
    }

    private static ProjectionSet projections(
            Minecraft client, RingGeometry geometry, BlockPos target, double yawOffset) {
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        List<Vec3> targetPoints = blockPoints(target);
        RingCreate610FixtureProjection.Aim aim = RingCreate610FixtureProjection.aim(
                geometry, camera, targetPoints, yawOffset,
                client.getMainRenderTarget().width, client.getMainRenderTarget().height, 70.0);
        List<Vec3> flatLocal = targetPoints.stream().map(point -> point.subtract(camera)).toList();
        Vec3 flatCenter = flatLocal.stream().reduce(Vec3.ZERO, Vec3::add)
                .scale(1.0 / flatLocal.size());
        RingCreate610FixtureProjection.Projection flat =
                RingCreate610FixtureProjection.projectCameraLocal(
                        flatLocal, flatCenter, aim.yaw(), aim.pitch(),
                        client.getMainRenderTarget().width, client.getMainRenderTarget().height, 70.0);
        // The left gold block is a single static control ROI; the other palette blocks remain
        // available for human framing review without making the stability ROI span the cog.
        List<Vec3> referencePoints = blockPoints(referencePositions(target).getFirst());
        List<Vec3> referenceLocal = referencePoints.stream()
                .map(point -> geometry.toCameraLocal(point, camera)).toList();
        Vec3 referenceCenter = referenceLocal.stream().reduce(Vec3.ZERO, Vec3::add)
                .scale(1.0 / referenceLocal.size());
        RingCreate610FixtureProjection.Projection reference =
                RingCreate610FixtureProjection.projectCameraLocal(
                        referenceLocal, referenceCenter, aim.yaw(), aim.pitch(),
                        client.getMainRenderTarget().width, client.getMainRenderTarget().height, 70.0);
        return new ProjectionSet(aim, aim.projection(), flat, reference);
    }

    private static VisualObservation observe(
            Minecraft client, BlockPos presentationTarget, BlockPos canonicalTarget) {
        VisualizationManager manager = VisualizationManager.get(client.level);
        if (manager == null) return null;
        if (!(manager.blockEntities() instanceof VisualManagerImpl<?, ?> visualManager)) {
            throw new IllegalStateException("unexpected Flywheel visual manager "
                    + manager.blockEntities().getClass().getName());
        }
        if (!(visualManager.getStorage() instanceof BlockEntityStorage storage)) {
            throw new IllegalStateException("unexpected Flywheel block storage "
                    + visualManager.getStorage().getClass().getName());
        }
        BlockPos storageLookup = presentationTarget;
        BlockEntityVisual<?> visual = storage.visualAtPos(presentationTarget.asLong());
        if (visual == null && !canonicalTarget.equals(presentationTarget)) {
            storageLookup = canonicalTarget;
            visual = storage.visualAtPos(canonicalTarget.asLong());
        }
        if (visual == null) return null;
        if (!(visual instanceof AbstractBlockEntityVisual<?> blockVisual)) {
            throw new IllegalStateException("unexpected non-block visual " + visual.getClass().getName());
        }
        BlockEntity blockEntity = client.level.getBlockEntity(storageLookup);
        RingCreate610KineticEmbeddingOwner.Snapshot ownerSnapshot =
                ((RingCreate610KineticEmbeddingAccess) (Object) storage)
                        .ringworld$kineticEmbeddingSnapshot(blockEntity);
        List<RotatingInstance> instances = rotatingInstances(visual);
        if (instances.isEmpty()) throw new IllegalStateException("visual has no rotating instances");
        RotatingInstance instance = instances.getFirst();
        String environmentClass = "unknown";
        int matrixIndex = -1;
        int visibleInstances = 0;
        List<String> states = new ArrayList<>();
        for (RotatingInstance candidate : instances) {
            if (candidate.handle() instanceof InstanceHandleImpl<?> handle) {
                states.add(handle.state == null ? "null" : handle.state.getClass().getName());
                Object instancerOwner = handle.state instanceof AbstractInstancer<?>
                        ? handle.state : readFieldOrNull(handle.state, "parent");
                if (instancerOwner instanceof AbstractInstancer<?> instancer
                        && !(handle.state instanceof InstanceHandleImpl.Hidden<?>)) {
                    visibleInstances++;
                    if ("unknown".equals(environmentClass)) {
                        instance = candidate;
                        environmentClass = instancer.environment.getClass().getName();
                        matrixIndex = instancer.environment.matrixIndex();
                    }
                }
            }
        }
        return new VisualObservation(
                visual.getClass().getName(), System.identityHashCode(visual), storageLookup,
                blockVisual.getVisualPosition(), manager.renderOrigin(),
                instance.x, instance.y, instance.z,
                instance.rotationalSpeed, instance.rotationOffset,
                visibleInstances > 0, instances.size(), visibleInstances,
                String.join(",", states), environmentClass, matrixIndex,
                ownerSnapshot.ownedCount(), ownerSnapshot.created(), ownerSnapshot.deleted(),
                ownerSnapshot.failedDeletes(),
                ownerSnapshot.identityUpdates(), ownerSnapshot.curvedUpdates(),
                ownerSnapshot.malformedUpdates(), ownerSnapshot.visualIdentity(),
                ownerSnapshot.embeddingIdentity(), ownerSnapshot.curved(),
                ownerSnapshot.finitePose(), matrix(ownerSnapshot.pose()));
    }

    private static List<RotatingInstance> rotatingInstances(Object visual) {
        List<RotatingInstance> result = new ArrayList<>();
        for (Class<?> type = visual.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!RotatingInstance.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(visual);
                    if (value instanceof RotatingInstance instance && !result.contains(instance)) {
                        result.add(instance);
                    }
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("could not inspect " + field, failure);
                }
            }
        }
        return List.copyOf(result);
    }

    private static Object readField(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // Continue through the exact visual hierarchy.
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("could not read " + name + " from " + target, failure);
            }
        }
        throw new IllegalStateException("missing field " + name + " on " + target.getClass().getName());
    }

    private static Object readFieldOrNull(Object target, String name) {
        if (target == null) return null;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // Continue through the exact state hierarchy.
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("could not read " + name + " from " + target, failure);
            }
        }
        return null;
    }

    private static void setField(Object target, String name, Object value) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // Continue through the exact engine hierarchy.
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("could not set " + name + " on " + target, failure);
            }
        }
        throw new IllegalStateException("missing field " + name + " on " + target.getClass().getName());
    }

    private static RingCreate610KineticEmbeddingOwner.Snapshot ownerSnapshot(
            Minecraft client, BlockEntity blockEntity) {
        VisualizationManager manager = VisualizationManager.get(client.level);
        if (manager == null || !(manager.blockEntities() instanceof VisualManagerImpl<?, ?> visualManager)
                || !(visualManager.getStorage() instanceof BlockEntityStorage storage)) {
            return new RingCreate610KineticEmbeddingOwner.Snapshot(
                    0, 0, 0, 0, 0, 0, 0, -1, -1, false, new org.joml.Matrix4f());
        }
        return ((RingCreate610KineticEmbeddingAccess) (Object) storage)
                .ringworld$kineticEmbeddingSnapshot(blockEntity);
    }

    private boolean driveToRouteChart(Minecraft client, RingGeometry geometry) {
        double target = cameraCanonicalX(geometry) + 0.5;
        double x = client.player.getX();
        if (Math.abs(x - target) <= 4.0) return true;
        if (route() == Route.HIGH) {
            if (chartHop == 0 && x < 100.0) {
                chartHop = 1;
                teleportPlayer(client, 800.5, 40.0);
            } else if (chartHop == 1 && Math.abs(x - 800.5) < 8.0) {
                chartHop = 2;
                teleportPlayer(client, 1_600.5, 40.0);
            } else if (chartHop == 2 && Math.abs(x - 1_600.5) < 8.0) {
                chartHop = 3;
                teleportPlayer(client, target, 40.0);
            }
        } else {
            teleportPlayer(client, target, 40.0);
        }
        return false;
    }

    private static void teleportPlayer(Minecraft client, double x, double z) {
        var server = client.getSingleplayerServer();
        UUID playerUuid = client.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player != null) player.teleportTo(server.overworld(), x, Y, z,
                    Set.of(), 0.0F, 0.0F);
        });
    }

    private static void configureCamera(Minecraft client) {
        client.options.setCameraType(CameraType.FIRST_PERSON);
        client.options.fov().set(70);
        client.options.hideGui = true;
    }

    private static void orientClient(Minecraft client, float yaw, float pitch) {
        client.player.setYRot(yaw);
        client.player.yRotO = yaw;
        client.player.setYHeadRot(yaw);
        client.player.setXRot(pitch);
        client.player.xRotO = pitch;
    }

    private static void prepareArena(ServerLevel level, BlockPos target) {
        level.setDayTime(6_000L);
        level.setWeatherParameters(0, 120_000, false, false);
        for (int x = target.getX() - 14; x <= target.getX() + 14; x++) {
            for (int z = target.getZ() - 112; z <= target.getZ() + 8; z++) {
                level.setBlockAndUpdate(new BlockPos(x, Y - 3, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = Y - 2; y <= Y + 8; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int x = target.getX() - 6; x <= target.getX() + 6; x++) {
            for (int y = Y - 5; y <= Y + 5; y++) {
                level.setBlockAndUpdate(new BlockPos(x, y, target.getZ() + 3),
                        Blocks.WHITE_CONCRETE.defaultBlockState());
            }
        }
    }

    private static void placeReproducer(ServerLevel level, BlockPos target) {
        BlockState cog = withProperty(withProperty(
                block("create:andesite_encased_large_cogwheel").defaultBlockState(), "axis", "z"),
                "top_shaft", "true");
        level.setBlockAndUpdate(target, cog);
        BlockState motor = withProperty(
                block("create:creative_motor").defaultBlockState(), "facing", "north");
        level.setBlockAndUpdate(target.south(), motor);
        List<BlockPos> references = referencePositions(target);
        BlockState[] states = {
                Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.MAGENTA_CONCRETE.defaultBlockState(),
                Blocks.LIME_CONCRETE.defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState()
        };
        for (int index = 0; index < references.size(); index++) {
            level.setBlockAndUpdate(references.get(index), states[index]);
        }
    }

    private static List<BlockPos> referencePositions(BlockPos target) {
        return List.of(target.offset(-2, 0, 0), target.offset(2, 0, 0),
                target.offset(0, 2, 0), target.offset(0, -2, 0));
    }

    private static List<Vec3> blockPoints(BlockPos position) {
        List<Vec3> points = new ArrayList<>(9);
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    points.add(new Vec3(position.getX() + x, position.getY() + y, position.getZ() + z));
                }
            }
        }
        points.add(Vec3.atCenterOf(position));
        return List.copyOf(points);
    }

    private static float kineticPhase(Minecraft client, float speed, float offset) {
        float phase = (client.level.getGameTime() * speed * 3.0F / 10.0F + offset) % 360.0F;
        return phase < 0.0F ? phase + 360.0F : phase;
    }

    private static float kineticSpeed(BlockEntity blockEntity) {
        try {
            Object value = blockEntity.getClass().getMethod("getSpeed").invoke(blockEntity);
            if (value instanceof Number number) return number.floatValue();
            throw new IllegalStateException("getSpeed returned " + value);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                    "could not observe exact Create kinetic speed on "
                            + blockEntity.getClass().getName(), failure);
        }
    }

    private static void setCreativeMotorSpeed(BlockEntity blockEntity, int speed) {
        Object behaviour = readField(blockEntity, "generatedSpeed");
        try {
            behaviour.getClass().getMethod("setValue", int.class).invoke(behaviour, speed);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not apply ordinary Create motor control", failure);
        }
    }

    private static float angularDistance(float first, float second) {
        float delta = Math.abs(first - second) % 360.0F;
        return Math.min(delta, 360.0F - delta);
    }

    private static BlockPos canonicalTarget(RingGeometry geometry) {
        int x = switch (route()) {
            case NORMAL -> geometry.circumferenceBlocks() / 4;
            case HIGH -> 70;
            case LOW -> geometry.circumferenceBlocks() - 70;
        };
        return new BlockPos(x, Y, 100);
    }

    private static int cameraCanonicalX(RingGeometry geometry) {
        return switch (route()) {
            case NORMAL -> geometry.circumferenceBlocks() / 4 - 74;
            case HIGH -> geometry.circumferenceBlocks() - 4;
            case LOW -> 4;
        };
    }

    private static BlockPos presentationTarget(RingGeometry geometry, double referenceX) {
        BlockPos canonical = canonicalTarget(geometry);
        return dev.ringworld.world.RingBlockCoordinates.nearestImageBlockPos(
                canonical, referenceX, geometry);
    }

    private static String backend() {
        return Backend.REGISTRY.getIdOrThrow(BackendManager.currentBackend()).toString();
    }

    private static String requestedBackend() {
        return System.getProperty(BACKEND_PROPERTY, "default");
    }

    private static boolean offMode() { return "off".equals(requestedBackend()); }

    private static boolean d2Mode() { return "d2".equals(phase()); }

    private static String phase() {
        return System.getProperty(PHASE_PROPERTY, "d1");
    }

    private static Route route() {
        return switch (System.getProperty(ROUTE_PROPERTY, "high")) {
            case "normal" -> Route.NORMAL;
            case "high" -> Route.HIGH;
            case "low" -> Route.LOW;
            default -> throw new IllegalStateException("unknown kinetic visual route "
                    + System.getProperty(ROUTE_PROPERTY));
        };
    }

    private void advance(int next) { stage = next; stageTicks = 0; }

    private void runAsync(String label, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            asynchronousFailure = label + ": " + failure;
            RingWorldMod.LOGGER.error("[create-kinetic-d1] async failure {}", label, failure);
        }
    }

    private static boolean finish(Minecraft client, boolean passed, String detail) {
        RingCreate610ClientDiagnostics.suppressKineticEmbeddingGeometryForFixture(false);
        RingWorldMod.LOGGER.info("[create-kinetic-d1] result={} {}", passed, detail);
        client.stop();
        return true;
    }

    private static Block block(String id) {
        Block value = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse(id));
        if (value == Blocks.AIR) throw new IllegalStateException("missing registered block " + id);
        return value;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withProperty(BlockState state, String name, String value) {
        Property property = state.getProperties().stream()
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "missing property " + name + " on " + state));
        var parsedValue = property.getValue(value);
        if (parsedValue.isEmpty()) {
            throw new IllegalStateException(
                    "invalid property " + name + "=" + value + " on " + state);
        }
        Comparable parsed = (Comparable) parsedValue.get();
        return state.setValue(property, parsed);
    }

    private static String blockName(BlockState state) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock())
                + state.getValues().toString();
    }

    private static String describeBlockEntity(BlockEntity blockEntity) {
        return blockEntity == null ? "none"
                : blockEntity.getClass().getName() + "@" + blockEntity.getBlockPos()
                        + "/speed=" + kineticSpeed(blockEntity);
    }

    private record ProjectionSet(
            RingCreate610FixtureProjection.Aim aim,
            RingCreate610FixtureProjection.Projection curved,
            RingCreate610FixtureProjection.Projection flat,
            RingCreate610FixtureProjection.Projection reference) { }

    private record VisualObservation(
            String visualClass, int visualIdentity, BlockPos storageLookup, BlockPos visualPos,
            net.minecraft.core.Vec3i renderOrigin,
            float instanceX, float instanceY, float instanceZ,
            float rotationalSpeed, float rotationOffset, boolean instanceVisible,
            int instanceCount, int visibleInstanceCount, String instanceStates,
            String environmentClass, int matrixIndex,
            int ownedCount, long ownerCreated, long ownerDeleted, long ownerFailedDeletes,
            long ownerIdentityUpdates, long ownerCurvedUpdates, long ownerMalformedUpdates,
            int ownerVisualIdentity, int ownerEmbeddingIdentity, boolean ownerCurved,
            boolean ownerFinitePose, String ownerPose) {
        String instancePosition() {
            return String.format(Locale.ROOT, "%.3f/%.3f/%.3f", instanceX, instanceY, instanceZ);
        }
    }

    private static String matrix(org.joml.Matrix4f matrix) {
        float[] values = matrix.get(new float[16]);
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) result.append('/');
            result.append(String.format(Locale.ROOT, "%.5f", values[index]));
        }
        return result.toString();
    }

    private enum CameraPose {
        CENTER_NEAR("center-near", 28.0, 0.0),
        EDGE_NEAR("edge-near", 28.0, -20.0),
        CENTER_FAR("center-far", 85.0, 0.0),
        EDGE_FAR("edge-far", 85.0, -18.0);

        private final String id;
        private final double distance;
        private final double yawOffset;

        CameraPose(String id, double distance, double yawOffset) {
            this.id = id;
            this.distance = distance;
            this.yawOffset = yawOffset;
        }
    }

    private enum Route {
        NORMAL("normal"), HIGH("high"), LOW("low");

        private final String id;

        Route(String id) { this.id = id; }
    }
}
