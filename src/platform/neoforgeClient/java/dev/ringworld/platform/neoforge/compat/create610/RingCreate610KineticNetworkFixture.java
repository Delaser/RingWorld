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
import dev.ringworld.client.compat.ClientWorldLifecycle;
import dev.ringworld.client.compat.Screenshot;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingBlockCoordinates;
import dev.ringworld.world.RingGeometry;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Disposable D3 fixture for the exact Create 6.0.10 standalone kinetic visual matrix.
 * Production code never references this class.
 */
public final class RingCreate610KineticNetworkFixture {
    public static final String ENABLE_PROPERTY = "ringworld.createCompatKineticNetwork";
    public static final String ROUTE_PROPERTY = "ringworld.createCompatKineticNetworkRoute";
    public static final String BACKEND_PROPERTY = "ringworld.createCompatKineticNetworkBackend";
    private static final String WORLD_NAME = "RingWorld Create Kinetic Network";
    private static final int Y = 120;
    private static final int TIMEOUT_TICKS = 4_800;
    private static final int SETTLE_TICKS = 80;
    private static final RingCreate610KineticNetworkFixture INSTANCE =
            new RingCreate610KineticNetworkFixture();

    private boolean worldScreenOpened;
    private boolean worldStarted;
    private boolean backendChecked;
    private boolean setupRequested;
    private volatile boolean setupReady;
    private volatile String asynchronousFailure;
    private int chartHop;
    private int ticks;
    private int stage;
    private int stageTicks;
    private int captureIndex;
    private boolean poseRequested;
    private volatile boolean poseReady;
    private int poseTicks;
    private int captures;
    private int expectedVisuals;
    private boolean originRequested;
    private long originCreated;
    private long originDeleted;
    private boolean removeRequested;
    private volatile boolean removeReady;
    private boolean readdRequested;
    private volatile boolean readdReady;
    private boolean unloadRequested;
    private boolean disconnectRequested;
    private boolean disconnectCleared;
    private boolean reopenRequested;
    private boolean reopenedReady;
    private long frameBaseline;
    private long lastFrameNanos;
    private final List<Double> frameMillis = new ArrayList<>();
    private Map<String, Integer> stableVisuals = Map.of();

    private RingCreate610KineticNetworkFixture() { }

