package dev.ringworld.platform.neoforge.compat.create610;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.piston.LinearActuatorBlockEntity;
import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.ringworld.RingWorldMod;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.compat.ClientWorldLifecycle;
import dev.ringworld.client.compat.Screenshot;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.platform.neoforge.compat.create610.mixin.RingCreate610MixinPlugin;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingBlockCoordinates;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingObjectTransform;
import dev.ringworld.world.RingTopology;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Disposable exact-tuple matrix for real glued linear Create contraptions. */
public final class RingCreate610LinearContraptionFixture {
    public static final String ENABLE_PROPERTY = "ringworld.createCompatLinear";
    public static final String ROUTE_PROPERTY = "ringworld.createCompatLinearRoute";
    public static final String BACKEND_PROPERTY = "ringworld.createCompatLinearBackend";
    private static final String WORLD_PREFIX = "RingWorld Create Linear ";
    private static final int Y = 120;
    private static final int TIMEOUT_TICKS = 6_000;
    private static final RingCreate610LinearContraptionFixture INSTANCE =
            new RingCreate610LinearContraptionFixture();

    private boolean worldScreenOpened;
    private boolean worldStarted;
    private boolean setupRequested;
    private volatile boolean setupReady;
    private volatile boolean serverTaskPending;
    private volatile String asynchronousFailure;
    private volatile long gluePlacedAt = Long.MIN_VALUE;
    private volatile int gluePolls;
    private volatile UUID contraptionUuid;
    private volatile int serverEntityId = -1;
    private volatile double serverX;
    private volatile double serverY;
    private volatile double progress;
    private volatile double initialAxis;
    private volatile int capturedBlocks;
    private volatile int movedBlockEntityTypes;
    private volatile String capturedNbt;
    private volatile String inventoryBefore;
    private volatile String inventoryAfter;
    private volatile boolean assemblyReady;
    private volatile boolean restorationReady;
    private volatile boolean reopenedServerReady;
    private int motorSetting = 64;
    private boolean powerPlaced;
    private boolean powerAdjusted;
    private boolean assemblyTriggered;
    private boolean reversalRequested;
    private boolean durableRequested;
    private volatile boolean durableSlowReady;
    private boolean durableCleared;
    private boolean reopenRequested;
    private boolean reopenedClientBound;
    private Entity clientIdentity;
    private int clientEntityId = -1;
    private int visualIdentity = -1;
    private int visualCreates;
    private int visualDeletes;
    private int generation = 1;
    private int stage;
    private int stageTicks;
    private int ticks;
    private int chartHop;
    private int capturePhase;
    private String pendingCapture;
    private int captureWarmup;
    private int captures;
    private boolean backendChecked;

    private RingCreate610LinearContraptionFixture() { }

