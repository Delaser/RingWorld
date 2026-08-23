package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.net.RingMultiplayerTestPayload;
import dev.ringworld.client.chunk.RingClientChunkMaps;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.Minecraft;
import dev.ringworld.client.compat.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

/** A real-network, two-process client driver. It is dormant outside its JVM test flag. */
public final class MultiplayerTestClient {
    private static final int COMPLETION_GRACE_TICKS = 40;
    private final String role = System.getProperty("ringworld.multiplayerTestRole", "").trim();
    private boolean optionsApplied;
    private boolean connectionRequested;
    private boolean reconnectPending;
    private int menuTicks;
    private int stalledConnectionTicks;
    private int stage;
    private int stageTicks;
    private boolean readySent;

    private int positionedTicks;
    private boolean seamArmed;
    private double localSeamBoundary = Double.NaN;
    private double previousRemoteX = Double.NaN;
    private double maximumRemoteStep;
    private int remoteMissingTicks;
    private boolean remoteCrossedZero;

    private boolean blockSeen;
    private boolean interactionChunkReadySent;
    private boolean interactionSent;
    private int interactionAge;
    private boolean blockInteractionResultSent;
    private boolean forwardPlacementSent;
    private boolean forwardPlacementResultSent;
    private boolean reversePlacementSent;
    private boolean reversePlacementResultSent;
    private int attacksSent;

    private boolean vehicleSeen;
    private double previousVehicleX = Double.NaN;
    private float previousVehicleYaw;
    private float previousVehiclePitch;
    private double maximumVehicleStep;
    private float maximumVehicleRotationStep;
    private double maximumVehicleSpeed;
    private int vehicleMissingTicks;
    private int vehicleStateFailures;
    private int vehicleId = -1;
    private int vehiclePassengerId = -1;

    private boolean sawFarTeleport;
    private boolean sawRemoteFarTeleport;
    private boolean reconnectResultSent;
    private boolean extendedFixtureSent;
    private boolean bedSleepSent;
    private boolean sawSleeping;
    private int bedSleepObservedTicks;
    private boolean sleepingReconnectRequested;
    private int sleepingReconnectWaitTicks;
    private boolean sleepingReconnectResultSent;
    private boolean bedSleepRestartSent;
    private boolean bedDamageWakeSent;
    private boolean bedDestroyedSent;
    private boolean deathSeenSent;
    private boolean respawnRequested;
    private boolean deathRespawnSent;
    private boolean netherEnterSent;
    private boolean netherReturnSent;
    private boolean endEnterSent;
    private boolean endReturnSent;
    private boolean sawSeamLightning;
    private boolean seamWeatherSent;
    private int seamWeatherProbeTicks;
    private int completionGraceTicks;
    private boolean clientStopRequested;

    public boolean tick(Minecraft client) {
        if (role.isEmpty()) return false;
        if (!optionsApplied) {
            client.options.renderDistance().set(Math.clamp(Integer.getInteger(
                    "ringworld.multiplayerTestViewDistanceChunks", 2), 2, 32));
            client.options.simulationDistance().set(5);
            client.options.enableVsync().set(false);
            client.options.pauseOnLostFocus = false;
            client.options.onboardAccessibility = false;
            optionsApplied = true;
        }
        if (client.level == null || client.player == null) {
            connectWhenReady(client);
            return true;
        }
        if (client.screen instanceof PauseScreen) client.setScreen(null);
        if (!readySent && client.isGameLoadFinished()) {
            if (sendResult("client_ready", true, client.player.getX())) {
                readySent = true;
                RingWorldMod.LOGGER.info("[multiplayer:{}] client world fully loaded x={}",
                        role, client.player.getX());
            }
        }
        stageTicks++;
        switch (stage) {
            case 0 -> runSeamScenario(client);
            case 1 -> runCombatScenario(client);
            case 2 -> runInteractionScenario(client);
            case 3 -> runVehicleScenario(client);
            case 4 -> runTeleportScenario(client);
            case 5 -> runReconnectResult(client);
            case 6 -> runExtendedScenario(client);
            default -> { }
        }
        finishWhenLocalScenarioIsComplete(client);
        return true;
    }

    /**
     * Every required result is already on the wire before this grace period
     * begins. Exiting the graphical clients here lets the unattended harness
     * wait for clean process completion before stopping its dedicated server.
     */
    private void finishWhenLocalScenarioIsComplete(Minecraft client) {
        if (clientStopRequested || !localScenarioComplete()) return;
        if (++completionGraceTicks < COMPLETION_GRACE_TICKS) return;
        clientStopRequested = true;
        RingWorldMod.LOGGER.info(
                "[multiplayer:{}] local scenario result=true; stopping client", role);
        client.stop();
    }

    private boolean localScenarioComplete() {
        if (stage < 6 || !extendedFixtureSent || !seamWeatherSent) return false;
        if (role.equals("B")) return reconnectResultSent;
        return bedSleepSent && sleepingReconnectResultSent && bedSleepRestartSent
                && bedDamageWakeSent && bedDestroyedSent
                && deathSeenSent && deathRespawnSent
                && netherEnterSent && netherReturnSent
                && endEnterSent && endReturnSent;
    }