    public static RingCreate610KineticNetworkFixture instance() { return INSTANCE; }

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
            creator.setName(WORLD_NAME + " " + route().id + " " + requestedBackend());
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
        if (client.player == null || client.level == null || client.screen != null) {
            if (stage == 10) waitForDisconnect(client);
            else if (stage == 11) reopenWorld(client);
            return true;
        }
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || geometry.circumferenceBlocks() != 2_048) return true;
        if (!backendChecked) {
            backendChecked = true;
            if (!"default".equals(requestedBackend())
                    && !backend().equals("flywheel:" + requestedBackend())) {
                return finish(client, false, "capability-rejected requested=flywheel:"
                        + requestedBackend() + " actual=" + backend());
            }
        }
        configureCamera(client);
        ClientRingState.updateCameraPosition(client.player.getX());
        stageTicks++;
        return switch (stage) {
            case 0 -> establishChartAndSetup(client, geometry);
            case 1 -> waitForNetwork(client, geometry);
            case 2 -> captureViews(client, geometry);
            case 3 -> verifyOriginRecreation(client, geometry);
            case 4 -> verifyRemove(client, geometry);
            case 5 -> verifyReadd(client, geometry);
            case 6 -> requestChunkUnload(client, geometry);
            case 7 -> verifyChunkUnload(client, geometry);
            case 9 -> requestDurableDisconnect(client);
            case 10 -> waitForDisconnect(client);
            case 11 -> reopenWorld(client);
            case 12 -> verifyReopenedNetwork(client, geometry);
            case 13 -> teardown(client, geometry);
            default -> finish(client, false, "invalid stage=" + stage);
        };
    }

    public void frameRendered() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || stage < 1 || stage > 5) return;
        long now = System.nanoTime();
        if (lastFrameNanos != 0L && frameBaseline > 0L) {
            frameMillis.add((now - lastFrameNanos) / 1_000_000.0);
        }
        lastFrameNanos = now;
        frameBaseline++;
    }

    private boolean establishChartAndSetup(Minecraft client, RingGeometry geometry) {
        if (!driveToRouteChart(client, geometry)) return true;
        if (setupRequested) return true;
        setupRequested = true;
        var server = client.getSingleplayerServer();
        UUID playerUuid = client.player.getUUID();
        server.execute(() -> runAsync("kinetic network setup", () -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) throw new IllegalStateException("missing integrated player");
            RingGeometry serverGeometry = RingWorldServer.geometryFor(level);
            BlockPos origin = canonicalOrigin(serverGeometry);
            prepareArena(level, origin);
            Map<String, Module> modules = placeNetwork(level, origin);
            if (densityEnabled()) placeDensity(level, densityOrigin(origin));
            for (Module module : modules.values()) {
                BlockEntity blockEntity = level.getBlockEntity(module.position());
                if (blockEntity == null) {
                    throw new IllegalStateException("missing module " + module.id()
                            + " at " + module.position());
                }
                float speed = kineticSpeed(blockEntity);
                RingWorldMod.LOGGER.info(
                        "[create-kinetic-d3] server-module id={} canonical={} block={} "
                                + "class={} speed={} state={} ownership=canonical",
                        module.id(), module.position(), blockName(blockEntity.getBlockState()),
                        blockEntity.getClass().getName(), speed, module.state());
            }
            expectedVisuals = modules.size() + modules.values().stream()
                    .map(Module::power).filter(java.util.Objects::nonNull).distinct().toList().size()
                    + modules.values().stream().map(Module::support)
                            .filter(java.util.Objects::nonNull).map(Support::position)
                            .distinct().toList().size()
                    + (densityEnabled() ? 128 : 0);
            setupReady = true;
            RingWorldMod.LOGGER.info(
                    "[create-kinetic-d3] setup queued route={} modules={} "
                            + "densityVisuals={} expectedVisuals={} origin={} ownership=canonical",
                    route().id, modules.size(), densityEnabled() ? 128 : 0,
                    expectedVisuals, origin);
        }));
        advance(1);
        return true;
    }

    private boolean waitForNetwork(Minecraft client, RingGeometry geometry) {
        if (!setupReady || stageTicks < SETTLE_TICKS) return true;
        List<Observation> observations = observeModules(client, geometry);
        if (stageTicks % 100 == 0) {
            RingCreate610KineticEmbeddingOwner.Snapshot snapshot = ownerSnapshot(client, null);
            RingWorldMod.LOGGER.info(
                    "[create-kinetic-d3] waiting stageTicks={} observations={} ownerSnapshot={}",
                    stageTicks, observations, snapshot);
        }
        if (observations.size() != moduleSpecs(canonicalOrigin(geometry)).size()) {
            if (stageTicks < 600) return true;
            return finish(client, false, "missing module observations " + observations.size());
        }
        if (offMode()) {
            if (observations.stream().anyMatch(observation -> observation.visualClass() != null)) {
                return finish(client, false, "OFF created Flywheel visual " + observations);
            }
        } else {
            for (Observation observation : observations) {
                if (observation.visualClass() == null
                        || observation.speed() == 0.0F && !observation.stationaryController()
                        || observation.matrixIndex() <= 0 && backend().equals("flywheel:indirect")
                        || !observation.ownerCurved() || !observation.ownerFinite()
                        || observation.ownerFailedDeletes() != 0) {
                    if (stageTicks < 600) return true;
                    return finish(client, false, "invalid live module " + observation);
                }
            }
            RingCreate610KineticEmbeddingOwner.Snapshot snapshot = ownerSnapshot(client, null);
            if (snapshot.ownedCount() < expectedVisuals) {
                if (stageTicks < 600) return true;
                return finish(client, false, "owned embeddings " + snapshot.ownedCount()
                        + " < expected " + expectedVisuals);
            }
            stableVisuals = observations.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Observation::id, Observation::visualIdentity));
        }
        RingWorldMod.LOGGER.info(
                "[create-kinetic-d3] baseline PASS backend={} route={} modules={} "
                        + "expectedVisuals={} observations={}",
                backend(), route().id, observations.size(), expectedVisuals, observations);
        advance(2);
        return true;
    }

    private boolean captureViews(Minecraft client, RingGeometry geometry) {
        Capture capture = Capture.values()[captureIndex];
        if (!poseRequested) {
            requestCameraPose(client, geometry, capture);
            poseRequested = true;
            return true;
        }
        if (!poseReady) return true;
        poseTicks++;
        List<Observation> observations = observeModules(client, geometry);
        if (observations.size() != moduleSpecs(canonicalOrigin(geometry)).size()) return true;
        ProjectionSet projections = projections(client, geometry, observations, capture.yawOffset);
        orientClient(client, projections.aim().yaw(), projections.aim().pitch());
        if (poseTicks < capture.settleTicks) return true;
        if (!projections.allInView(client)) {
            return finish(client, false, "network projection is not in view capture=" + capture.id
                    + " projections=" + projections.moduleBounds());
        }
        if (!offMode()) {
            for (Observation observation : observations) {
                Integer expectedIdentity = stableVisuals.get(observation.id());
                if (expectedIdentity == null || expectedIdentity != observation.visualIdentity()
                        || !observation.ownerCurved() || !observation.ownerFinite()
                        || observation.ownerFailedDeletes() != 0) {
                    return finish(client, false, "visual continuity failure " + observation);
                }
            }
        }
        String name = "ringworld-create-kinetic-d3-" + backend().replace(':', '-')
                + "-" + route().id + "-" + capture.id;
        RingCreate610KineticEmbeddingOwner.Snapshot total = ownerSnapshot(client, null);
        RingWorldMod.LOGGER.info(
                "[create-kinetic-d3] capture-proof name={} relative=screenshots/{}.png "
                        + "backend={} route={} capture={} gameTime={} camera={}/{}/{} yaw={} pitch={} "
                        + "modules={} curvedBounds={} flatBounds={} referenceBounds={} "
                        + "owned={} created={} deleted={} failedDeletes={} expectedVisible=true",
                name, name, backend(), route().id, capture.id, client.level.getGameTime(),
                client.gameRenderer.getMainCamera().getPosition().x,
                client.gameRenderer.getMainCamera().getPosition().y,
                client.gameRenderer.getMainCamera().getPosition().z,
                client.player.getYRot(), client.player.getXRot(),
                observations, encodeBounds(projections.moduleBounds()),
                encodeBounds(projections.flatBounds()), projections.reference().logValue(),
                total.ownedCount(), total.created(), total.deleted(), total.failedDeletes());
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1,
                message -> RingWorldMod.LOGGER.info(
                        "[create-kinetic-d3] screenshot {} {}", name, message.getString()));
        captures++;
        captureIndex++;
        poseRequested = false;
        poseReady = false;
        poseTicks = 0;
        if (captureIndex == Capture.values().length) advance(3);
        return true;
    }

    private boolean verifyOriginRecreation(Minecraft client, RingGeometry geometry) {
        if (offMode()) {
            RingWorldMod.LOGGER.info(
                    "[create-kinetic-d3] origin-recreation skipped backend=flywheel:off owned=0");
            advance(4);
            return true;
        }
        RingCreate610KineticEmbeddingOwner.Snapshot snapshot = ownerSnapshot(client, null);
        if (!originRequested) {
            originRequested = true;
            originCreated = snapshot.created();
            originDeleted = snapshot.deleted();
            VisualizationManager manager = VisualizationManager.get(client.level);
            if (!(manager instanceof dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl exact)
                    || exact.getEngineImpl() == null) {
                return finish(client, false, "missing exact engine for origin recreation");
            }
            Object engine = exact.getEngineImpl();
            Object value = readField(engine, "renderOrigin");
            if (!(value instanceof BlockPos oldOrigin)) {
                return finish(client, false, "unexpected render origin " + value);
            }
            setField(engine, "renderOrigin", oldOrigin.offset(10_000, 0, 0));
            return true;
        }
        if (snapshot.created() <= originCreated || snapshot.deleted() <= originDeleted) {
            if (stageTicks < 180) return true;
            return finish(client, false, "origin recreation did not rebuild owners " + snapshot);
        }
        if (snapshot.ownedCount() < expectedVisuals
                || snapshot.created() - snapshot.deleted() != snapshot.ownedCount()
                || snapshot.failedDeletes() != 0) {
            return finish(client, false, "origin recreation ownership failure " + snapshot);
        }
        RingWorldMod.LOGGER.info(
                "[create-kinetic-d3] origin-recreation PASS owned={} created={} deleted={} "
                        + "failedDeletes=0 balanced=true",
                snapshot.ownedCount(), snapshot.created(), snapshot.deleted());
        advance(4);
        return true;
    }

    private boolean verifyRemove(Minecraft client, RingGeometry geometry) {
        if (!removeRequested) {
            removeRequested = true;
            var server = client.getSingleplayerServer();
            server.execute(() -> runAsync("network remove", () -> {
                removeNetwork(server.overworld(), canonicalOrigin(geometry));
                removeReady = true;
            }));
            return true;
        }
        if (!removeReady) return true;
        RingCreate610KineticEmbeddingOwner.Snapshot snapshot = ownerSnapshot(client, null);
        if (snapshot.ownedCount() != 0) {
            if (stageTicks < 240) return true;
            return finish(client, false, "network remove leaked owners " + snapshot);
        }
        if (!offMode() && (snapshot.created() != snapshot.deleted()
                || snapshot.failedDeletes() != 0)) {
            return finish(client, false, "network remove imbalance " + snapshot);
        }
        RingWorldMod.LOGGER.info(
                "[create-kinetic-d3] remove PASS owned=0 created={} deleted={} failedDeletes={} balanced={}",
                snapshot.created(), snapshot.deleted(), snapshot.failedDeletes(),
                snapshot.created() == snapshot.deleted() && snapshot.failedDeletes() == 0);
        advance(5);
        return true;
    }

    private boolean verifyReadd(Minecraft client, RingGeometry geometry) {
        if (!readdRequested) {
            readdRequested = true;
            var server = client.getSingleplayerServer();
            server.execute(() -> runAsync("network readd", () -> {
                placeNetwork(server.overworld(), canonicalOrigin(geometry));
                if (densityEnabled()) placeDensity(server.overworld(), densityOrigin(canonicalOrigin(geometry)));
                readdReady = true;
            }));
            return true;
        }
        if (!readdReady || stageTicks < SETTLE_TICKS) return true;
        List<Observation> observations = observeModules(client, geometry);
        if (observations.size() != moduleSpecs(canonicalOrigin(geometry)).size()) return true;
        RingCreate610KineticEmbeddingOwner.Snapshot snapshot = ownerSnapshot(client, null);
        if (!offMode() && (snapshot.ownedCount() < expectedVisuals
                || snapshot.created() - snapshot.deleted() != snapshot.ownedCount()
                || snapshot.failedDeletes() != 0)) {
            return finish(client, false, "network readd ownership failure " + snapshot);
        }
        RingWorldMod.LOGGER.info(
                "[create-kinetic-d3] readd PASS modules={} owned={} created={} deleted={} "
                        + "failedDeletes={} generation=new",
                observations.size(), snapshot.ownedCount(), snapshot.created(), snapshot.deleted(),
                snapshot.failedDeletes());
        advance(6);
        return true;
    }

    private boolean requestChunkUnload(Minecraft client, RingGeometry geometry) {
        if (unloadRequested) return true;
        unloadRequested = true;
        BlockPos presentationOrigin = presentationPosition(
                canonicalOrigin(geometry), client.player.getX(), geometry);
        Set<ChunkPos> chunks = networkChunks(presentationOrigin);
        int unloaded = 0;
        for (ChunkPos chunk : chunks) {
            if (!client.level.hasChunk(chunk.x, chunk.z)) continue;
            client.level.unload(client.level.getChunk(chunk.x, chunk.z));
            unloaded++;
        }
        RingWorldMod.LOGGER.info(
                "[create-kinetic-d3] client-chunk-unload requested chunks={} unloaded={} "
                        + "route={} serverOwnership=unchanged",
                chunks.size(), unloaded, route().id);
        advance(7);
        return true;
    }

    private boolean verifyChunkUnload(Minecraft client, RingGeometry geometry) {
        RingCreate610KineticEmbeddingOwner.Snapshot snapshot = ownerSnapshot(client, null);
        if (snapshot.ownedCount() != 0) {
            if (stageTicks < 120) return true;
            return finish(client, false, "chunk unload retained owners " + snapshot);
        }
        if (!offMode() && (snapshot.created() != snapshot.deleted()
                || snapshot.failedDeletes() != 0)) {
            return finish(client, false, "chunk unload imbalance " + snapshot);
        }
        RingWorldMod.LOGGER.info(
                "[create-kinetic-d3] client-chunk-unload PASS owned=0 created={} deleted={} "
                        + "failedDeletes={} balanced={} serverOwnership=unchanged",
                snapshot.created(), snapshot.deleted(), snapshot.failedDeletes(),
                snapshot.created() == snapshot.deleted() && snapshot.failedDeletes() == 0);
        // One ordinary indirect low-chart run owns the durable same-process reopen
        // claim. Other backends end after the same exact unload balance boundary;
        // repeating world persistence adds no distinct visual-path coverage.
        advance(durableReopenEnabled() ? 9 : 13);
        return true;
    }

    private boolean requestDurableDisconnect(Minecraft client) {
        if (disconnectRequested || stageTicks < 10) return true;
        disconnectRequested = true;
        RingCreate610KineticEmbeddingOwner.Snapshot snapshot = ownerSnapshot(client, null);
        RingWorldMod.LOGGER.info(
                "[create-kinetic-d3] requesting durable save-and-disconnect route={} backend={} "
                        + "owned={} created={} deleted={} failedDeletes={}",
                route().id, backend(), snapshot.ownedCount(), snapshot.created(),
                snapshot.deleted(), snapshot.failedDeletes());
        ClientWorldLifecycle.disconnect(client,
                Component.literal("RingWorld Create kinetic D3 durable qualification"));
        advance(10);
        return true;
    }

    private boolean waitForDisconnect(Minecraft client) {
        if (client.level != null || client.getSingleplayerServer() != null) return true;
        disconnectCleared = ClientRingState.sessionCleared();
        if (!disconnectCleared) return true;
        RingWorldMod.LOGGER.info(
                "[create-kinetic-d3] durable disconnect complete clientStateCleared=true");
        advance(11);
        return true;
    }

    private boolean reopenWorld(Minecraft client) {
        if (client.level != null || client.getSingleplayerServer() != null || reopenRequested) {
            return true;
        }
        reopenRequested = true;
        chartHop = 0;
        String worldName = WORLD_NAME + " " + route().id + " " + requestedBackend();
        RingWorldMod.LOGGER.info(
                "[create-kinetic-d3] reopening durable world name={} route={} backend={}",
                worldName, route().id, requestedBackend());
        client.createWorldOpenFlows().openWorld(worldName,
                () -> finish(client, false, "durable kinetic-network reopen cancelled"));
        advance(12);
        return true;
    }

    private boolean verifyReopenedNetwork(Minecraft client, RingGeometry geometry) {
        if (!driveToRouteChart(client, geometry) || stageTicks < SETTLE_TICKS) return true;
        List<Observation> observations = observeModules(client, geometry);
        if (observations.size() != moduleSpecs(canonicalOrigin(geometry)).size()) return true;
        RingCreate610KineticEmbeddingOwner.Snapshot snapshot = ownerSnapshot(client, null);
        if (!offMode() && (snapshot.ownedCount() < expectedVisuals
                || snapshot.created() - snapshot.deleted() != snapshot.ownedCount()
                || snapshot.failedDeletes() != 0)) {
            if (stageTicks < 600) return true;
            return finish(client, false, "durable reopen ownership failure " + snapshot);
        }
        if (!reopenedReady) {
            reopenedReady = true;
            RingWorldMod.LOGGER.info(
                    "[create-kinetic-d3] durable-reopen PASS modules={} owned={} created={} "
                            + "deleted={} failedDeletes={} clientStateRestored=true",
                    observations.size(), snapshot.ownedCount(), snapshot.created(),
                    snapshot.deleted(), snapshot.failedDeletes());
            advance(13);
        }
        return true;
    }

    private boolean teardown(Minecraft client, RingGeometry geometry) {
        if (stageTicks == 1) {
            var server = client.getSingleplayerServer();
            server.execute(() -> runAsync("network teardown", () ->
                    removeNetwork(server.overworld(), canonicalOrigin(geometry))));
            return true;
        }
        RingCreate610KineticEmbeddingOwner.Snapshot snapshot = ownerSnapshot(client, null);
        if (snapshot.ownedCount() != 0) {
            if (stageTicks < 240) return true;
            return finish(client, false, "teardown retained owners " + snapshot);
        }
        if (!offMode() && (snapshot.created() != snapshot.deleted()
                || snapshot.failedDeletes() != 0)) {
            return finish(client, false, "teardown imbalance " + snapshot);
        }
        // Let Create finish its native kinetic-network removal bookkeeping before
        // the integrated server begins its normal terminal save.
        if (stageTicks < 40) return true;
        List<Double> sorted = frameMillis.stream().sorted().toList();
        double median = percentile(sorted, 0.50);
        double p95 = percentile(sorted, 0.95);
        double p99 = percentile(sorted, 0.99);
        long over50 = sorted.stream().filter(value -> value > 50.0).count();
        return finish(client, true,
                "backend=" + backend() + " route=" + route().id
                        + " modules=" + moduleSpecs(canonicalOrigin(geometry)).size()
                        + " densityVisuals=" + (densityEnabled() ? 128 : 0)
                        + " captures=" + captures + " owned=0 created=" + snapshot.created()
                        + " deleted=" + snapshot.deleted() + " failedDeletes="
                        + snapshot.failedDeletes() + " frameSamples=" + sorted.size()
                        + String.format(Locale.ROOT,
                                " medianMs=%.3f p95Ms=%.3f p99Ms=%.3f over50Ms=%d",
                                median, p95, p99, over50));
    }

    private void requestCameraPose(Minecraft client, RingGeometry geometry, Capture capture) {
        var server = client.getSingleplayerServer();
        UUID playerUuid = client.player.getUUID();
        server.execute(() -> runAsync("network camera pose", () -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) throw new IllegalStateException("missing player for camera pose");
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            double cameraX = cameraCanonicalX(geometry) + 0.5;
            double cameraZ = canonicalOrigin(geometry).getZ() - capture.distance;
            player.teleportTo(server.overworld(), cameraX, Y + 2, cameraZ,
                    Set.of(), 0.0F, 0.0F);
            poseReady = true;
        }));
    }

    private static ProjectionSet projections(
            Minecraft client, RingGeometry geometry, List<Observation> observations,
            double yawOffset) {
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        List<Vec3> allPoints = new ArrayList<>();
        Map<String, List<Vec3>> pointsByModule = new LinkedHashMap<>();
        for (Observation observation : observations) {
            List<Vec3> points = blockPoints(observation.presentation());
            pointsByModule.put(observation.id(), points);
            allPoints.addAll(points);
        }
        RingCreate610FixtureProjection.Aim aim = RingCreate610FixtureProjection.aim(
                geometry, camera, allPoints, yawOffset,
                client.getMainRenderTarget().width, client.getMainRenderTarget().height, 70.0);
        Map<String, RingCreate610FixtureProjection.Projection> curved = new LinkedHashMap<>();
        Map<String, RingCreate610FixtureProjection.Projection> flat = new LinkedHashMap<>();
        for (Map.Entry<String, List<Vec3>> entry : pointsByModule.entrySet()) {
            List<Vec3> curvedLocal = entry.getValue().stream()
                    .map(point -> geometry.toCameraLocal(point, camera)).toList();
            curved.put(entry.getKey(), project(curvedLocal, aim, client));
            List<Vec3> flatLocal = entry.getValue().stream().map(point -> point.subtract(camera)).toList();
            flat.put(entry.getKey(), project(flatLocal, aim, client));
        }
        BlockPos reference = presentationPosition(
                canonicalOrigin(geometry).offset(-12, 1, -3), camera.x, geometry);
        List<Vec3> referenceLocal = blockPoints(reference).stream()
                .map(point -> geometry.toCameraLocal(point, camera)).toList();
        return new ProjectionSet(aim, Map.copyOf(curved), Map.copyOf(flat),
                project(referenceLocal, aim, client));
    }

    private static RingCreate610FixtureProjection.Projection project(
            List<Vec3> points, RingCreate610FixtureProjection.Aim aim, Minecraft client) {
        Vec3 center = points.stream().reduce(Vec3.ZERO, Vec3::add).scale(1.0 / points.size());
        return RingCreate610FixtureProjection.projectCameraLocal(
                points, center, aim.yaw(), aim.pitch(),
                client.getMainRenderTarget().width, client.getMainRenderTarget().height, 70.0);
    }

    private static List<Observation> observeModules(Minecraft client, RingGeometry geometry) {
        List<Observation> result = new ArrayList<>();
        for (Module module : moduleSpecs(canonicalOrigin(geometry)).values()) {
            BlockPos presentation = presentationPosition(
                    module.position(), client.player.getX(), geometry);
            BlockEntity blockEntity = client.level.getBlockEntity(presentation);
            if (blockEntity == null && !presentation.equals(module.position())) {
                blockEntity = client.level.getBlockEntity(module.position());
            }
            if (blockEntity == null) continue;
            result.add(observe(client, module, presentation, blockEntity));
        }
        return List.copyOf(result);
    }

    private static Observation observe(
            Minecraft client, Module module, BlockPos presentation, BlockEntity blockEntity) {
        VisualizationManager manager = VisualizationManager.get(client.level);
        if (manager == null) {
            return Observation.off(module.id(), module.position(), presentation, blockEntity);
        }
        if (!(manager.blockEntities() instanceof VisualManagerImpl<?, ?> visualManager)
                || !(visualManager.getStorage() instanceof BlockEntityStorage storage)) {
            throw new IllegalStateException("unexpected Flywheel block storage");
        }
        BlockEntityVisual<?> visual = storage.visualAtPos(presentation.asLong());
        BlockPos lookup = presentation;
        if (visual == null && !module.position().equals(presentation)) {
            lookup = module.position();
            visual = storage.visualAtPos(module.position().asLong());
        }
        if (visual == null) {
            return Observation.off(module.id(), module.position(), presentation, blockEntity);
        }
        if (!(visual instanceof AbstractBlockEntityVisual<?> blockVisual)) {
            throw new IllegalStateException("unexpected non-block visual " + visual.getClass().getName());
        }
        BlockEntity owner = client.level.getBlockEntity(lookup);
        RingCreate610KineticEmbeddingOwner.Snapshot snapshot =
                ((RingCreate610KineticEmbeddingAccess) (Object) storage)
                        .ringworld$kineticEmbeddingSnapshot(owner);
        List<RotatingInstance> instances = rotatingInstances(visual);
        int matrixIndex = -1;
        String environmentClass = "none";
        String instancePosition = "none";
        int visible = 0;
        for (RotatingInstance instance : instances) {
            if (!(instance.handle() instanceof InstanceHandleImpl<?> handle)) continue;
            Object instancerOwner = handle.state instanceof AbstractInstancer<?>
                    ? handle.state : readFieldOrNull(handle.state, "parent");
            if (instancerOwner instanceof AbstractInstancer<?> instancer
                    && !(handle.state instanceof InstanceHandleImpl.Hidden<?>)) {
                visible++;
                if (matrixIndex < 0) {
                    environmentClass = instancer.environment.getClass().getName();
                    matrixIndex = instancer.environment.matrixIndex();
                    instancePosition = String.format(Locale.ROOT, "%.3f/%.3f/%.3f",
                            instance.x, instance.y, instance.z);
                }
            }
        }
        return new Observation(module.id(), module.position(), presentation,
                blockEntity.getClass().getName(), kineticSpeed(blockEntity),
                visual.getClass().getName(), System.identityHashCode(visual),
                blockVisual.getVisualPosition(), manager.renderOrigin(), instances.size(), visible,
                instancePosition, environmentClass, matrixIndex, snapshot.ownedCount(),
                snapshot.created(), snapshot.deleted(), snapshot.failedDeletes(),
                snapshot.visualIdentity(), snapshot.embeddingIdentity(), snapshot.curved(),
                snapshot.finitePose());
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

    private static Map<String, Module> placeNetwork(ServerLevel level, BlockPos origin) {
        Map<String, Module> modules = moduleSpecs(origin);
        for (Module module : modules.values()) {
            level.setBlockAndUpdate(module.position(), module.state());
            if (module.id().equals("motor")) {
                setCreativeMotorSpeed(level.getBlockEntity(module.position()), 32);
            }
            if (module.support() != null) {
                level.setBlockAndUpdate(module.support().position(), module.support().state());
            }
            if (module.power() != null) {
                level.setBlockAndUpdate(module.power(), motorState(module.powerFacing()));
                BlockEntity motor = level.getBlockEntity(module.power());
                if (motor == null) throw new IllegalStateException("missing motor for " + module.id());
                setCreativeMotorSpeed(motor, 32);
            }
        }
        level.setBlockAndUpdate(origin.offset(-12, 1, -3), Blocks.GOLD_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(-12, 2, -3), Blocks.MAGENTA_CONCRETE.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(-12, 3, -3), Blocks.LIME_CONCRETE.defaultBlockState());
        return modules;
    }

    private static Map<String, Module> moduleSpecs(BlockPos origin) {
        Map<String, Module> modules = new LinkedHashMap<>();
        addSimple(modules, "shaft", origin.offset(-10, 0, 0), "create:shaft", "axis", "z");
        addSimple(modules, "small-cog", origin.offset(-6, 0, 0), "create:cogwheel", "axis", "z");
        addSimple(modules, "large-cog", origin.offset(-2, 0, 0), "create:large_cogwheel", "axis", "z");
        addSimple(modules, "fan", origin.offset(2, 0, 0), "create:encased_fan", "facing", "north");
        addSimple(modules, "press", origin.offset(6, 0, 0), "create:mechanical_press", "facing", "north");
        BlockPos pump = origin.offset(10, 0, 0);
        BlockPos pumpCog = pump.east();
        modules.put("pump", new Module("pump", pump,
                withProperty(block("create:mechanical_pump").defaultBlockState(),
                        "facing", "north"), pumpCog.south(), "north",
                new Support(pumpCog, withProperty(
                        block("create:cogwheel").defaultBlockState(), "axis", "z"))));
        BlockPos mixer = origin.offset(-10, 0, 6);
        BlockPos mixerCog = mixer.east();
        modules.put("mixer", new Module("mixer", mixer,
                block("create:mechanical_mixer").defaultBlockState(), mixerCog.below(), "up",
                new Support(mixerCog, withProperty(
                        block("create:cogwheel").defaultBlockState(), "axis", "y"))));
        addSimple(modules, "bearing", origin.offset(-5, 0, 6), "create:mechanical_bearing", "facing", "up");
        BlockPos piston = origin.offset(0, 0, 6);
        BlockState pistonState = withProperty(withProperty(
                block("create:mechanical_piston").defaultBlockState(), "facing", "up"),
                "axis_along_first", "false");
        modules.put("piston", new Module("piston", piston, pistonState,
                piston.south(), "north", null));
        BlockPos gantry = origin.offset(5, 0, 6);
        BlockPos gantryShaft = gantry.west();
        BlockState carriage = withProperty(withProperty(
                block("create:gantry_carriage").defaultBlockState(), "facing", "east"),
                "axis_along_first", "false");
        BlockState shaft = withProperty(withProperty(withProperty(
                block("create:gantry_shaft").defaultBlockState(), "facing", "up"),
                "part", "middle"), "powered", "false");
        modules.put("gantry", new Module("gantry", gantry, carriage,
                gantryShaft.below(), "up", new Support(gantryShaft, shaft)));
        BlockPos pulley = origin.offset(10, 0, 6);
        modules.put("pulley", new Module("pulley", pulley,
                withProperty(block("create:rope_pulley").defaultBlockState(),
                        "axis", "z"), pulley.south(), "north", null));
        BlockPos motor = origin.offset(0, 0, 12);
        modules.put("motor", new Module("motor", motor,
                motorState("north"), null, null, null));
        return Map.copyOf(modules);
    }

    private static void addSimple(
            Map<String, Module> modules, String id, BlockPos position, String blockId,
            String property, String value) {
        BlockState state = withProperty(block(blockId).defaultBlockState(), property, value);
        modules.put(id, new Module(id, position, state, position.south(), "north", null));
    }

    private static void placeDensity(ServerLevel level, BlockPos origin) {
        BlockState shaftState = withProperty(
                block("create:shaft").defaultBlockState(), "axis", "z");
        for (int row = 0; row < 8; row++) {
            for (int column = 0; column < 8; column++) {
                BlockPos shaft = origin.offset(column * 3, 0, row * 3);
                BlockPos motor = shaft.south();
                level.setBlockAndUpdate(shaft, shaftState);
                level.setBlockAndUpdate(motor, motorState("north"));
                setCreativeMotorSpeed(level.getBlockEntity(motor), 32);
            }
        }
    }

    private static Set<ChunkPos> networkChunks(BlockPos origin) {
        Set<ChunkPos> chunks = new java.util.LinkedHashSet<>();
        for (Module module : moduleSpecs(origin).values()) {
            chunks.add(new ChunkPos(module.position()));
            if (module.power() != null) chunks.add(new ChunkPos(module.power()));
            if (module.support() != null) chunks.add(new ChunkPos(module.support().position()));
        }
        if (densityEnabled()) {
            BlockPos density = densityOrigin(origin);
            for (int row = 0; row < 8; row++) {
                for (int column = 0; column < 8; column++) {
                    BlockPos shaft = density.offset(column * 3, 0, row * 3);
                    chunks.add(new ChunkPos(shaft));
                    chunks.add(new ChunkPos(shaft.south()));
                }
            }
        }
        return Set.copyOf(chunks);
    }

    private static void removeNetwork(ServerLevel level, BlockPos origin) {
        for (Module module : moduleSpecs(origin).values()) {
            level.setBlockAndUpdate(module.position(), Blocks.AIR.defaultBlockState());
            if (module.power() != null) {
                level.setBlockAndUpdate(module.power(), Blocks.AIR.defaultBlockState());
            }
            if (module.support() != null) {
                level.setBlockAndUpdate(module.support().position(), Blocks.AIR.defaultBlockState());
            }
        }
        if (densityEnabled()) {
            BlockPos density = densityOrigin(origin);
            for (int row = 0; row < 8; row++) {
                for (int column = 0; column < 8; column++) {
                    BlockPos shaft = density.offset(column * 3, 0, row * 3);
                    level.setBlockAndUpdate(shaft, Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(shaft.south(), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void prepareArena(ServerLevel level, BlockPos origin) {
        level.setDayTime(6_000L);
        level.setWeatherParameters(0, 120_000, false, false);
        for (int x = origin.getX() - 24; x <= origin.getX() + 24; x++) {
            for (int z = origin.getZ() - 96; z <= origin.getZ() + 24; z++) {
                level.setBlock(new BlockPos(x, Y - 3, z),
                        Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = Y - 2; y <= Y + 8; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        for (int x = origin.getX() - 18; x <= origin.getX() + 18; x++) {
            for (int y = Y - 5; y <= Y + 8; y++) {
                level.setBlock(new BlockPos(x, y, origin.getZ() + 18),
                        Blocks.WHITE_CONCRETE.defaultBlockState(), 2);
            }
        }
    }

    private boolean driveToRouteChart(Minecraft client, RingGeometry geometry) {
        double canonicalTarget = cameraCanonicalX(geometry) + 0.5;
        double target = switch (route()) {
            case NORMAL -> canonicalTarget;
            case HIGH -> canonicalTarget + geometry.circumferenceBlocks();
            case LOW -> canonicalTarget - geometry.circumferenceBlocks();
        };
        double x = client.player.getX();
        if (Math.abs(x - target) <= 4.0) return true;
        if (route() == Route.HIGH) {
            if (chartHop == 0 && x < 400.0) {
                chartHop = 1;
                teleportPlayer(client, 800.5, 40.0);
            } else if (chartHop == 1 && Math.abs(x - 800.5) < 8.0) {
                chartHop = 2;
                teleportPlayer(client, 1_600.5, 40.0);
            } else if (chartHop == 2 && Math.abs(x - 1_600.5) < 8.0) {
                chartHop = 3;
                teleportPlayer(client, canonicalTarget, 40.0);
            }
        } else {
            teleportPlayer(client, canonicalTarget, 40.0);
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

    private static BlockPos canonicalOrigin(RingGeometry geometry) {
        int x = switch (route()) {
            case NORMAL -> geometry.circumferenceBlocks() / 4;
            case HIGH -> 260;
            case LOW -> geometry.circumferenceBlocks() - 260;
        };
        return new BlockPos(x, Y, 100);
    }

    private static int cameraCanonicalX(RingGeometry geometry) {
        return switch (route()) {
            case NORMAL -> geometry.circumferenceBlocks() / 4 - 20;
            case HIGH -> 240;
            case LOW -> geometry.circumferenceBlocks() - 240;
        };
    }

    private static BlockPos densityOrigin(BlockPos origin) {
        return origin.offset(-11, -10, -20);
    }

    private static BlockPos presentationPosition(
            BlockPos canonical, double referenceX, RingGeometry geometry) {
        return RingBlockCoordinates.nearestImageBlockPos(canonical, referenceX, geometry);
    }

    private static List<Vec3> blockPoints(BlockPos position) {
        List<Vec3> points = new ArrayList<>(9);
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    points.add(new Vec3(position.getX() + x,
                            position.getY() + y, position.getZ() + z));
                }
            }
        }
        points.add(Vec3.atCenterOf(position));
        return List.copyOf(points);
    }

    private static String encodeBounds(
            Map<String, RingCreate610FixtureProjection.Projection> bounds) {
        return bounds.entrySet().stream().map(entry -> entry.getKey() + ":"
                + String.format(Locale.ROOT, "%.1f/%.1f/%.1f/%.1f",
                        entry.getValue().minX(), entry.getValue().minY(),
                        entry.getValue().maxX(), entry.getValue().maxY()))
                .collect(java.util.stream.Collectors.joining(","));
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

    private static float kineticSpeed(BlockEntity blockEntity) {
        try {
            Object value = blockEntity.getClass().getMethod("getSpeed").invoke(blockEntity);
            if (value instanceof Number number) return number.floatValue();
            throw new IllegalStateException("getSpeed returned " + value);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not observe kinetic speed on "
                    + blockEntity.getClass().getName(), failure);
        }
    }

    private static void setCreativeMotorSpeed(BlockEntity blockEntity, int speed) {
        if (blockEntity == null) throw new IllegalStateException("missing creative motor");
        Object behaviour = readField(blockEntity, "generatedSpeed");
        try {
            behaviour.getClass().getMethod("setValue", int.class).invoke(behaviour, speed);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not apply Create motor control", failure);
        }
    }

    private static Object readField(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // Continue through the exact hierarchy.
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
                // Continue through the exact hierarchy.
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
                // Continue through the exact hierarchy.
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("could not set " + name + " on " + target, failure);
            }
        }
        throw new IllegalStateException("missing field " + name + " on " + target.getClass().getName());
    }

    private void advance(int next) {
        stage = next;
        stageTicks = 0;
    }

    private void runAsync(String label, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            asynchronousFailure = label + ": " + failure;
            RingWorldMod.LOGGER.error("[create-kinetic-d3] async failure {}", label, failure);
        }
    }

    private static boolean finish(Minecraft client, boolean passed, String detail) {
        RingWorldMod.LOGGER.info("[create-kinetic-d3] result={} {}", passed, detail);
        client.stop();
        return true;
    }

    private static double percentile(List<Double> sorted, double percentile) {
        if (sorted.isEmpty()) return 0.0;
        int index = Math.min(sorted.size() - 1,
                Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1));
        return sorted.get(index);
    }

    private static Block block(String id) {
        Block value = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse(id));
        if (value == Blocks.AIR) throw new IllegalStateException("missing registered block " + id);
        return value;
    }

    private static BlockState motorState(String facing) {
        return withProperty(block("create:creative_motor").defaultBlockState(), "facing", facing);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withProperty(BlockState state, String name, String value) {
        Property property = state.getProperties().stream()
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "missing property " + name + " on " + state));
        var parsedValue = property.getValue(value);
        if (parsedValue.isEmpty()) {
            throw new IllegalStateException("invalid property " + name + "=" + value
                    + " on " + state);
        }
        return state.setValue(property, (Comparable) parsedValue.get());
    }

    private static String blockName(BlockState state) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock())
                + state.getValues().toString();
    }

    private static String backend() {
        return Backend.REGISTRY.getIdOrThrow(BackendManager.currentBackend()).toString();
    }

    private static String requestedBackend() {
        return System.getProperty(BACKEND_PROPERTY, "default");
    }

    private static boolean offMode() { return "off".equals(requestedBackend()); }

    private static boolean densityEnabled() {
        return !offMode() && (route() == Route.NORMAL || route() == Route.HIGH)
                && "default".equals(requestedBackend());
    }

    private static boolean durableReopenEnabled() {
        return route() == Route.LOW && "default".equals(requestedBackend());
    }

    private static Route route() {
        return switch (System.getProperty(ROUTE_PROPERTY, "high")) {
            case "normal" -> Route.NORMAL;
            case "high" -> Route.HIGH;
            case "low" -> Route.LOW;
            default -> throw new IllegalStateException("unknown kinetic network route");
        };
    }

    private record Support(BlockPos position, BlockState state) { }

    private record Module(
            String id, BlockPos position, BlockState state, BlockPos power,
            String powerFacing, Support support) { }

    private record Observation(
            String id, BlockPos canonical, BlockPos presentation, String blockEntityClass,
            float speed, String visualClass, int visualIdentity, BlockPos visualPos,
            Vec3i renderOrigin, int instanceCount, int visibleInstances, String instancePosition,
            String environmentClass, int matrixIndex, int ownedCount, long ownerCreated,
            long ownerDeleted, long ownerFailedDeletes, int ownerVisualIdentity,
            int ownerEmbeddingIdentity, boolean ownerCurved, boolean ownerFinite) {
        private boolean stationaryController() {
            return id.equals("bearing") || id.equals("gantry");
        }

        private static Observation off(
                String id, BlockPos canonical, BlockPos presentation, BlockEntity blockEntity) {
            return new Observation(id, canonical, presentation, blockEntity.getClass().getName(),
                    kineticSpeed(blockEntity), null, -1, presentation, BlockPos.ZERO,
                    0, 0, "none", "none", -1, 0, 0, 0, 0,
                    -1, -1, false, true);
        }

        @Override
        public String toString() {
            return id + "{" + blockEntityClass + ",speed=" + speed + ",canonical=" + canonical
                    + ",presentation=" + presentation + ",visual=" + visualClass + "#"
                    + visualIdentity + ",visualPos=" + visualPos + ",renderOrigin=" + renderOrigin
                    + ",instances=" + instanceCount + "/" + visibleInstances + "@"
                    + instancePosition + ",environment=" + environmentClass + ",matrixIndex="
                    + matrixIndex + ",owner=" + ownerVisualIdentity + "/" + ownerEmbeddingIdentity
                    + ",curved=" + ownerCurved + ",finite=" + ownerFinite + "}";
        }
    }

    private record ProjectionSet(
            RingCreate610FixtureProjection.Aim aim,
            Map<String, RingCreate610FixtureProjection.Projection> moduleBounds,
            Map<String, RingCreate610FixtureProjection.Projection> flatBounds,
            RingCreate610FixtureProjection.Projection reference) {
        private boolean allInView(Minecraft client) {
            return moduleBounds.values().stream().allMatch(projection ->
                    projection.intersectsViewport(
                            client.getMainRenderTarget().width,
                            client.getMainRenderTarget().height));
        }
    }

    private enum Capture {
        CENTER_PHASE_0("center-phase-0", 24.0, 0.0, 90),
        CENTER_PHASE_1("center-phase-1", 24.0, 0.0, 9),
        CENTER_PHASE_2("center-phase-2", 24.0, 0.0, 11),
        EDGE_NEAR("edge-near", 24.0, -18.0, 70),
        CENTER_FAR("center-far", 92.0, 0.0, 80);

        private final String id;
        private final double distance;
        private final double yawOffset;
        private final int settleTicks;

        Capture(String id, double distance, double yawOffset, int settleTicks) {
            this.id = id;
            this.distance = distance;
            this.yawOffset = yawOffset;
            this.settleTicks = settleTicks;
        }
    }

    private enum Route {
        NORMAL("normal"), HIGH("high"), LOW("low");

        private final String id;

        Route(String id) { this.id = id; }
    }
}