    public static RingCreate610LinearContraptionFixture instance() { return INSTANCE; }

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
            creator.setName(WORLD_PREFIX + route().id);
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
        if (++ticks > TIMEOUT_TICKS) return finish(client, false,
                "timeout stage=" + stage + " progress=" + progress);
        if (asynchronousFailure != null) return finish(client, false, asynchronousFailure);
        if (client.player == null || client.level == null || client.screen != null) {
            if (stage == 4) waitForDisconnect(client);
            else if (stage == 5) reopenWorld(client);
            return true;
        }
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || geometry.circumferenceBlocks() != 2048) return true;
        if (!backendChecked) {
            backendChecked = true;
            String expected = requestedBackend().equals("default")
                    ? "flywheel:indirect" : "flywheel:" + requestedBackend();
            if (!backend().equals(expected)) {
                return finish(client, false, "capability-rejected expected=" + expected
                        + " actual=" + backend());
            }
        }
        ClientRingState.updateCameraPosition(client.player.getX());
        stageTicks++;
        if (stageTicks % 200 == 0) {
            RingWorldMod.LOGGER.info(
                    "[create-linear] stage-wait stage={} ticks={} playerX={} pending={} "
                            + "serverTask={} reopenedServer={} reopenedClient={}",
                    stage, stageTicks, client.player.getX(), pendingCapture,
                    serverTaskPending, reopenedServerReady, reopenedClientBound);
        }
        switch (stage) {
            case 0 -> establishChartAndSetup(client, geometry);
            case 1 -> waitForAssembly(client);
            case 2 -> bindClientGeneration(client);
            case 3 -> runOutbound(client);
            case 4 -> waitForDisconnect(client);
            case 5 -> reopenWorld(client);
            case 6 -> bindReopenedGeneration(client);
            case 7 -> runReverse(client);
            case 8 -> waitForRestoration(client);
            default -> { return finish(client, false, "invalid stage=" + stage); }
        }
        return true;
    }

    public void frameRendered() { }

    private void establishChartAndSetup(Minecraft client, RingGeometry geometry) {
        if (!driveToChart(client, geometry)) return;
        if (setupRequested) return;
        setupRequested = true;
        var server = client.getSingleplayerServer();
        server.execute(() -> runAsync("linear setup", () -> {
            ServerLevel level = server.overworld();
            RingGeometry serverGeometry = RingWorldServer.geometryFor(level);
            BlockPos controller = controllerPos(serverGeometry);
            prepareArena(level, controller, serverGeometry);
            placeMechanism(level, controller, serverGeometry);
            Map<BlockPos, BlockState> expected = expectedStates(controller);
            expected.forEach(level::setBlockAndUpdate);
            seedMovedBlockEntities(level, expected);
            BlockPos negative = negativeControl(controller);
            level.setBlockAndUpdate(negative, negativeControlState());
            List<SuperGlueEntity> addedGlue = new ArrayList<>();
            for (Edge edge : glueEdges(controller)) {
                SuperGlueEntity glue = new SuperGlueEntity(
                        level, SuperGlueEntity.span(edge.first(), edge.second()));
                if (!level.addFreshEntity(glue)) {
                    throw new IllegalStateException("could not add glue " + edge);
                }
                addedGlue.add(glue);
            }
            AABB glueSearch = assemblyBounds(expected.keySet()).inflate(2.0);
            List<SuperGlueEntity> placedGlue = level.getEntitiesOfClass(
                    SuperGlueEntity.class, glueSearch);
            inventoryBefore = inventory(level, expected);
            gluePlacedAt = level.getGameTime();
            setupReady = true;
            RingWorldMod.LOGGER.info(
                    "[create-linear] setup route={} controller={} canonical=true blocks={} "
                            + "glueEdges={} indexedImmediately={} glueEntities={} negative={} inventory={}",
                    route().id, controller, expected.size(), glueEdges(controller).size(),
                    allGlue(level, glueEdges(controller)), placedGlue.size(),
                    negative, inventoryBefore);
        }));
        advance(1);
    }

    private void waitForAssembly(Minecraft client) {
        if (!setupReady || serverTaskPending || stageTicks % 3 != 0) return;
        serverTaskPending = true;
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            try {
                runAsync("linear assembly", () -> pollAssembly(server.overworld()));
            } finally {
                serverTaskPending = false;
            }
        });
        if (assemblyReady) advance(2);
    }

    private void pollAssembly(ServerLevel level) {
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        BlockPos controllerPos = controllerPos(geometry);
        if (level.getGameTime() < gluePlacedAt + 4L) return;
        BlockEntity controller = level.getBlockEntity(controllerPos);
        if (powerPlaced && route().mechanism == Mechanism.GANTRY) {
            AbstractContraptionEntity moving = activeEntity(level, null, controllerPos);
            if (moving != null) {
                finishAssemblyBinding(level, controllerPos, moving,
                        moving.getDeltaMovement().length());
                return;
            }
            if (controller == null) return;
        }
        controller = requireController(level, controllerPos);
        if (!powerPlaced) {
            if (!allGlue(level, glueEdges(controllerPos))) {
                gluePolls++;
                if (gluePolls == 1 || gluePolls % 20 == 0) {
                    AABB glueSearch = assemblyBounds(
                            expectedStates(controllerPos).keySet()).inflate(2.0);
                    RingWorldMod.LOGGER.info(
                            "[create-linear] glue-wait route={} polls={} gameTicks={} entities={}",
                            route().id, gluePolls, level.getGameTime() - gluePlacedAt,
                            level.getEntitiesOfClass(
                                    SuperGlueEntity.class, glueSearch).size());
                }
                if (gluePolls < 100) return;
                assertGlue(level, glueEdges(controllerPos), true);
            }
            if (route().mechanism != Mechanism.PULLEY) {
                motorSetting = route().movementDirection == Direction.EAST ? -64 : 64;
                powerAdjusted = true;
            }
            placeAndSetMotorSpeed(level, motorPos(controllerPos, geometry), motorSetting);
            powerPlaced = true;
            RingWorldMod.LOGGER.info(
                    "[create-linear] ordinary power transition route={} motorSetting={} glueVerified=true",
                    route().id, motorSetting);
            return;
        }
        AbstractContraptionEntity entity = activeEntity(level, controller, controllerPos);
        if (entity != null) {
            finishAssemblyBinding(level, controllerPos, entity,
                    route().mechanism == Mechanism.PISTON
                            ? ((Number) invoke(controller, "getMovementSpeed")).doubleValue()
                            : outwardMovement(controller, level, geometry));
            return;
        }
        double movement = route().mechanism == Mechanism.PISTON
                ? 1.0 : outwardMovement(controller, level, geometry);
        if (level.getGameTime() % 40L == 0L) {
            BlockEntity motor = level.getBlockEntity(motorPos(controllerPos, geometry));
            RingWorldMod.LOGGER.info(
                    "[create-linear] assembly-wait route={} controllerSpeed={} movement={} "
                            + "motor={} motorSetting={} powerAdjusted={} triggered={}",
                    route().id, invoke(controller, "getSpeed"), movement,
                    motor == null ? "null" : motor.getClass().getName(), motorSetting,
                    powerAdjusted, assemblyTriggered);
        }
        if (movement == 0.0) return;
        if (!powerAdjusted && movement < 0.0) {
            motorSetting = -motorSetting;
            setMotorSpeed(level, motorPos(controllerPos, geometry), motorSetting);
            powerAdjusted = true;
            return;
        }
        if (movement < 0.0) return;
        if (!assemblyTriggered) {
            assemblyTriggered = true;
            triggerAssembly(controller);
            return;
        }
        entity = activeEntity(level, controller, controllerPos);
        if (entity == null) return;
        finishAssemblyBinding(level, controllerPos, entity, movement);
    }

    private void finishAssemblyBinding(
            ServerLevel level, BlockPos controllerPos,
            AbstractContraptionEntity entity, double movement) {
        verifyCaptured(level, controllerPos, entity);
        contraptionUuid = entity.getUUID();
        serverEntityId = entity.getId();
        serverX = entity.getX();
        serverY = entity.getY();
        initialAxis = route().axisValue(entity.position());
        capturedBlocks = entity.getContraption().getBlocks().size();
        movedBlockEntityTypes = distinctMovedBlockEntityTypes(entity);
        capturedNbt = capturedNbt(entity);
        assemblyReady = true;
        RingWorldMod.LOGGER.info(
                "[create-linear] assembled route={} entity={}/{} class={} contraption={} "
                        + "captured={} movedBlockEntityTypes={} capturedNbt={} movement={} "
                        + "canonicalControllerX={}",
                route().id, entity.getId(), entity.getUUID(), entity.getClass().getName(),
                entity.getContraption().getClass().getName(), capturedBlocks,
                movedBlockEntityTypes, capturedNbt, movement, controllerPos.getX());
    }

    private void bindClientGeneration(Minecraft client) {
        Entity entity = renderedEntity(client, contraptionUuid);
        if (!(entity instanceof AbstractContraptionEntity)) return;
        int visual = RingCreate610ClientDiagnostics.visualIdentity(entity.getId());
        int creates = RingCreate610ClientDiagnostics.visualCreateCount(entity.getId());
        if (offMode()) {
            if (visual != -1 || creates != 0) return;
        } else if (visual < 0 || creates != 1
                || RingCreate610ClientDiagnostics.entityTransformSamples(entity.getId()).isEmpty()) return;
        bindIdentity(entity, visual);
        RingWorldMod.LOGGER.info(
                "[create-linear] client-generation={} baseline route={} entity={}/{} object={} "
                        + "visual={}/creates={}/deletes={} chart={} renderMembership=true",
                generation, route().id, entity.getId(), entity.getUUID(),
                System.identityHashCode(entity), visualIdentity, visualCreates, visualDeletes,
                route().chart);
        advance(3);
    }

    private void runOutbound(Minecraft client) {
        AbstractContraptionEntity entity = requireStable(client);
        if (entity == null) return;
        requestServerSample(client);
        if (pendingCapture != null) {
            handleCapture(client, entity);
            return;
        }
        if (capturePhase == 0 && progress >= route().earlyProgress) {
            queueCapture("outbound-early");
            return;
        }
        if (capturePhase == 1 && progress >= route().crossProgress) {
            if (route().seam && !presentationCrossed(entity)) {
                finish(client, false, "server progress crossed but client presentation did not route="
                        + route().id + " clientX=" + entity.getX() + " serverX=" + serverX);
                return;
            }
            queueCapture(route().seam ? "outbound-seam" : "outbound-vertical");
            return;
        }
        if (capturePhase >= 2) {
            if (durableEnabled() && !durableRequested) {
                if (!durableSlowReady) {
                    requestDurableSlowdown(client);
                    return;
                }
                durableRequested = true;
                RingWorldMod.LOGGER.info(
                        "[create-linear] active durable disconnect route={} generation={} "
                                + "entity={}/{} progress={}",
                        route().id, generation, entity.getId(), entity.getUUID(), progress);
                ClientWorldLifecycle.disconnect(client,
                        Component.literal("RingWorld Create linear durable qualification"));
                advance(4);
            } else if (!durableEnabled()) {
                requestReversal(client);
                advance(7);
            }
        }
    }

    private void requestDurableSlowdown(Minecraft client) {
        if (serverTaskPending) return;
        serverTaskPending = true;
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            try {
                runAsync("linear durable slowdown", () -> {
                    RingGeometry geometry = RingWorldServer.geometryFor(server.overworld());
                    motorSetting = Integer.signum(motorSetting) * 8;
                    setMotorSpeed(server.overworld(),
                            motorPos(controllerPos(geometry), geometry), motorSetting);
                    durableSlowReady = true;
                    RingWorldMod.LOGGER.info(
                            "[create-linear] durable active slowdown route={} motorSetting={}",
                            route().id, motorSetting);
                });
            } finally {
                serverTaskPending = false;
            }
        });
    }

    private void waitForDisconnect(Minecraft client) {
        if (client.level != null || client.getSingleplayerServer() != null) return;
        durableCleared = ClientRingState.sessionCleared();
        if (!durableCleared) return;
        RingWorldMod.LOGGER.info(
                "[create-linear] durable disconnect route={} clientStateCleared=true", route().id);
        advance(5);
    }

    private void reopenWorld(Minecraft client) {
        if (client.level != null || client.getSingleplayerServer() != null || reopenRequested) return;
        reopenRequested = true;
        serverTaskPending = false;
        chartHop = 0;
        client.createWorldOpenFlows().openWorld(WORLD_PREFIX + route().id,
                () -> finish(client, false, "durable reopen cancelled"));
        advance(6);
    }

    private void bindReopenedGeneration(Minecraft client) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || !driveToChart(client, geometry)) return;
        if (!reopenedServerReady && !serverTaskPending && stageTicks % 3 == 0) {
            serverTaskPending = true;
            var server = client.getSingleplayerServer();
            server.execute(() -> {
                try {
                    runAsync("linear durable reopen", () -> {
                        ServerLevel level = server.overworld();
                        BlockPos controllerPos = controllerPos(RingWorldServer.geometryFor(level));
                        BlockEntity controller = requireController(level, controllerPos);
                        AbstractContraptionEntity entity = activeEntity(level, controller, controllerPos);
                        if (entity == null) return;
                        verifyCaptured(level, controllerPos, entity);
                        contraptionUuid = entity.getUUID();
                        serverEntityId = entity.getId();
                        serverX = entity.getX();
                        serverY = entity.getY();
                        progress = route().progress(controller, entity, initialAxis,
                                RingWorldServer.geometryFor(level));
                        reopenedServerReady = true;
                        RingWorldMod.LOGGER.info(
                                "[create-linear] durable reopen server route={} entity={}/{} "
                                        + "progress={} canonicalControllerX={} active=true",
                                route().id, entity.getId(), entity.getUUID(), progress,
                                controllerPos.getX());
                    });
                } finally {
                    serverTaskPending = false;
                }
            });
        }
        if (!reopenedServerReady) return;
        Entity entity = renderedEntity(client, contraptionUuid);
        if (!(entity instanceof AbstractContraptionEntity contraption)) return;
        int visual = RingCreate610ClientDiagnostics.visualIdentity(entity.getId());
        if ((!offMode() && (visual < 0
                || RingCreate610ClientDiagnostics.entityTransformSamples(entity.getId()).isEmpty()))
                || (offMode() && visual != -1)) return;
        if (!reopenedClientBound) {
            generation = 2;
            bindIdentity(entity, visual);
            reopenedClientBound = true;
            queueCapture("durable-reopened-active");
            return;
        }
        if (pendingCapture != null) {
            handleCapture(client, contraption);
            return;
        }
        if (capturePhase >= 3) {
            requestReversal(client);
            advance(7);
        }
    }

    private void runReverse(Minecraft client) {
        AbstractContraptionEntity entity = requireStable(client);
        if (entity == null) return;
        requestServerSample(client);
        if (pendingCapture != null) {
            handleCapture(client, entity);
            return;
        }
        if (capturePhase < (durableEnabled() ? 4 : 3)
                && progress <= route().reverseCaptureProgress && progress > 4.0) {
            queueCapture("reverse-return");
            return;
        }
        int requiredPhase = durableEnabled() ? 4 : 3;
        if (capturePhase >= requiredPhase) {
            // Let the ordinary reversed gantry route approach its controller
            // before applying Create's sequenced exact-stop limit. Applying
            // the limit at the ~23-block capture point intermittently leaves
            // exact 6.0.10 with a live entity and a residual 7.875-block limit;
            // no fixture-authored move or disassembly is used here.
            if (route().mechanism == Mechanism.GANTRY && progress > 12.0) return;
            if (route().mechanism == Mechanism.GANTRY && !serverTaskPending) {
                serverTaskPending = true;
                var server = client.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        runAsync("gantry exact return", () -> {
                            Entity current = server.overworld().getEntity(contraptionUuid);
                            if (current instanceof GantryContraptionEntity gantry) {
                                // progress is measured from the entity anchor while
                                // native gantry restoration selects the carriage cell
                                // around its half-block centre.
                                gantry.limitMovement(Math.max(0.0, progress + 0.5));
                                RingWorldMod.LOGGER.info(
                                        "[create-linear] gantry exact native return progress={} limit={}",
                                        progress, progress + 0.5);
                            }
                        });
                    } finally {
                        serverTaskPending = false;
                    }
                });
            }
            advance(8);
        }
    }

    private void waitForRestoration(Minecraft client) {
        if (!restorationReady && !serverTaskPending && stageTicks % 3 == 0) {
            serverTaskPending = true;
            var server = client.getSingleplayerServer();
            server.execute(() -> {
                try {
                    runAsync("linear restoration", () -> verifyRestoration(server.overworld()));
                } finally {
                    serverTaskPending = false;
                }
            });
        }
        if (!restorationReady) return;
        int expectedCaptures = durableEnabled() ? 4 : 3;
        if (captures != expectedCaptures) {
            finish(client, false, "capture count mismatch expected=" + expectedCaptures
                    + " actual=" + captures);
            return;
        }
        if (RingCreate610MixinPlugin.appliedServerMixinCount() != 3
                || RingCreate610MixinPlugin.appliedClientMixinCount() != 8) {
            finish(client, false, "mixin count changed server="
                    + RingCreate610MixinPlugin.appliedServerMixinCount() + " client="
                    + RingCreate610MixinPlugin.appliedClientMixinCount());
            return;
        }
        finish(client, true, "backend=" + backend() + " route=" + route().id
                + " mechanism=" + route().mechanism.id + " realCreateMotion=true glued=true"
                + " capturedBlocks=" + capturedBlocks + " movedBlockEntityTypes="
                + movedBlockEntityTypes + " captures=" + captures + " reversal=true"
                + " identityContinuous=true flywheelEmbedding=" + (offMode() ? "zero" : "finite")
                + " durableReopen=" + durableEnabled() + " canonicalOwnership=true"
                + " exactRestoration=true negativeControl=true serverMixins=3 clientMixins=8");
    }

    private void requestServerSample(Minecraft client) {
        if (serverTaskPending || stageTicks % 3 != 0) return;
        serverTaskPending = true;
        var server = client.getSingleplayerServer();
        UUID uuid = contraptionUuid;
        server.execute(() -> {
            try {
                runAsync("linear motion sample", () -> {
                    ServerLevel level = server.overworld();
                    BlockPos controllerPos = controllerPos(RingWorldServer.geometryFor(level));
                    BlockEntity controller = route().mechanism == Mechanism.GANTRY
                            ? level.getBlockEntity(controllerPos)
                            : requireController(level, controllerPos);
                    Entity entity = level.getEntity(uuid);
                    if (!(entity instanceof AbstractContraptionEntity contraption)) {
                        if (stage < 8) throw new IllegalStateException(
                                "contraption disappeared before restoration stage route=" + route().id);
                        return;
                    }
                    serverX = entity.getX();
                    serverY = entity.getY();
                    progress = route().progress(controller, contraption, initialAxis,
                            RingWorldServer.geometryFor(level));
                    RingWorldMod.LOGGER.info(
                            "[create-linear] motion route={} generation={} entity={}/{} "
                                    + "serverCanonical={}/{}/{} progress={} reversing={}",
                            route().id, generation, entity.getId(), entity.getUUID(),
                            serverX, serverY, entity.getZ(), progress, reversalRequested);
                });
            } finally {
                serverTaskPending = false;
            }
        });
    }

    private void requestReversal(Minecraft client) {
        if (reversalRequested) return;
        reversalRequested = true;
        motorSetting = -Integer.signum(motorSetting) * 64;
        var server = client.getSingleplayerServer();
        server.execute(() -> runAsync("linear direction reversal", () -> {
            ServerLevel level = server.overworld();
            RingGeometry geometry = RingWorldServer.geometryFor(level);
            setMotorSpeed(level, motorPos(controllerPos(geometry), geometry), motorSetting);
            RingWorldMod.LOGGER.info(
                    "[create-linear] reversal route={} motorSetting={} progress={} createOwned=true",
                    route().id, motorSetting, progress);
        }));
    }

    private void verifyRestoration(ServerLevel level) {
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        BlockPos controllerPos = controllerPos(geometry);
        AbstractContraptionEntity entity = route().mechanism == Mechanism.GANTRY
                ? activeEntity(level, null, controllerPos) : null;
        BlockEntity controller = entity == null && route().mechanism == Mechanism.GANTRY
                ? level.getBlockEntity(controllerPos)
                : route().mechanism != Mechanism.GANTRY
                        ? requireController(level, controllerPos) : null;
        if (route().mechanism == Mechanism.GANTRY && entity == null && controller == null) return;
        if (route().mechanism == Mechanism.GANTRY && entity == null
                && !controller.getClass().getName().equals(route().mechanism.controllerClass)) {
            throw new IllegalStateException("wrong restored gantry controller at "
                    + controllerPos + ": " + controller.getClass().getName());
        }
        if (entity == null) entity = activeEntity(level, controller, controllerPos);
        if (entity != null) {
            if (entity instanceof GantryContraptionEntity gantry
                    && gantry.sequencedOffsetLimit == 0.0) {
                setMotorSpeed(level, motorPos(controllerPos, geometry), 0);
            }
            if (stageTicks % 40 == 0) {
                RingWorldMod.LOGGER.info(
                        "[create-linear] restoration-wait route={} entity={}/{} position={} "
                                + "delta={} limit={} offset={} speed={}",
                        route().id, entity.getId(), entity.getUUID(),
                        entity.position(), entity.getDeltaMovement(),
                        entity instanceof GantryContraptionEntity gantry
                                ? gantry.sequencedOffsetLimit : Double.NaN,
                        controller instanceof LinearActuatorBlockEntity actuator
                                ? actuator.offset : progress,
                        controller == null ? "moved-controller" : invoke(controller, "getSpeed"));
            }
            return;
        }
        setMotorSpeed(level, motorPos(controllerPos, geometry), 0);
        Map<BlockPos, BlockState> expected = expectedStates(controllerPos);
        if (!statesMatch(level, expected)) return;
        if (!allGlue(level, glueEdges(controllerPos))) return;
        if (!level.getBlockState(negativeControl(controllerPos))
                .is(negativeControlState().getBlock())) {
            throw new IllegalStateException("unglued negative changed during lifecycle");
        }
        assertMovedBlockEntityContents(level, expected);
        inventoryAfter = inventory(level, expected);
        if (!inventoryBefore.equals(inventoryAfter)) {
            throw new IllegalStateException("restored inventory differs before=" + inventoryBefore
                    + " after=" + inventoryAfter);
        }
        restorationReady = true;
        RingWorldMod.LOGGER.info(
                "[create-linear] restoration PASS route={} generation={} inventory={} "
                        + "glueEdges={} negative={} controllerCanonical=true",
                route().id, generation, inventoryAfter, glueEdges(controllerPos).size(),
                negativeControl(controllerPos));
    }

    private void queueCapture(String label) {
        pendingCapture = label;
        captureWarmup = 0;
    }

    private void handleCapture(Minecraft client, AbstractContraptionEntity entity) {
        RingCreate610FixtureProjection.Aim aim = RingCreate610FixtureProjection.aim(
                ClientRingState.geometry(), client.gameRenderer.getMainCamera().getPosition(),
                targetPoints(entity), 0.0, client.getMainRenderTarget().width,
                client.getMainRenderTarget().height, 70.0);
        orientClient(client, aim.yaw(), aim.pitch());
        if (++captureWarmup < 14) return;
        RingCreate610FixtureProjection.Projection projection = aim.projection();
        if (!projection.centerInViewport()
                || !projection.intersectsViewport(client.getMainRenderTarget().width,
                client.getMainRenderTarget().height)
                || projection.pointsInViewport() < 8 || projection.width() < 36.0
                || projection.height() < 8.0) {
            finish(client, false, "projection missed target capture=" + pendingCapture
                    + " projection=" + projection.logValue());
            return;
        }
        List<RingCreate610ClientDiagnostics.EntityTransformSample> transforms =
                RingCreate610ClientDiagnostics.entityTransformSamples(entity.getId());
        RingCreate610ClientDiagnostics.EntityTransformSample transform = offMode() || transforms.isEmpty()
                ? null : transforms.get(transforms.size() - 1);
        if (!offMode() && transform == null) {
            finish(client, false, "missing finite embedding sample capture=" + pendingCapture);
            return;
        }
        BlockPos chestLocal = route().mechanism == Mechanism.PULLEY
                ? new BlockPos(3, 0, 0) : new BlockPos(0, 0, 3);
        BlockPos shulkerLocal = route().mechanism == Mechanism.PULLEY
                ? new BlockPos(0, 0, 3) : new BlockPos(0, 3, 0);
        BlockPos chestStep = route().mechanism == Mechanism.PULLEY
                ? BlockPos.ZERO.east() : BlockPos.ZERO.south();
        BlockPos shulkerStep = route().mechanism == Mechanism.PULLEY
                ? BlockPos.ZERO.south() : BlockPos.ZERO.above();
        RingCreate610FixtureProjection.Projection chestProjection = payloadProjection(
                client, entity, chestLocal, aim.yaw(), aim.pitch());
        RingCreate610FixtureProjection.Projection shulkerProjection = payloadProjection(
                client, entity, shulkerLocal, aim.yaw(), aim.pitch());
        RingCreate610FixtureProjection.Projection chestBeforeProjection = payloadProjection(
                client, entity, chestLocal.subtract(chestStep), aim.yaw(), aim.pitch());
        RingCreate610FixtureProjection.Projection chestAfterProjection = payloadProjection(
                client, entity, chestLocal.offset(chestStep), aim.yaw(), aim.pitch());
        RingCreate610FixtureProjection.Projection shulkerBeforeProjection = payloadProjection(
                client, entity, shulkerLocal.subtract(shulkerStep), aim.yaw(), aim.pitch());
        RingCreate610FixtureProjection.Projection shulkerAfterProjection = payloadProjection(
                client, entity, shulkerLocal.offset(shulkerStep), aim.yaw(), aim.pitch());
        for (RingCreate610FixtureProjection.Projection payload : List.of(
                chestProjection, shulkerProjection, chestBeforeProjection,
                chestAfterProjection, shulkerBeforeProjection, shulkerAfterProjection)) {
            if (!payload.intersectsViewport(client.getMainRenderTarget().width,
                    client.getMainRenderTarget().height)) {
                finish(client, false, "payload projection missed capture=" + pendingCapture
                        + " projection=" + payload.logValue());
                return;
            }
        }
        String offLayers = RingCreate610ClientDiagnostics
                .offContraptionLayerSamples(entity.getId()).stream()
                .map(sample -> sample.sourceLayer() + "->" + sample.mappedLayer()
                        + "#shader=" + sample.shaderName()
                        + "/ringWorldLayout=" + sample.ringWorldLayoutUniform()
                        + "/chunkTerrain=" + sample.chunkTerrainLayer()
                        + "@" + sample.modelViewProjection())
                .collect(java.util.stream.Collectors.joining(";"));
        if (offMode() && offLayers.isEmpty()) {
            finish(client, false, "missing OFF layer/MVP diagnostics capture=" + pendingCapture);
            return;
        }
        String name = "ringworld-create-linear-" + backend().replace(':', '-') + "-"
                + route().id + "-" + pendingCapture;
        String sanity = "40/30/1240/690";
        RingWorldMod.LOGGER.info(
                "[create-linear] capture-proof name={} relative=screenshots/{}.png backend={} "
                        + "route={} mechanism={} chart={} direction={} phase={} generation={} "
                        + "entity={}/{} object={} visual={}/creates={}/deletes={} "
                        + "serverCanonical={}/{} clientPresentation={}/{} progress={} "
                        + "transformIndex={} transformAngle={} matrix={} expectedVisible=true "
                        + "projectedBounds={} chestLocal={} chestBounds={} "
                        + "chestNeighbors={}|{} shulkerLocal={} shulkerBounds={} "
                        + "shulkerNeighbors={}|{} offLayers={} poseSanityRoi={} "
                        + "camera={}/{}/{} yaw={} pitch={} "
                        + "renderMembership=true removed=false",
                name, name, backend(), route().id, route().mechanism.id, route().chart,
                route().directionLabel, pendingCapture, generation, entity.getId(), entity.getUUID(),
                System.identityHashCode(entity), visualIdentity, visualCreates, visualDeletes,
                serverX, serverY, entity.getX(), entity.getY(), progress,
                transform == null ? -1 : transform.transformIndex(),
                transform == null ? 0.0F : transform.angle(),
                transform == null ? "none" : transform.matrix(), projection.logValue(),
                coordinates(chestLocal), bounds(chestProjection), bounds(chestBeforeProjection),
                bounds(chestAfterProjection), coordinates(shulkerLocal),
                bounds(shulkerProjection), bounds(shulkerBeforeProjection),
                bounds(shulkerAfterProjection),
                offLayers.isEmpty() ? "none" : offLayers, sanity,
                client.player.getX(), client.player.getY(), client.player.getZ(),
                client.player.getYRot(), client.player.getXRot());
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1,
                message -> RingWorldMod.LOGGER.info(
                        "[create-linear] screenshot {} {}", name, message.getString()));
        captures++;
        capturePhase++;
        pendingCapture = null;
        captureWarmup = 0;
    }

    private AbstractContraptionEntity requireStable(Minecraft client) {
        Entity byId = client.level.getEntity(clientEntityId);
        Entity byUuid = renderedEntity(client, contraptionUuid);
        boolean stable = byId == clientIdentity && byUuid == clientIdentity
                && !clientIdentity.isRemoved()
                && RingCreate610ClientDiagnostics.visualIdentity(clientEntityId) == visualIdentity
                && RingCreate610ClientDiagnostics.visualCreateCount(clientEntityId) == visualCreates
                && RingCreate610ClientDiagnostics.visualDeleteCount(clientEntityId) == visualDeletes;
        if (!stable) {
            finish(client, false, "identity/visual discontinuity route=" + route().id
                    + " expectedObject=" + System.identityHashCode(clientIdentity)
                    + " byId=" + describe(byId) + " byUuid=" + describe(byUuid));
            return null;
        }
        return (AbstractContraptionEntity) clientIdentity;
    }

    private void bindIdentity(Entity entity, int visual) {
        clientIdentity = entity;
        clientEntityId = entity.getId();
        visualIdentity = visual;
        visualCreates = RingCreate610ClientDiagnostics.visualCreateCount(entity.getId());
        visualDeletes = RingCreate610ClientDiagnostics.visualDeleteCount(entity.getId());
    }

    private boolean presentationCrossed(Entity entity) {
        return route().chart.equals("high")
                ? entity.getX() >= ClientRingState.geometry().circumferenceBlocks()
                : entity.getX() < 0.0;
    }

    private boolean driveToChart(Minecraft client, RingGeometry geometry) {
        double target = cameraPresentationX(geometry);
        if (Math.abs(client.player.getX() - target) <= 3.0) return true;
        if (route().chart.equals("high")) {
            if (chartHop == 0 && client.player.getX() < 400.0) {
                chartHop = 1;
                teleportPlayer(client, 800.5);
            } else if (chartHop == 1 && Math.abs(client.player.getX() - 800.5) < 8.0) {
                chartHop = 2;
                teleportPlayer(client, 1_600.5);
            } else if (chartHop == 2 && Math.abs(client.player.getX() - 1_600.5) < 8.0) {
                chartHop = 3;
                teleportPlayer(client, target);
            }
        } else {
            teleportPlayer(client, target);
        }
        return false;
    }

    private static void teleportPlayer(Minecraft client, double presentationX) {
        var server = client.getSingleplayerServer();
        UUID playerUuid = client.player.getUUID();
        double canonicalX = new RingTopology(ClientRingState.geometry()).canonicalX(presentationX);
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player != null) player.teleportTo(server.overworld(), canonicalX,
                    route().mechanism == Mechanism.PULLEY ? Y + 12.0 : Y + 7.0,
                    70.0, Set.of(), 0.0F, 4.0F);
        });
    }

    private static void placeMechanism(
            ServerLevel level, BlockPos controller, RingGeometry geometry) {
        switch (route().mechanism) {
            case PISTON -> {
                BlockState piston = withProperty(withProperty(
                        block("create:mechanical_piston").defaultBlockState(),
                        "facing", route().movementDirection.getName()),
                        "axis_along_first", "true");
                level.setBlockAndUpdate(controller, piston);
                BlockState pole = withProperty(
                        block("create:piston_extension_pole").defaultBlockState(),
                        "facing", route().movementDirection.getName());
                for (int distance = 1; distance <= 64; distance++) {
                    level.setBlockAndUpdate(controller.relative(
                            route().movementDirection.getOpposite(), distance), pole);
                }
            }
            case GANTRY -> {
                BlockState shaft = withProperty(withProperty(withProperty(
                        block("create:gantry_shaft").defaultBlockState(), "facing",
                        route().movementDirection.getName()), "part", "middle"),
                        "powered", "false");
                for (int distance = -96; distance <= 96; distance++) {
                    BlockPos shaftPos = canonical(controller.north().relative(
                            route().movementDirection, distance), geometry);
                    level.setBlockAndUpdate(shaftPos, shaft);
                }
                BlockState carriage = withProperty(withProperty(
                        block("create:gantry_carriage").defaultBlockState(),
                        "facing", "south"), "axis_along_first", "true");
                level.setBlockAndUpdate(controller, carriage);
            }
            case PULLEY -> {
                level.setBlockAndUpdate(controller, withProperty(
                        block("create:rope_pulley").defaultBlockState(), "axis", "z"));
            }
        }
    }

    private static Map<BlockPos, BlockState> expectedStates(BlockPos controller) {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        BlockPos root = assemblyRoot(controller);
        states.put(root, Blocks.GOLD_BLOCK.defaultBlockState());
        int firstLength = route().mechanism == Mechanism.PULLEY ? 8 : 13;
        int secondLength = route().mechanism == Mechanism.PULLEY ? 7 : 12;
        Direction first = route().mechanism == Mechanism.PULLEY ? Direction.EAST : Direction.SOUTH;
        Direction second = route().mechanism == Mechanism.PULLEY ? Direction.SOUTH : Direction.UP;
        for (int i = 1; i <= firstLength; i++) {
            states.put(root.relative(first, i), paletteState(i, false));
        }
        for (int i = 1; i <= secondLength; i++) {
            states.put(root.relative(second, i), paletteState(i, true));
        }
        return Map.copyOf(states);
    }

    private static BlockState paletteState(int distance, boolean second) {
        return switch (distance) {
            case 1 -> Blocks.CUT_COPPER.defaultBlockState();
            case 2 -> Blocks.LIME_CONCRETE.defaultBlockState();
            case 3 -> second ? Blocks.BLUE_SHULKER_BOX.defaultBlockState()
                    : Blocks.CHEST.defaultBlockState();
            case 4 -> Blocks.GOLD_BLOCK.defaultBlockState();
            case 5 -> Blocks.MAGENTA_CONCRETE.defaultBlockState();
            case 6 -> Blocks.AMETHYST_BLOCK.defaultBlockState();
            case 7 -> Blocks.LIME_CONCRETE.defaultBlockState();
            case 8 -> Blocks.EMERALD_BLOCK.defaultBlockState();
            case 9 -> Blocks.MAGENTA_STAINED_GLASS.defaultBlockState();
            case 10 -> Blocks.GLASS.defaultBlockState();
            case 11 -> Blocks.CHAIN.defaultBlockState();
            case 12 -> Blocks.TRIPWIRE.defaultBlockState();
            case 13 -> Blocks.LIME_CONCRETE.defaultBlockState();
            default -> Blocks.GOLD_BLOCK.defaultBlockState();
        };
    }

    private static List<Edge> glueEdges(BlockPos controller) {
        BlockPos root = assemblyRoot(controller);
        List<Edge> edges = new ArrayList<>();
        int firstLength = route().mechanism == Mechanism.PULLEY ? 8 : 13;
        int secondLength = route().mechanism == Mechanism.PULLEY ? 7 : 12;
        Direction first = route().mechanism == Mechanism.PULLEY ? Direction.EAST : Direction.SOUTH;
        Direction second = route().mechanism == Mechanism.PULLEY ? Direction.SOUTH : Direction.UP;
        for (Direction direction : List.of(first, second)) {
            int length = direction == first ? firstLength : secondLength;
            BlockPos previous = root;
            for (int i = 1; i <= length; i++) {
                BlockPos next = root.relative(direction, i);
                edges.add(new Edge(previous, next));
                previous = next;
            }
        }
        return List.copyOf(edges);
    }

    private static BlockPos negativeControl(BlockPos controller) {
        BlockPos root = assemblyRoot(controller);
        // The pane is deliberately adjacent to a visually similar palette arm,
        // but brittle and unglued, so Create must leave it authoritative in-world.
        return route().mechanism == Mechanism.PULLEY
                ? root.east(5).north()
                : root.above(9).north();
    }

    private static BlockState negativeControlState() {
        return Blocks.MAGENTA_STAINED_GLASS_PANE.defaultBlockState();
    }

    private static BlockPos assemblyRoot(BlockPos controller) {
        return switch (route().mechanism) {
            case PISTON -> controller.relative(route().movementDirection);
            case GANTRY -> controller.south();
            case PULLEY -> controller.below();
        };
    }

    private static void seedMovedBlockEntities(
            ServerLevel level, Map<BlockPos, BlockState> expected) {
        for (Map.Entry<BlockPos, BlockState> entry : expected.entrySet()) {
            BlockEntity blockEntity = level.getBlockEntity(entry.getKey());
            if (!(blockEntity instanceof Container container)) continue;
            boolean shulker = entry.getValue().is(Blocks.BLUE_SHULKER_BOX);
            container.setItem(0, new ItemStack(shulker ? Items.EMERALD : Items.DIAMOND,
                    shulker ? 11 : 7));
            blockEntity.setChanged();
        }
        assertMovedBlockEntityContents(level, expected);
    }

    private static void assertMovedBlockEntityContents(
            ServerLevel level, Map<BlockPos, BlockState> expected) {
        int containers = 0;
        for (Map.Entry<BlockPos, BlockState> entry : expected.entrySet()) {
            if (!entry.getValue().is(Blocks.CHEST)
                    && !entry.getValue().is(Blocks.BLUE_SHULKER_BOX)) continue;
            if (!(level.getBlockEntity(entry.getKey()) instanceof Container container)) {
                throw new IllegalStateException("missing restored container at " + entry.getKey());
            }
            boolean shulker = entry.getValue().is(Blocks.BLUE_SHULKER_BOX);
            ItemStack stack = container.getItem(0);
            if (!stack.is(shulker ? Items.EMERALD : Items.DIAMOND)
                    || stack.getCount() != (shulker ? 11 : 7)) {
                throw new IllegalStateException("container state mismatch at " + entry.getKey()
                        + ": " + stack);
            }
            containers++;
        }
        if (containers != 2) throw new IllegalStateException("expected two moved containers");
    }

    private static void verifyCaptured(
            ServerLevel level, BlockPos controller, AbstractContraptionEntity entity) {
        Map<BlockPos, BlockState> expected = expectedStates(controller);
        if (route().mechanism == Mechanism.PISTON && expected.size() < 24) {
            throw new IllegalStateException("piston assembly is below 24 blocks");
        }
        for (Map.Entry<BlockPos, BlockState> entry : expected.entrySet()) {
            if (!level.getBlockState(entry.getKey()).isAir()) {
                throw new IllegalStateException("captured source remained at " + entry.getKey());
            }
            BlockPos local = entry.getKey().subtract(entity.getContraption().anchor);
            var info = entity.getContraption().getBlocks().get(local);
            if (info == null || !info.state().equals(entry.getValue())) {
                throw new IllegalStateException("captured state mismatch global=" + entry.getKey()
                        + " local=" + local + " info=" + info);
            }
        }
        if (!level.getBlockState(negativeControl(controller))
                .is(negativeControlState().getBlock())) {
            throw new IllegalStateException("unglued negative was captured");
        }
        assertGlue(level, glueEdges(controller), false);
        if (distinctMovedBlockEntityTypes(entity) < 2
                || !capturedNbt(entity).contains("minecraft:diamond")
                || !capturedNbt(entity).contains("minecraft:emerald")) {
            throw new IllegalStateException("moved block-entity NBT incomplete: "
                    + capturedNbt(entity));
        }
    }

    private static int distinctMovedBlockEntityTypes(AbstractContraptionEntity entity) {
        Set<String> ids = new java.util.HashSet<>();
        entity.getContraption().getBlocks().values().forEach(info -> {
            if (info.nbt() != null) ids.add(info.nbt().getString("id"));
        });
        return ids.size();
    }

    private static String capturedNbt(AbstractContraptionEntity entity) {
        List<String> values = new ArrayList<>();
        entity.getContraption().getBlocks().forEach((pos, info) -> {
            if (info.nbt() != null) values.add(pos + "=" + info.nbt());
        });
        return values.toString();
    }

    private static void triggerAssembly(BlockEntity controller) {
        switch (route().mechanism) {
            case PISTON -> invoke(controller, "assemble");
            case GANTRY -> invoke(controller, "queueAssembly");
            case PULLEY -> ((LinearActuatorBlockEntity) controller).assembleNextTick = true;
        }
    }

    private static AbstractContraptionEntity activeEntity(
            ServerLevel level, BlockEntity controller, BlockPos controllerPos) {
        if (controller instanceof LinearActuatorBlockEntity actuator) {
            return actuator.movedContraption;
        }
        List<GantryContraptionEntity> entities = level.getEntitiesOfClass(
                GantryContraptionEntity.class,
                new AABB(controllerPos).inflate(140.0, 32.0, 32.0));
        return entities.stream().filter(entity -> route().axisDistance(
                entity.position(), Vec3.atLowerCornerOf(controllerPos)) < 120.0)
                .findFirst().orElse(null);
    }

    private static double outwardMovement(
            BlockEntity controller, ServerLevel level, RingGeometry geometry) {
        if (controller instanceof LinearActuatorBlockEntity) {
            return ((Number) invoke(controller, "getMovementSpeed")).doubleValue();
        }
        BlockEntity shaft = level.getBlockEntity(canonical(
                controller.getBlockPos().north(), geometry));
        return ((Number) invoke(shaft, "getPinionMovementSpeed")).doubleValue();
    }

    private static BlockEntity requireController(ServerLevel level, BlockPos position) {
        BlockEntity controller = level.getBlockEntity(position);
        String expected = route().mechanism.controllerClass;
        if (controller == null || !controller.getClass().getName().equals(expected)) {
            throw new IllegalStateException("missing controller " + expected + " at "
                    + position + ": " + controller);
        }
        return controller;
    }

    private static void setMotorSpeed(ServerLevel level, BlockPos position, int speed) {
        BlockEntity motor = level.getBlockEntity(position);
        if (motor == null || !motor.getClass().getName().equals(
                "com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity")) {
            throw new IllegalStateException("missing creative motor at " + position + ": " + motor);
        }
        Object behaviour = field(motor, "generatedSpeed");
        invoke(behaviour, "setValue", int.class, speed);
    }

    private static void placeAndSetMotorSpeed(
            ServerLevel level, BlockPos position, int speed) {
        String facing = switch (route().mechanism) {
            case PISTON -> "up";
            case GANTRY -> route().movementDirection.getName();
            case PULLEY -> "north";
        };
        level.setBlockAndUpdate(position, motorState(facing));
        setMotorSpeed(level, position, speed);
    }

    private static BlockPos controllerPos(RingGeometry geometry) {
        int x = switch (route()) {
            case PISTON_HIGH, GANTRY_HIGH -> geometry.circumferenceBlocks() - 38;
            case PISTON_LOW, GANTRY_LOW -> 38;
            case PULLEY_NORMAL -> geometry.circumferenceBlocks() / 4;
        };
        return new BlockPos(x, route().mechanism == Mechanism.PULLEY ? Y + 20 : Y, 100);
    }

    private static double cameraPresentationX(RingGeometry geometry) {
        return switch (route()) {
            case PISTON_HIGH, GANTRY_HIGH -> geometry.circumferenceBlocks() - 8.0;
            case PISTON_LOW, GANTRY_LOW -> 8.0;
            case PULLEY_NORMAL -> geometry.circumferenceBlocks() / 4.0;
        };
    }

    private static BlockPos motorPos(BlockPos controller, RingGeometry geometry) {
        return switch (route().mechanism) {
            case PISTON -> controller.below();
            case GANTRY -> canonical(controller.north().relative(
                    route().movementDirection.getOpposite(), 97), geometry);
            case PULLEY -> controller.south();
        };
    }

    private static BlockPos canonical(BlockPos position, RingGeometry geometry) {
        return RingBlockCoordinates.canonicalBlockPos(position, geometry);
    }

    private static void prepareArena(
            ServerLevel level, BlockPos controller, RingGeometry geometry) {
        level.setDayTime(6_000L);
        level.setWeatherParameters(0, 120_000, false, false);
        int xRadius = route().mechanism == Mechanism.PULLEY ? 28 : 112;
        int minY = route().mechanism == Mechanism.PULLEY ? Y - 10 : Y - 3;
        int maxY = route().mechanism == Mechanism.PULLEY ? Y + 34 : Y + 16;
        for (int dx = -xRadius; dx <= xRadius; dx++) {
            int x = RingBlockCoordinates.canonicalBlockX(controller.getX() + dx, geometry);
            for (int z = 64; z <= 124; z++) {
                level.setBlock(new BlockPos(x, minY - 1, z),
                        Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = minY; y <= maxY; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        for (int dx = -28; dx <= 28; dx++) {
            int x = RingBlockCoordinates.canonicalBlockX(controller.getX() + dx, geometry);
            for (int y = minY; y <= maxY; y++) {
                level.setBlock(new BlockPos(x, y, 124),
                        Blocks.WHITE_CONCRETE.defaultBlockState(), 2);
            }
        }
    }

    private static boolean statesMatch(ServerLevel level, Map<BlockPos, BlockState> states) {
        return states.entrySet().stream().allMatch(entry ->
                level.getBlockState(entry.getKey()).equals(entry.getValue()));
    }

    private static void assertGlue(ServerLevel level, List<Edge> edges, boolean expected) {
        for (Edge edge : edges) {
            boolean actual = SuperGlueEntity.isGlued(
                    level, edge.first(), edge.direction(), null);
            if (actual != expected) {
                throw new IllegalStateException("glue edge " + edge + " expected="
                        + expected + " actual=" + actual);
            }
        }
    }

    private static boolean allGlue(ServerLevel level, List<Edge> edges) {
        return edges.stream().allMatch(edge -> SuperGlueEntity.isGlued(
                level, edge.first(), edge.direction(), null));
    }

    private static AABB assemblyBounds(java.util.Collection<BlockPos> positions) {
        AABB bounds = null;
        for (BlockPos pos : positions) {
            AABB block = new AABB(pos);
            bounds = bounds == null ? block : bounds.minmax(block);
        }
        if (bounds == null) throw new IllegalArgumentException("empty assembly positions");
        return bounds;
    }

    private static String inventory(ServerLevel level, Map<BlockPos, BlockState> states) {
        List<String> entries = new ArrayList<>();
        states.forEach((pos, state) -> {
            String extra = "";
            if (level.getBlockEntity(pos) instanceof Container container) {
                extra = "[slot0=" + container.getItem(0) + "]";
            }
            entries.add(pos + "=" + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                    + state.getValues() + extra);
        });
        return entries.toString();
    }

    private static List<Vec3> targetPoints(AbstractContraptionEntity entity) {
        List<Vec3> points = new ArrayList<>();
        entity.getContraption().getBlocks().keySet().forEach(local -> {
            for (int x = 0; x <= 1; x++) for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    points.add(entity.toGlobalVector(new Vec3(
                            local.getX() + x, local.getY() + y, local.getZ() + z), 1.0F));
                }
            }
        });
        return List.copyOf(points);
    }

    private static RingCreate610FixtureProjection.Projection payloadProjection(
            Minecraft client, AbstractContraptionEntity entity, BlockPos local,
            float yaw, float pitch) {
        RingGeometry geometry = ClientRingState.geometry();
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        RingObjectTransform anchor = RingObjectTransform.fromCameraRelative(
                geometry, camera, entity.getX() - camera.x,
                entity.getY() - camera.y, entity.getZ() - camera.z);
        double angle = anchor.tangentAngleRadians();
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        List<Vec3> points = new ArrayList<>();
        for (int x = 0; x <= 1; x++) for (int y = 0; y <= 1; y++) {
            for (int z = 0; z <= 1; z++) {
                Vec3 world = entity.toGlobalVector(new Vec3(
                        local.getX() + x, local.getY() + y, local.getZ() + z), 1.0F);
                Vec3 delta = world.subtract(entity.position());
                points.add(anchor.cameraLocalPosition().add(
                        cosine * delta.x - sine * delta.y,
                        sine * delta.x + cosine * delta.y,
                        delta.z));
            }
        }
        Vec3 center = points.stream().reduce(Vec3.ZERO, Vec3::add)
                .scale(1.0 / points.size());
        return RingCreate610FixtureProjection.projectCameraLocal(
                points, center, yaw, pitch, client.getMainRenderTarget().width,
                client.getMainRenderTarget().height, 70.0);
    }

    private static String bounds(RingCreate610FixtureProjection.Projection projection) {
        return String.format(java.util.Locale.ROOT, "%.2f/%.2f/%.2f/%.2f",
                projection.minX(), projection.minY(), projection.maxX(), projection.maxY());
    }

    private static String coordinates(BlockPos position) {
        return position.getX() + "/" + position.getY() + "/" + position.getZ();
    }

    private static Entity renderedEntity(Minecraft client, UUID uuid) {
        if (uuid == null) return null;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity.getUUID().equals(uuid)) return entity;
        }
        return null;
    }

    private static void orientClient(Minecraft client, float yaw, float pitch) {
        client.player.setYRot(yaw);
        client.player.yRotO = yaw;
        client.player.setYHeadRot(yaw);
        client.player.setXRot(pitch);
        client.player.xRotO = pitch;
    }

    private static BlockState motorState(String facing) {
        return withProperty(block("create:creative_motor").defaultBlockState(), "facing", facing);
    }

    private static Block block(String id) {
        Block result = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        if (result == Blocks.AIR) throw new IllegalStateException("missing block " + id);
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withProperty(BlockState state, String name, String value) {
        Property property = state.getProperties().stream()
                .filter(candidate -> candidate.getName().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "missing property " + name + " on " + state));
        java.util.Optional parsedValue = property.getValue(value);
        if (parsedValue.isEmpty()) {
            throw new IllegalStateException(
                    "invalid property " + name + "=" + value + " on " + state);
        }
        Comparable parsed = (Comparable) parsedValue.get();
        return state.setValue(property, parsed);
    }

    private static Object field(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("could not read " + name, failure);
            }
        }
        throw new IllegalStateException("missing field " + name + " on " + target.getClass());
    }

    private static Object invoke(Object target, String name) {
        return invoke(target, name, null, null);
    }

    private static Object invoke(Object target, String name, Class<?> parameter, Object argument) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = parameter == null
                        ? type.getDeclaredMethod(name) : type.getDeclaredMethod(name, parameter);
                method.setAccessible(true);
                return parameter == null ? method.invoke(target) : method.invoke(target, argument);
            } catch (NoSuchMethodException ignored) {
            } catch (ReflectiveOperationException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("could not invoke " + name, cause == null ? failure : cause);
            }
        }
        throw new IllegalStateException("missing method " + name + " on " + target.getClass());
    }

    private static String backend() {
        return Backend.REGISTRY.getIdOrThrow(BackendManager.currentBackend()).toString();
    }

    private static boolean offMode() { return backend().equals("flywheel:off"); }

    private static boolean durableEnabled() {
        return route().durable && requestedBackend().equals("default");
    }

    private static String requestedBackend() {
        return System.getProperty(BACKEND_PROPERTY, "default").trim().toLowerCase(Locale.ROOT);
    }

    private static Route route() {
        String id = System.getProperty(ROUTE_PROPERTY, "piston-high");
        for (Route route : Route.values()) if (route.id.equals(id)) return route;
        throw new IllegalStateException("unsupported linear route " + id);
    }

    private void advance(int next) {
        stage = next;
        stageTicks = 0;
    }

    private static void runAsync(String label, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            INSTANCE.asynchronousFailure = label + ": " + failure;
            RingWorldMod.LOGGER.error("[create-linear] asynchronous failure {}", label, failure);
        }
    }

    private static boolean finish(Minecraft client, boolean passed, String detail) {
        RingWorldMod.LOGGER.info("[create-linear] result={} {}", passed, detail);
        client.stop();
        return true;
    }

    private static String describe(Entity entity) {
        return entity == null ? "null" : entity.getId() + "/" + entity.getUUID()
                + "/object=" + System.identityHashCode(entity) + "/removed=" + entity.isRemoved();
    }

    private record Edge(BlockPos first, BlockPos second) {
        Direction direction() {
            int dx = second.getX() - first.getX();
            int dy = second.getY() - first.getY();
            int dz = second.getZ() - first.getZ();
            for (Direction direction : Direction.values()) {
                if (direction.getStepX() == dx && direction.getStepY() == dy
                        && direction.getStepZ() == dz) return direction;
            }
            throw new IllegalStateException("non-adjacent edge " + this);
        }
    }

    private enum Mechanism {
        PISTON("piston", "com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity"),
        GANTRY("gantry", "com.simibubi.create.content.contraptions.gantry.GantryCarriageBlockEntity"),
        PULLEY("pulley", "com.simibubi.create.content.contraptions.pulley.PulleyBlockEntity");

        private final String id;
        private final String controllerClass;

        Mechanism(String id, String controllerClass) {
            this.id = id;
            this.controllerClass = controllerClass;
        }
    }

    private enum Route {
        PISTON_HIGH("piston-high", Mechanism.PISTON, "high", "positive-seam",
                Direction.EAST, true, true, 8.0, 42.0, 25.0),
        PISTON_LOW("piston-low", Mechanism.PISTON, "low", "negative-seam",
                Direction.WEST, true, false, 8.0, 42.0, 25.0),
        GANTRY_HIGH("gantry-high", Mechanism.GANTRY, "high", "positive-seam",
                Direction.EAST, true, false, 8.0, 42.0, 25.0),
        GANTRY_LOW("gantry-low", Mechanism.GANTRY, "low", "negative-seam",
                Direction.WEST, true, false, 8.0, 42.0, 25.0),
        PULLEY_NORMAL("pulley-normal", Mechanism.PULLEY, "normal", "vertical-down-up",
                Direction.DOWN, false, false, 4.0, 12.0, 7.0);

        private final String id;
        private final Mechanism mechanism;
        private final String chart;
        private final String directionLabel;
        private final Direction movementDirection;
        private final boolean seam;
        private final boolean durable;
        private final double earlyProgress;
        private final double crossProgress;
        private final double reverseCaptureProgress;

        Route(String id, Mechanism mechanism, String chart, String directionLabel,
              Direction movementDirection, boolean seam, boolean durable,
              double earlyProgress, double crossProgress, double reverseCaptureProgress) {
            this.id = id;
            this.mechanism = mechanism;
            this.chart = chart;
            this.directionLabel = directionLabel;
            this.movementDirection = movementDirection;
            this.seam = seam;
            this.durable = durable;
            this.earlyProgress = earlyProgress;
            this.crossProgress = crossProgress;
            this.reverseCaptureProgress = reverseCaptureProgress;
        }

        double axisValue(Vec3 position) {
            return mechanism == Mechanism.PULLEY ? position.y : position.x;
        }

        double axisDistance(Vec3 first, Vec3 second) {
            return Math.abs(axisValue(first) - axisValue(second));
        }

        double progress(BlockEntity controller, AbstractContraptionEntity entity,
                        double initialAxis, RingGeometry geometry) {
            if (controller instanceof LinearActuatorBlockEntity actuator) {
                return Math.max(0.0, actuator.offset);
            }
            double current = new RingTopology(geometry).imageNear(entity.getX(), initialAxis);
            return Math.max(0.0, movementDirection.getStepX() * (current - initialAxis));
        }
    }
}