    private void connectWhenReady(Minecraft client) {
        // Client ticks begin while the initial SplashOverlay resource reload is
        // still running. Joining a world before it finishes can let random
        // display ticks request particle sprite providers whose prepared
        // sprite list has not been installed yet.
        if (!client.isGameLoadFinished()) {
            menuTicks = 0;
            return;
        }
        if (reconnectPending && client.screen != null) {
            connectionRequested = false;
            reconnectPending = false;
            menuTicks = 0;
            stalledConnectionTicks = 0;
            RingWorldMod.LOGGER.info("[multiplayer:{}] disconnected as planned; starting reconnect", role);
        }
        if (connectionRequested) {
            if (++stalledConnectionTicks % 200 == 0) {
                RingWorldMod.LOGGER.info("[multiplayer:{}] connection state screen={} networkHandler={}",
                        role, client.screen == null ? "none" : client.screen.getClass().getSimpleName()
                                + " title=" + client.screen.getNarrationMessage().getString(),
                        client.getConnection() != null);
            }
            return;
        }
        Screen parent = client.screen;
        if (parent == null) return;
        if (++menuTicks < 40) return;
        int port = Integer.getInteger("ringworld.multiplayerTestPort", 25566);
        String addressText = "127.0.0.1:" + port;
        ServerData server = new ServerData("RingWorld automated multiplayer", addressText,
                ServerData.Type.OTHER);
        connectionRequested = true;
        ServerAddress address = ServerAddress.parseString(addressText);
        RingWorldMod.LOGGER.info("[multiplayer:{}] connecting to {} from {}; resolved={}", role, addressText,
                parent.getClass().getSimpleName(), ServerNameResolver.DEFAULT.resolveAddress(address));
        // A non-null cookie store marks this as a 1.21 server-transfer login.
        ConnectScreen.startConnecting(parent, client, address, server, false, null);
    }

    private void runSeamScenario(Minecraft client) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || client.gameMode == null
                || client.gameMode.getPlayerMode() != GameType.SURVIVAL) return;
        boolean atTestPose = role.equals("A")
                ? Math.abs(geometry.shortestCircumferenceDelta(
                        client.player.getX(), geometry.circumferenceBlocks() - 4.0)) < 0.75
                : Math.abs(geometry.shortestCircumferenceDelta(client.player.getX(), 2.0)) < 0.75;
        if (!seamArmed && !atTestPose) return;

        AbstractClientPlayer remote = findRemotePlayer(client);
        if (remote == null) {
            if (seamArmed) remoteMissingTicks++;
            if (!seamArmed && stageTicks % 100 == 0) {
                RingWorldMod.LOGGER.info(
                        "[multiplayer:{}] waiting to arm seam localX={} atTestPose={} mode={} remote=missing",
                        role, client.player.getX(), atTestPose, client.gameMode.getPlayerMode());
            }
            return;
        }
        if (!seamArmed) {
            double expectedRemoteX = geometry.nearestImageX(
                    role.equals("A") ? 2.0 : geometry.circumferenceBlocks() - 4.0,
                    client.player.getX());
            if (Math.abs(remote.getX() - expectedRemoteX) >= 0.75) {
                if (stageTicks % 100 == 0) {
                    RingWorldMod.LOGGER.info(
                            "[multiplayer:{}] waiting to arm seam localX={} atTestPose={} mode={} remoteX={} expectedRemoteX={}",
                            role, client.player.getX(), atTestPose, client.gameMode.getPlayerMode(),
                            remote.getX(), expectedRemoteX);
                }
                return;
            }
        }
        positionedTicks++;
        if (!seamArmed && positionedTicks >= 80) {
            seamArmed = true;
            if (role.equals("A")) {
                localSeamBoundary = geometry.nextPositiveSeamX(client.player.getX());
            }
            previousRemoteX = remote.getX();
            sendResult("movement_started", true, client.player.getX());
            RingWorldMod.LOGGER.info(
                    "[multiplayer:{}] armed localX={} localSeam={} remote={} remoteLogicalX={} shortestDistance={}",
                    role, client.player.getX(), localSeamBoundary,
                    remote.getName().getString(), remote.getX(),
                    Math.abs(geometry.shortestCircumferenceDelta(client.player.getX(), remote.getX())));
        }
        if (!seamArmed) return;

        double remoteStep = Math.abs(remote.getX() - previousRemoteX);
        maximumRemoteStep = Math.max(maximumRemoteStep, remoteStep);
        if (previousRemoteX < 0.0 && remote.getX() >= 0.0) remoteCrossedZero = true;
        previousRemoteX = remote.getX();

        if (role.equals("A") && client.player.getX() < localSeamBoundary) {
            double nextX = Math.min(localSeamBoundary,
                    client.player.getX() + 0.25);
            client.player.setPos(nextX, client.player.getY(), client.player.getZ());
            client.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                    nextX, client.player.getY(), client.player.getZ(),
                    client.player.getYRot(), client.player.getXRot(),
                    client.player.onGround()));
            return;
        }

        boolean localCrossed = role.equals("B")
                ? remoteCrossedZero
                : client.player.getX() >= localSeamBoundary;
        if (!localCrossed) return;

        boolean remoteStillAdjacent = Math.abs(geometry.shortestCircumferenceDelta(
                client.player.getX(), remote.getX())) < 12.0;
        boolean passed = remoteMissingTicks == 0 && maximumRemoteStep <= 1.25 && remoteStillAdjacent;
        RingWorldMod.LOGGER.info(
                "[multiplayer:{}] client seam result={} localX={} remoteX={} maxRemoteStep={} missingTicks={}",
                role, passed, client.player.getX(), remote.getX(), maximumRemoteStep, remoteMissingTicks);
        sendResult("seam_visibility", passed, maximumRemoteStep);
        Screenshot.grab(client.gameDirectory,
                "ringworld-multiplayer-" + role.toLowerCase() + ".png",
                client.getMainRenderTarget(), 1,
                message -> RingWorldMod.LOGGER.info(
                        "[multiplayer:{}] screenshot: {}", role, message.getString()));
        stage = 1;
        stageTicks = 0;
    }

    private void runCombatScenario(Minecraft client) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || client.gameMode == null) return;

        if (role.equals("B")
                && (client.player.getHealth() < client.player.getMaxHealth()
                || client.player.hurtTime > 0)) {
            RingWorldMod.LOGGER.info(
                    "[multiplayer:B] cross-seam melee result=true health={} hurtTime={}",
                    client.player.getHealth(), client.player.hurtTime);
            sendResult("melee_combat", true, client.player.getHealth());
            stage = 2;
            stageTicks = 0;
            return;
        }

        // A's responsibility is to put a real attack packet on the wire. The
        // server independently proves that B lost health before advancing;
        // do not make test sequencing depend on the decorative result marker,
        // because cross-seam block delivery is asserted in the next phase.
        if (role.equals("A") && attacksSent > 0 && stageTicks >= 40) {
            sendResult("melee_combat", true, attacksSent);
            stage = 2;
            stageTicks = 0;
            return;
        }

        int markerX = (int)Math.floor(geometry.nearestImageX(0.0, client.player.getX()));
        BlockPos marker = new BlockPos(markerX, 123, 4);
        if (client.level.getBlockState(marker).is(Blocks.LIME_CONCRETE)) {
            RingWorldMod.LOGGER.info("[multiplayer:{}] cross-seam melee result=true attacksSent={}",
                    role, attacksSent);
            sendResult("melee_combat", true, attacksSent);
            stage = 2;
            stageTicks = 0;
            return;
        }
        if (client.level.getBlockState(marker).is(Blocks.RED_CONCRETE) || stageTicks >= 1_300) {
            RingWorldMod.LOGGER.error("[multiplayer:{}] cross-seam melee result=false attacksSent={}",
                    role, attacksSent);
            sendResult("melee_combat", false, attacksSent);
            stage = 2;
            stageTicks = 0;
            return;
        }

        AbstractClientPlayer remote = findRemotePlayer(client);
        if (role.equals("A") && remote != null && stageTicks >= 20 && stageTicks % 20 == 0) {
            client.gameMode.attack(client.player, remote);
            client.player.swing(InteractionHand.MAIN_HAND);
            attacksSent++;
            RingWorldMod.LOGGER.info(
                    "[multiplayer:A] sent cross-seam melee attack localX={} remoteX={} periodicDistance={}",
                    client.player.getX(), remote.getX(),
                    Math.abs(geometry.shortestCircumferenceDelta(client.player.getX(), remote.getX())));
        }
    }

    private void runInteractionScenario(Minecraft client) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || client.gameMode == null) return;
        int logicalX = (int)Math.floor(geometry.nearestImageX(1.0, client.player.getX()));
        BlockPos target = new BlockPos(logicalX, 119, 0);
        if (!interactionChunkReadySent
                && client.level.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4)) {
            interactionChunkReadySent = true;
            sendResult("interaction_chunk_ready", true, target.getX());
            RingWorldMod.LOGGER.info("[multiplayer:{}] interaction chunk ready at {}", role, target);
        }
        if (!interactionChunkReadySent) {
            if (stageTicks % 100 == 0) {
                var storage = RingClientChunkMaps.get(client.level.getChunkSource());
                RingWorldMod.LOGGER.info(
                        "[multiplayer:{}] interaction chunk waiting targetChunk={},{} playerChunk={},{} cacheCenter={},{}",
                        role, target.getX() >> 4, target.getZ() >> 4,
                        client.player.chunkPosition().x, client.player.chunkPosition().z,
                        storage == null ? Integer.MIN_VALUE : storage.ringworld$centerChunkX(),
                        storage == null ? Integer.MIN_VALUE : storage.ringworld$centerChunkZ());
            }
            return;
        }
        boolean targetPresent = client.level.getBlockState(target).is(Blocks.GOLD_BLOCK);
        blockSeen |= targetPresent;
        if (stageTicks % 100 == 0) {
            RingWorldMod.LOGGER.info(
                    "[multiplayer:{}] seam block probe target={} state={} seen={} playerX={}",
                    role, target, client.level.getBlockState(target).getBlock(), blockSeen,
                    client.player.getX());
        }

        // Give the observer client time to receive and render the armed gold
        // block before A removes it; otherwise both updates can coalesce.
        if (role.equals("A") && blockSeen && !interactionSent && stageTicks >= 100) {
            interactionSent = client.gameMode.startDestroyBlock(target, Direction.UP);
            if (interactionSent) {
                interactionAge = 0;
                RingWorldMod.LOGGER.info("[multiplayer:A] attacked logical seam block {}", target);
            }
        }
        if (interactionSent) interactionAge++;

        if (!blockInteractionResultSent && blockSeen && !targetPresent
                && (!role.equals("A") || interactionAge >= 5)) {
            RingWorldMod.LOGGER.info("[multiplayer:{}] cross-seam block update result=true at {}", role, target);
            sendResult("block_interaction", true, stageTicks);
            blockInteractionResultSent = true;
            stageTicks = 0;
            return;
        } else if (!blockInteractionResultSent && stageTicks >= 600) {
            RingWorldMod.LOGGER.error("[multiplayer:{}] cross-seam block update result=false", role);
            sendResult("block_interaction", false, stageTicks);
            blockInteractionResultSent = true;
            stageTicks = 0;
            return;
        }

        if (!blockInteractionResultSent) return;
        if (runSeamPlacementScenario(client, geometry)) {
            stage = 3;
            stageTicks = 0;
        }
    }

    private boolean runSeamPlacementScenario(Minecraft client, RingGeometry geometry) {
        int highX = (int)Math.floor(geometry.nearestImageX(
                geometry.circumferenceBlocks() - 1.0, client.player.getX()));
        int zeroX = (int)Math.floor(geometry.nearestImageX(0.0, client.player.getX()));
        BlockPos forwardSupport = new BlockPos(highX, 119, 2);
        BlockPos forwardTarget = new BlockPos(highX + 1, 119, 2);
        BlockPos reverseSupport = new BlockPos(zeroX, 119, 3);
        BlockPos reverseTarget = new BlockPos(zeroX - 1, 119, 3);

        boolean forwardFixture = client.level.getBlockState(forwardSupport).is(Blocks.STONE);
        boolean forwardPlaced = client.level.getBlockState(forwardTarget).is(Blocks.STONE);
        if (!forwardPlacementResultSent) {
            if (role.equals("A") && forwardFixture && !forwardPlaced
                    && !forwardPlacementSent
                    && client.gameMode.getPlayerMode() == GameType.SURVIVAL
                    && client.player.getMainHandItem().is(Blocks.STONE.asItem())
                    && client.player.getMainHandItem().getCount() == 2) {
                BlockHitResult hit = new BlockHitResult(
                        new Vec3(forwardSupport.getX() + 1.0, 119.5, 2.5),
                        Direction.EAST, forwardSupport, false);
                InteractionResult result = client.gameMode.useItemOn(
                        client.player, InteractionHand.MAIN_HAND, hit);
                forwardPlacementSent = result.consumesAction();
                RingWorldMod.LOGGER.info(
                        "[multiplayer:A] forward seam placement sent={} support={} hitX={} target={}",
                        forwardPlacementSent, forwardSupport, hit.getLocation().x, forwardTarget);
            }
            if (forwardFixture && forwardPlaced) {
                forwardPlacementResultSent = true;
                sendResult("placement_forward", true, stageTicks);
                RingWorldMod.LOGGER.info(
                        "[multiplayer:{}] forward seam placement result=true target={}", role, forwardTarget);
                stageTicks = 0;
            } else if (stageTicks >= 600) {
                forwardPlacementResultSent = true;
                sendResult("placement_forward", false, stageTicks);
                RingWorldMod.LOGGER.error(
                        "[multiplayer:{}] forward seam placement result=false support={} targetState={}",
                        role, forwardSupport, client.level.getBlockState(forwardTarget).getBlock());
                stageTicks = 0;
            }
            return false;
        }

        boolean reverseFixture = client.level.getBlockState(reverseSupport).is(Blocks.STONE);
        boolean reversePlaced = client.level.getBlockState(reverseTarget).is(Blocks.STONE);
        if (!reversePlacementResultSent) {
            if (role.equals("A") && reverseFixture && !reversePlaced
                    && !reversePlacementSent
                    && client.gameMode.getPlayerMode() == GameType.SURVIVAL
                    && client.player.getMainHandItem().is(Blocks.STONE.asItem())
                    && client.player.getMainHandItem().getCount() == 2) {
                BlockHitResult hit = new BlockHitResult(
                        new Vec3(reverseSupport.getX(), 119.5, 3.5),
                        Direction.WEST, reverseSupport, false);
                InteractionResult result = client.gameMode.useItemOn(
                        client.player, InteractionHand.MAIN_HAND, hit);
                reversePlacementSent = result.consumesAction();
                RingWorldMod.LOGGER.info(
                        "[multiplayer:A] reverse seam placement sent={} support={} hitX={} target={}",
                        reversePlacementSent, reverseSupport, hit.getLocation().x, reverseTarget);
            }
            if (reverseFixture && reversePlaced) {
                reversePlacementResultSent = true;
                sendResult("placement_reverse", true, stageTicks);
                RingWorldMod.LOGGER.info(
                        "[multiplayer:{}] reverse seam placement result=true target={}", role, reverseTarget);
            } else if (stageTicks >= 600) {
                reversePlacementResultSent = true;
                sendResult("placement_reverse", false, stageTicks);
                RingWorldMod.LOGGER.error(
                        "[multiplayer:{}] reverse seam placement result=false support={} targetState={}",
                        role, reverseSupport, client.level.getBlockState(reverseTarget).getBlock());
            }
        }
        return reversePlacementResultSent;
    }

    private void runVehicleScenario(Minecraft client) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;
        Boat boat = findBoat(client);
        if (boat == null) {
            if (vehicleSeen) vehicleMissingTicks++;
            if (stageTicks >= 600) {
                sendResult("vehicle_visibility", false, vehicleMissingTicks);
                stage = 4;
                stageTicks = 0;
            }
            return;
        }
        if (!vehicleSeen) {
            if (boat.getPassengers().size() != 1) return;
            Entity passenger = boat.getPassengers().getFirst();
            vehicleSeen = true;
            vehicleId = boat.getId();
            vehiclePassengerId = passenger.getId();
            previousVehicleX = boat.getX();
            previousVehicleYaw = boat.getYRot();
            previousVehiclePitch = boat.getXRot();
            sendResult("vehicle_acquired", true, boat.getX());
            RingWorldMod.LOGGER.info(
                    "[multiplayer:{}] acquired seam vehicle id={} passengerId={} x={} yaw={} pitch={} velocity={}",
                    role, vehicleId, vehiclePassengerId, boat.getX(),
                    boat.getYRot(), boat.getXRot(), boat.getDeltaMovement());
            return;
        } else {
            maximumVehicleStep = Math.max(maximumVehicleStep, Math.abs(boat.getX() - previousVehicleX));
            maximumVehicleRotationStep = Math.max(maximumVehicleRotationStep,
                    Math.max(Math.abs(Mth.wrapDegrees(boat.getYRot() - previousVehicleYaw)),
                            Math.abs(Mth.wrapDegrees(boat.getXRot() - previousVehiclePitch))));
            maximumVehicleSpeed = Math.max(maximumVehicleSpeed, boat.getDeltaMovement().length());
            previousVehicleX = boat.getX();
            previousVehicleYaw = boat.getYRot();
            previousVehiclePitch = boat.getXRot();
            Entity passenger = boat.getPassengers().size() == 1
                    ? boat.getPassengers().getFirst()
                    : null;
            if (boat.getId() != vehicleId || passenger == null
                    || passenger.getId() != vehiclePassengerId
                    || passenger.getVehicle() != boat) {
                vehicleStateFailures++;
            }
        }
        double observedSeam = geometry.nearestImageX(0.0, client.player.getX());
        boolean crossed = boat.getX() >= observedSeam;
        if (crossed) {
            boolean passed = vehicleMissingTicks == 0
                    && vehicleStateFailures == 0
                    && maximumVehicleStep <= 1.0
                    && maximumVehicleRotationStep <= 1.0f
                    && maximumVehicleSpeed <= 0.05;
            RingWorldMod.LOGGER.info(
                    "[multiplayer:{}] vehicle visibility result={} id={} passengerId={} x={} localSeam={} "
                            + "maxStep={} missingTicks={} stateFailures={} maxRotationStep={} maxSpeed={}",
                    role, passed, vehicleId, vehiclePassengerId, boat.getX(), observedSeam,
                    maximumVehicleStep, vehicleMissingTicks, vehicleStateFailures,
                    maximumVehicleRotationStep, maximumVehicleSpeed);
            sendResult("vehicle_visibility", passed, maximumVehicleStep);
            stage = 4;
            stageTicks = 0;
        }
    }

    private void runTeleportScenario(Minecraft client) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;
        if (role.equals("A")) {
            if (!sawFarTeleport && Math.abs(client.player.getX() - 64.5) < 1.0) {
                sawFarTeleport = true;
                RingWorldMod.LOGGER.info("[multiplayer:A] intentional long teleport preserved at x={}",
                        client.player.getX());
                sendResult("intentional_teleport", true, client.player.getX());
            } else if (sawFarTeleport
                    && Math.abs(geometry.shortestCircumferenceDelta(
                            client.player.getX(), geometry.circumferenceBlocks() - 4.0)) < 1.0) {
                RingWorldMod.LOGGER.info("[multiplayer:A] return teleport preserved at x={}", client.player.getX());
                sendResult("teleport_return", true, client.player.getX());
                stage = 5;
                stageTicks = 0;
            } else if (stageTicks >= 1_000) {
                sendResult(sawFarTeleport ? "teleport_return" : "intentional_teleport", false,
                        client.player.getX());
                stage = 5;
                stageTicks = 0;
            }
            return;
        }

        AbstractClientPlayer remote = findRemotePlayer(client);
        if (remote == null) {
            sawRemoteFarTeleport = true;
            return;
        }
        if (Math.abs(geometry.shortestCircumferenceDelta(client.player.getX(), remote.getX())) > 32.0) {
            sawRemoteFarTeleport = true;
            return;
        }
        if (sawRemoteFarTeleport
                && Math.abs(geometry.shortestCircumferenceDelta(remote.getX(),
                        geometry.circumferenceBlocks() - 4.0)) < 4.0) {
            RingWorldMod.LOGGER.info("[multiplayer:B] remote player untracked and reappeared; disconnecting for reconnect test");
            stage = 5;
            stageTicks = 0;
            reconnectPending = true;
            client.getConnection().getConnection().disconnect(Component.literal("RingWorld automated reconnect"));
        }
    }

    private void runReconnectResult(Minecraft client) {
        if (role.equals("A")) {
            if (stageTicks >= 40) {
                stage = 6;
                stageTicks = 0;
            }
            return;
        }
        if (reconnectResultSent || stageTicks < 40) return;
        RingGeometry geometry = ClientRingState.geometry();
        AbstractClientPlayer remote = findRemotePlayer(client);
        if ((geometry == null || remote == null) && stageTicks < 600) return;
        boolean passed = geometry != null && remote != null
                && Math.abs(geometry.shortestCircumferenceDelta(client.player.getX(), remote.getX())) < 12.0;
        RingWorldMod.LOGGER.info("[multiplayer:B] reconnect result={} localX={} remote={}",
                passed, client.player.getX(), remote == null ? "missing" : remote.getX());
        sendResult("reconnect", passed, remote == null ? Double.NaN : remote.getX());
        reconnectResultSent = true;
        stage = 6;
        stageTicks = 0;
    }

    private void runExtendedScenario(Minecraft client) {
        if (role.equals("A") && deathRespawnSent) {
            if (client.level.dimension() == Level.NETHER && !netherEnterSent) {
                netherEnterSent = true;
                sendResult("nether_enter", ClientRingState.geometry() == null, client.player.getX());
                RingWorldMod.LOGGER.info("[multiplayer:A] physical Nether portal entered x={} ringStateCleared={}",
                        client.player.getX(), ClientRingState.geometry() == null);
            } else if (netherEnterSent && client.level.dimension() == Level.OVERWORLD
                    && !netherReturnSent) {
                netherReturnSent = true;
                sendResult("nether_return", ClientRingState.geometry() != null, client.player.getX());
                RingWorldMod.LOGGER.info("[multiplayer:A] physical Nether portal return x={} ringStateReady={}",
                        client.player.getX(), ClientRingState.geometry() != null);
            } else if (netherReturnSent && client.level.dimension() == Level.END && !endEnterSent) {
                endEnterSent = true;
                sendResult("end_enter", ClientRingState.geometry() == null, client.player.getX());
                RingWorldMod.LOGGER.info("[multiplayer:A] physical End portal entered x={} ringStateCleared={}",
                        client.player.getX(), ClientRingState.geometry() == null);
            } else if (endEnterSent && client.level.dimension() == Level.OVERWORLD && !endReturnSent) {
                endReturnSent = true;
                RingGeometry returnedGeometry = ClientRingState.geometry();
                boolean canonical = returnedGeometry != null
                        && returnedGeometry.wrapX(client.player.getX()) >= 0.0
                        && returnedGeometry.wrapX(client.player.getX())
                        < returnedGeometry.circumferenceBlocks();
                sendResult("end_return", canonical, client.player.getX());
                RingWorldMod.LOGGER.info("[multiplayer:A] physical End portal return={} x={} ringStateReady={}",
                        canonical, client.player.getX(), returnedGeometry != null);
            }
        }

        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;

        if (!seamWeatherSent && client.level.dimension() == Level.OVERWORLD) {
            int lightningEntities = 0;
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity instanceof LightningBolt) {
                    lightningEntities++;
                    sawSeamLightning = true;
                }
            }
            int skyFlashTime = client.level.getSkyFlashTime();
            if (skyFlashTime > 0) sawSeamLightning = true;
            boolean storm = client.level.getRainLevel(1.0F) >= 0.95F
                    && client.level.getThunderLevel(1.0F) >= 0.95F;
            if (++seamWeatherProbeTicks % 100 == 0) {
                RingWorldMod.LOGGER.info(
                        "[multiplayer:{}] waiting for seam weather rain={} thunder={} lightningEntities={} skyFlashTime={} sawLightning={}",
                        role, client.level.getRainLevel(1.0F), client.level.getThunderLevel(1.0F),
                        lightningEntities, skyFlashTime, sawSeamLightning);
            }
            if (storm && sawSeamLightning) {
                seamWeatherSent = true;
                sendResult("seam_weather", true, client.player.getX());
                Screenshot.grab(client.gameDirectory,
                        "ringworld-multiplayer-weather-" + role.toLowerCase() + ".png",
                        client.getMainRenderTarget(), 1,
                        message -> RingWorldMod.LOGGER.info(
                                "[multiplayer:{}] weather screenshot: {}", role, message.getString()));
                RingWorldMod.LOGGER.info(
                        "[multiplayer:{}] seam thunder/lightning result=true rain={} thunder={} x={}",
                        role, client.level.getRainLevel(1.0F),
                        client.level.getThunderLevel(1.0F), client.player.getX());
            }
        }

        if (!extendedFixtureSent) {
            int chestX = presentationX(geometry, client, 0);
            int lecternX = presentationX(geometry, client, 1);
            BlockPos chest = new BlockPos(chestX, 120, -3);
            BlockPos chestHigh = new BlockPos(presentationX(
                    geometry, client, geometry.circumferenceBlocks() - 1), 120, -3);
            BlockPos lectern = new BlockPos(lecternX, 120, -3);
            BlockPos lamp = new BlockPos(chestX, 120, -5);
            // The sealed trough clears X=0 before placing its only source at
            // C-1. This is the received destination image, not the source.
            BlockPos fluidDestination = new BlockPos(presentationX(geometry, client, 0), 120, 6);
            BlockPos blast = new BlockPos(chestX, 124, 9);
            boolean chestReady = client.level.getBlockEntity(chest) instanceof ChestBlockEntity
                    && client.level.getBlockEntity(chestHigh) instanceof ChestBlockEntity
                    && client.level.getBlockState(chest).getValue(net.minecraft.world.level.block.ChestBlock.TYPE)
                    != net.minecraft.world.level.block.state.properties.ChestType.SINGLE
                    && client.level.getBlockState(chestHigh).getValue(net.minecraft.world.level.block.ChestBlock.TYPE)
                    != net.minecraft.world.level.block.state.properties.ChestType.SINGLE;
            boolean lecternReady = client.level.getBlockEntity(lectern) instanceof LecternBlockEntity
                    && client.level.getBlockState(lectern).getValue(LecternBlock.HAS_BOOK);
            boolean lampLit = client.level.getBlockState(lamp)
                    .getOptionalValue(BlockStateProperties.LIT).orElse(false);
            boolean waterReachedDestination = !client.level.getFluidState(fluidDestination).isEmpty();
            boolean explosionCrossed = client.level.getBlockState(blast).isAir();
            if (stageTicks % 200 == 0) {
                RingWorldMod.LOGGER.info(
                        "[multiplayer:{}] waiting for extended fixture chest={} chestState={} highState={} chestBe={} highBe={} lectern={} lamp={} waterReachedDestination={} blast={}",
                        role, chestReady, client.level.getBlockState(chest),
                        client.level.getBlockState(chestHigh), client.level.getBlockEntity(chest),
                        client.level.getBlockEntity(chestHigh), lecternReady, lampLit,
                        waterReachedDestination, explosionCrossed);
            }
            if (chestReady && lecternReady && lampLit && waterReachedDestination && explosionCrossed) {
                extendedFixtureSent = true;
                sendResult("extended_fixture", true, stageTicks);
                RingWorldMod.LOGGER.info(
                        "[multiplayer:{}] extended seam fixture=true chest={} lectern={} lamp={} waterReachedDestination={} blast={}",
                        role, chest, lectern, lamp, fluidDestination, blast);
            } else if (stageTicks >= 1_200) {
                extendedFixtureSent = true;
                sendResult("extended_fixture", false, stageTicks);
                RingWorldMod.LOGGER.error(
                        "[multiplayer:{}] extended seam fixture=false chest={} lectern={} lamp={} waterReachedDestination={} blast={}",
                        role, chestReady, lecternReady, lampLit, waterReachedDestination, explosionCrossed);
            }
        }

        if (!role.equals("A")) return;
        if (client.player.isSleeping()) {
            sawSleeping = true;
            if (!bedSleepSent) {
                BlockPos canonicalBed = new BlockPos(1, 120, -1);
                int expectedX = presentationX(geometry, client, canonicalBed.getX());
                boolean nearestBed = client.player.getSleepingPos()
                        .map(pos -> pos.equals(new BlockPos(expectedX, 120, -1)))
                        .orElse(false);
                bedSleepSent = true;
                sendResult("bed_sleep", nearestBed, expectedX);
                RingWorldMod.LOGGER.info(
                        "[multiplayer:A] seam bed sleep={} sleepingPos={} expectedX={} playerX={}",
                        nearestBed, client.player.getSleepingPos().orElse(null), expectedX,
                        client.player.getX());
            }
            if (!sleepingReconnectRequested && ++bedSleepObservedTicks >= 20) {
                sleepingReconnectRequested = true;
                reconnectPending = true;
                RingWorldMod.LOGGER.info(
                        "[multiplayer:A] disconnecting while asleep for seam-bed reconnect test");
                client.getConnection().getConnection().disconnect(
                        Component.literal("RingWorld automated sleeping reconnect"));
                return;
            }
            if (sleepingReconnectRequested && sleepingReconnectResultSent
                    && !bedSleepRestartSent) {
                BlockPos canonicalBed = new BlockPos(1, 120, -1);
                int expectedX = presentationX(geometry, client, canonicalBed.getX());
                boolean nearestBed = client.player.getSleepingPos()
                        .map(pos -> pos.equals(new BlockPos(expectedX, 120, -1)))
                        .orElse(false);
                boolean adjacent = Math.abs(geometry.shortestCircumferenceDelta(
                        client.player.getX(), canonicalBed.getX())) < 4.0;
                bedSleepRestartSent = true;
                sendResult("bed_sleep_restart", nearestBed && adjacent, client.player.getX());
                RingWorldMod.LOGGER.info(
                        "[multiplayer:A] seam bed post-reconnect sleep={} sleepingPos={} expectedX={} playerX={}",
                        nearestBed && adjacent, client.player.getSleepingPos().orElse(null),
                        expectedX, client.player.getX());
            }
        } else if (sawSleeping && !bedDamageWakeSent
                && (client.player.hurtTime > 0 || client.player.getHealth() < client.player.getMaxHealth())) {
            bedDamageWakeSent = true;
            boolean adjacent = Math.abs(geometry.shortestCircumferenceDelta(
                    client.player.getX(), 1.0)) < 4.0;
            sendResult("bed_damage_wake", adjacent, client.player.getX());
            RingWorldMod.LOGGER.info("[multiplayer:A] seam bed damage wake={} x={} hurtTime={}",
                    adjacent, client.player.getX(), client.player.hurtTime);
        }

        if (sleepingReconnectRequested && !sleepingReconnectResultSent
                && ++sleepingReconnectWaitTicks >= 20) {
            BlockPos canonicalBed = new BlockPos(1, 120, -1);
            BlockPos presentedBed = new BlockPos(
                    presentationX(geometry, client, canonicalBed.getX()),
                    canonicalBed.getY(), canonicalBed.getZ());
            boolean adjacent = Math.abs(geometry.shortestCircumferenceDelta(
                    client.player.getX(), canonicalBed.getX())) < 4.0
                    && Math.abs(client.player.getY() - canonicalBed.getY()) < 4.0
                    && Math.abs(client.player.getZ() - canonicalBed.getZ()) < 4.0;
            boolean overworldSessionReady = client.level.dimension() == Level.OVERWORLD
                    && ClientRingState.geometry() != null;
            boolean bedStillLoaded = client.level.hasChunkAt(presentedBed)
                    && client.level.getBlockState(presentedBed).is(Blocks.RED_BED);
            boolean vanillaAwake = !client.player.isSleeping()
                    && client.player.getSleepingPos().isEmpty();
            sleepingReconnectResultSent = true;
            sendResult("bed_reconnect",
                    vanillaAwake && adjacent && overworldSessionReady && bedStillLoaded,
                    client.player.getX());
            RingWorldMod.LOGGER.info(
                    "[multiplayer:A] seam bed reconnect={} vanillaAwake={} sessionReady={} bedLoaded={} sleepingPos={} pos={}",
                    vanillaAwake && adjacent && overworldSessionReady && bedStillLoaded,
                    vanillaAwake, overworldSessionReady, bedStillLoaded,
                    client.player.getSleepingPos().orElse(null), client.player.position());
        }

        if (bedDamageWakeSent && !bedDestroyedSent) {
            BlockPos foot = new BlockPos(presentationX(geometry, client, 0), 120, -1);
            BlockPos head = new BlockPos(presentationX(geometry, client, 1), 120, -1);
            if (client.level.getBlockState(foot).isAir()
                    && client.level.getBlockState(head).isAir()
                    && client.player.getSleepingPos().isEmpty()) {
                bedDestroyedSent = true;
                sendResult("bed_destroyed", true, client.player.getX());
                RingWorldMod.LOGGER.info("[multiplayer:A] seam bed destruction=true x={}",
                        client.player.getX());
            }
        }

        if (client.screen instanceof DeathScreen && !respawnRequested) {
            deathSeenSent = true;
            respawnRequested = true;
            sendResult("death_seen", true, client.player.getX());
            RingWorldMod.LOGGER.info("[multiplayer:A] death screen observed at x={}; requesting respawn",
                    client.player.getX());
            client.player.respawn();
            return;
        }
        if (respawnRequested && !deathRespawnSent && !(client.screen instanceof DeathScreen)
                && client.player.isAlive() && client.player.getHealth() > 0.0F) {
            double canonicalX = geometry.wrapX(client.player.getX());
            boolean canonical = canonicalX >= 0.0
                    && canonicalX < geometry.circumferenceBlocks();
            deathRespawnSent = true;
            sendResult("death_respawn", deathSeenSent && canonical, client.player.getX());
            RingWorldMod.LOGGER.info("[multiplayer:A] death respawn={} x={} health={}",
                    deathSeenSent && canonical, client.player.getX(), client.player.getHealth());
        }
    }

    private static int presentationX(RingGeometry geometry, Minecraft client, double canonicalX) {
        return (int)Math.floor(geometry.nearestImageX(canonicalX, client.player.getX()));
    }

    private boolean sendResult(String phase, boolean passed, double value) {
        if (!RingClientPayloadTransport.canSend(RingMultiplayerTestPayload.ID)) return false;
        RingClientPayloadTransport.send(new RingMultiplayerTestPayload(role, phase, passed, value));
        return true;
    }

    private AbstractClientPlayer findRemotePlayer(Minecraft client) {
        if (client.level == null || client.player == null) return null;
        for (AbstractClientPlayer player : client.level.players()) {
            if (player != client.player && player.getName().getString().startsWith("RingTester")) return player;
        }
        return null;
    }

    private Boat findBoat(Minecraft client) {
        if (client.level == null) return null;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof Boat boat) return boat;
        }
        return null;
    }
}
