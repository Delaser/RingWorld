package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

/** Dedicated-server half of the opt-in, two-real-client multiplayer regression. */
public final class RingWorldMultiplayerTest {
    private static final Map<String, Boolean> CLIENT_RESULTS = new HashMap<>();
    private static int stage;
    private static int ticks;
    private static boolean initialized;
    private static double previousAX = Double.NaN;
    private static double maximumAStep;
    private static double maximumAPacketStep;
    private static boolean sawCanonicalPlayerWrap;
    private static boolean serverSeamPassed;
    private static boolean loggedWaiting;
    private static boolean combatPassed;
    private static boolean interactionPassed;
    private static boolean interactionArmed;
    private static boolean vehiclePassed;
    private static boolean sawVehicleHighSide;
    private static boolean sawCanonicalVehicleWrap;
    private static boolean vehicleStateContinuous = true;
    private static int vehicleId = -1;
    private static int vehiclePassengerId = -1;
    private static ServerPlayer reconnectBaselineB;
    private static boolean sawReconnectDisconnect;
    private static boolean baselineScenarioPassed;
    private static RingMultiplayerReadinessGate readinessGate;
    private static boolean readinessPassed;

    private RingWorldMultiplayerTest() { }

    public static void recordClientResult(String role, String phase, boolean passed) {
        if (!Boolean.getBoolean("ringworld.multiplayerTest")) return;
        CLIENT_RESULTS.put(role + ':' + phase, passed);
        if (role.equals("A") && phase.equals("movement_started") && passed) {
            maximumAPacketStep = 0.0;
        }
    }

    public static void recordPlayerMovementPacket(ServerPlayer player, double projectedX,
                                                  RingGeometry geometry) {
        if (!Boolean.getBoolean("ringworld.multiplayerTest") || stage != 1
                || !Boolean.TRUE.equals(CLIENT_RESULTS.get("A:movement_started"))
                || !player.getName().getString().equals("RingTesterA")) return;
        maximumAPacketStep = Math.max(maximumAPacketStep,
                Math.abs(geometry.shortestCircumferenceDelta(player.getX(), projectedX)));
    }

    static void prepareWaitingPlayer(ServerPlayer player) {
        if (!Boolean.getBoolean("ringworld.multiplayerTest")) return;
        prepareCreativePlayer(player);
    }

    static void tick(ServerLevel world, RingGeometry geometry) {
        if (!Boolean.getBoolean("ringworld.multiplayerTest")) return;
        ServerPlayer playerA = playerNamed(world, "RingTesterA");
        ServerPlayer playerB = playerNamed(world, "RingTesterB");

        if (!initialized) {
            // Persisted test worlds can leave yesterday's arm marker behind.
            // Build the seam fixture before clients connect. On a new world,
            // synchronously generating both sides of the seam can take long
            // enough to trip the login timeout if this is deferred until the
            // players have joined.
            prepareSeamChunks(world, geometry);
            prepareSeamLane(world, geometry, 120);
            clearStaleTestVehicles(world);
            world.setRespawnData(LevelData.RespawnData.of(
                    world.dimension(), new BlockPos(0, 120, 0), 0.0f, 0.0f));
            world.setBlock(seamArmMarker(), Blocks.RED_CONCRETE.defaultBlockState(), 3);
            world.setBlock(combatResultMarker(), Blocks.AIR.defaultBlockState(), 3);
            initialized = true;
            RingWorldMod.LOGGER.info("[multiplayer] seam test region ready; waiting for clients");
        }

        if (stage == 7) {
            tickReconnect(playerA, playerB);
            return;
        }
        if (stage == 8) {
            if (RingWorldExtendedMultiplayerTest.tick(world, geometry, playerA, playerB,
                    baselineScenarioPassed)) {
                stage = 9;
            }
            return;
        }
        if (playerA == null || playerB == null) {
            if (!loggedWaiting) {
                RingWorldMod.LOGGER.info("[multiplayer] waiting for RingTesterA and RingTesterB");
                loggedWaiting = true;
            }
            return;
        }

        ticks++;
        if (stage == 0 && !readinessPassed) {
            if (!clientPassed("A", "client_ready") || !clientPassed("B", "client_ready")) {
                ticks = 0;
                return;
            }
            if (readinessGate == null) readinessGate = new RingMultiplayerReadinessGate();
            RingMultiplayerReadinessGate.Result readiness = readinessGate.observe(System.nanoTime());
            if (readiness == RingMultiplayerReadinessGate.Result.TIMED_OUT) {
                RingWorldMod.LOGGER.error(
                        "[multiplayer] readiness gate timed out before seam scenario (observedTicks={}, consecutiveOnTimeTicks={}, longestTickIntervalMs={}); stopping disposable harness",
                        readinessGate.observedTicks(), readinessGate.consecutiveOnTimeTicks(),
                        readinessGate.longestTickIntervalNanos() / 1_000_000.0);
                world.getServer().halt(false);
                return;
            }
            if (readiness != RingMultiplayerReadinessGate.Result.READY) return;
            readinessPassed = true;
            // Preserve the original harness pacing after the infrastructure
            // barrier: send Creative first, then allow both real clients time
            // to observe it before the Survival pose and seam arm arrive.
            ticks = 0;
        }
        if (stage == 0 && ticks == 1) {
            // Reused-world boats can finish loading only when the automated
            // clients begin watching the seam chunks. Clear them again here,
            // before either client is allowed to acquire the new fixture.
            clearStaleTestVehicles(world);
            prepareCreativePlayer(playerA);
            prepareCreativePlayer(playerB);
        }
        if (stage == 0 && ticks >= 100) {
            world.setBlock(combatResultMarker(), Blocks.AIR.defaultBlockState(), 3);
            preparePlayer(playerA);
            preparePlayer(playerB);
            playerA.teleportTo(world, geometry.circumferenceBlocks() - 4.0, 120.0, 0.5,
                    Set.<Relative>of(), 90.0f, 10.0f, false);
            playerB.teleportTo(world, 2.0, 120.0, 0.5,
                    Set.<Relative>of(), -90.0f, 10.0f, false);
            world.setBlock(seamArmMarker(), Blocks.BLUE_CONCRETE.defaultBlockState(), 3);
            previousAX = playerA.getX();
            maximumAStep = 0.0;
            maximumAPacketStep = 0.0;
            stage = 1;
            ticks = 0;
            RingWorldMod.LOGGER.info("[multiplayer] seam scenario armed: A x={}, B x={}, periodicDistance={}",
                    playerA.getX(), playerB.getX(), playerA.distanceTo(playerB));
            return;
        }

        if (stage == 1) {
            double canonicalStep = geometry.shortestCircumferenceDelta(previousAX, playerA.getX());
            maximumAStep = Math.max(maximumAStep, Math.abs(canonicalStep));
            if (previousAX - playerA.getX() > geometry.circumferenceBlocks() / 2.0
                    && canonicalStep > 0.0 && canonicalStep < 8.0) {
                sawCanonicalPlayerWrap = true;
            }
            previousAX = playerA.getX();
            boolean reachedPostSeamTarget = sawCanonicalPlayerWrap
                    && playerA.getX() >= 0.0 && playerA.getX() < 0.5;
            if (reachedPostSeamTarget || ticks >= 2_400) {
                List<Entity> visibleToB = world.getEntities(playerB,
                        playerB.getBoundingBox().inflate(12.0), entity -> entity == playerA);
                boolean crossed = sawCanonicalPlayerWrap;
                boolean queryPassed = visibleToB.size() == 1;
                boolean distancePassed = playerA.distanceTo(playerB) < 4.0f;
                boolean smoothServerMotion = maximumAPacketStep <= 0.251;
                boolean canonical = playerA.getX() >= 0.0
                        && playerA.getX() < geometry.circumferenceBlocks()
                        && playerB.getX() >= 0.0
                        && playerB.getX() < geometry.circumferenceBlocks();
                boolean passed = crossed && queryPassed && distancePassed && smoothServerMotion && canonical;
                serverSeamPassed = passed;
                RingWorldMod.LOGGER.info(
                        "[multiplayer] server seam result={} (canonicalWrap={}, query={}, distance={}, smooth={}, canonical={}, maxPacketStep={}, maxTickSample={}, A={}, B={})",
                        passed, crossed, queryPassed, distancePassed, smoothServerMotion, canonical,
                        maximumAPacketStep, maximumAStep, playerA.getX(), playerB.getX());
                stage = 2;
                ticks = 0;
            }
            return;
        }

        if (stage == 2) {
            if (playerB.getHealth() < playerB.getMaxHealth()) {
                combatPassed = true;
                world.setBlock(combatResultMarker(), Blocks.LIME_CONCRETE.defaultBlockState(), 3);
                RingWorldMod.LOGGER.info(
                        "[multiplayer] cross-seam melee result=true (A={}, B={}, B health={})",
                        playerA.getX(), playerB.getX(), playerB.getHealth());
                prepareCreativePlayer(playerA);
                prepareCreativePlayer(playerB);
                stage = 3;
                ticks = 0;
            } else if (ticks >= 1_200) {
                world.setBlock(combatResultMarker(), Blocks.RED_CONCRETE.defaultBlockState(), 3);
                RingWorldMod.LOGGER.error(
                        "[multiplayer] cross-seam melee result=false (A={}, B={}, periodicDistance={})",
                        playerA.getX(), playerB.getX(), playerA.distanceTo(playerB));
                prepareCreativePlayer(playerA);
                prepareCreativePlayer(playerB);
                stage = 3;
                ticks = 0;
            }
            return;
        }

        if (stage == 3) {
            BlockPos target = new BlockPos(1, 119, 0);
            // Keep the damaged health visible long enough for B's real client
            // to observe the result independently of the server assertion.
            if (ticks == 20) playerB.setHealth(playerB.getMaxHealth());
            boolean clientsReady = clientPassed("A", "interaction_chunk_ready")
                    && clientPassed("B", "interaction_chunk_ready");
            if (!interactionArmed && clientsReady) {
                world.setBlock(target, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                interactionArmed = true;
                RingWorldMod.LOGGER.info("[multiplayer] cross-seam interaction armed at canonical {}", target);
            }
            if (interactionArmed && ticks % 40 == 0 && world.getBlockState(target).is(Blocks.GOLD_BLOCK)) {
                // A cold client can re-key its seam chart just after the first
                // packet. Repeat the authoritative state until A performs the
                // real interaction; this still requires both delivery and a
                // genuine client break packet, while removing one-frame
                // startup timing from the acceptance gate.
                var state = world.getBlockState(target);
                world.sendBlockUpdated(target, state, state, 3);
            }
            if (interactionArmed && world.getBlockState(target).isAir()) {
                interactionPassed = true;
                Boat boat = EntityType.OAK_BOAT.create(world, EntitySpawnReason.COMMAND);
                if (boat != null) {
                    boat.setPos(geometry.circumferenceBlocks() - 2.0, 120.0, 3.5);
                    boat.setYRot(37.0f);
                    boat.setXRot(0.0f);
                    boat.setNoGravity(true);
                    // Hold the fixture until both clients have acquired it.
                    // This removes network-startup timing from the seam test.
                    boat.setDeltaMovement(Vec3.ZERO);
                    world.addFreshEntity(boat);
                    vehicleId = boat.getId();
                    Entity passenger = EntityType.ARMOR_STAND.create(world, EntitySpawnReason.COMMAND);
                    if (passenger != null) {
                        passenger.setPos(boat.getX(), boat.getY(), boat.getZ());
                        passenger.setYRot(boat.getYRot());
                        passenger.setNoGravity(true);
                        world.addFreshEntity(passenger);
                        if (passenger.startRiding(boat)) {
                            vehiclePassengerId = passenger.getId();
                        } else {
                            passenger.discard();
                            vehicleStateContinuous = false;
                        }
                    } else {
                        vehicleStateContinuous = false;
                    }
                    sawVehicleHighSide = boat.getX() > geometry.circumferenceBlocks() / 2.0;
                }
                RingWorldMod.LOGGER.info(
                        "[multiplayer] cross-seam interaction result=true; vehicleId={} passengerId={}",
                        vehicleId, vehiclePassengerId);
                stage = 4;
                ticks = 0;
            } else if (ticks >= 600) {
                RingWorldMod.LOGGER.error(
                        "[multiplayer] cross-seam interaction result=false (timeout, armed={}, clientAReady={}, clientBReady={})",
                        interactionArmed, clientPassed("A", "interaction_chunk_ready"),
                        clientPassed("B", "interaction_chunk_ready"));
                stage = 4;
                ticks = 0;
            }
            return;
        }

        if (stage == 4) {
            Entity vehicle = world.getEntity(vehicleId);
            Entity passenger = world.getEntity(vehiclePassengerId);
            vehicleStateContinuous &= vehicle != null && passenger != null
                    && passenger.getVehicle() == vehicle
                    && vehicle.getPassengers().contains(passenger)
                    && vehicle.getDeltaMovement().equals(Vec3.ZERO)
                    && vehicle.getYRot() == 37.0f
                    && vehicle.getXRot() == 0.0f;
            boolean clientsAcquired = clientPassed("A", "vehicle_acquired")
                    && clientPassed("B", "vehicle_acquired");
            if (vehicle != null && clientsAcquired && !sawCanonicalVehicleWrap) {
                // Drive a deterministic server-owned pose. Riderless boat
                // drag is deliberately excluded; this phase tests vehicle
                // section reindexing and network interpolation at the seam.
                double sourceX = geometry.wrapX(vehicle.getX());
                if (sourceX > geometry.circumferenceBlocks() / 2.0) {
                    sawVehicleHighSide = true;
                }
                double nextX = geometry.wrapX(sourceX + 0.18);
                vehicle.setPos(nextX, vehicle.getY(), vehicle.getZ());
                vehicle.setDeltaMovement(Vec3.ZERO);
                if (sawVehicleHighSide && nextX < 3.0) {
                    sawCanonicalVehicleWrap = true;
                }
            }
            if (vehicle != null && sawCanonicalVehicleWrap && vehicle.getX() < 3.0) {
                boolean canonicalOwnership = passenger != null
                        && vehicle.getX() >= 0.0
                        && vehicle.getX() < geometry.circumferenceBlocks()
                        && passenger.getX() >= 0.0
                        && passenger.getX() < geometry.circumferenceBlocks();
                vehiclePassed = vehicleStateContinuous && canonicalOwnership;
                RingWorldMod.LOGGER.info(
                        "[multiplayer] vehicle canonical crossing result={} x={} passengerX={} "
                                + "mountContinuous={} canonicalOwnership={} yaw={} pitch={} velocity={}",
                        vehiclePassed, vehicle.getX(),
                        passenger == null ? Double.NaN : passenger.getX(),
                        vehicleStateContinuous, canonicalOwnership,
                        vehicle.getYRot(), vehicle.getXRot(), vehicle.getDeltaMovement());
                stage = 5;
                ticks = 0;
            } else if (ticks >= 600) {
                RingWorldMod.LOGGER.error(
                        "[multiplayer] vehicle canonical crossing result=false x={} acquiredA={} acquiredB={}",
                        vehicle == null ? Double.NaN : vehicle.getX(),
                        clientPassed("A", "vehicle_acquired"),
                        clientPassed("B", "vehicle_acquired"));
                stage = 5;
                ticks = 0;
            }
            return;
        }

        if (stage == 5 && ((ticks >= 20
                && clientPassed("A", "vehicle_visibility")
                && clientPassed("B", "vehicle_visibility")) || ticks >= 1_200)) {
            playerA.teleportTo(world, 64.5, 120.0, 0.5,
                    Set.<Relative>of(), playerA.getYRot(), playerA.getXRot(), false);
            RingWorldMod.LOGGER.info("[multiplayer] intentional long teleport sent to A x={}", playerA.getX());
            stage = 6;
            ticks = 0;
            return;
        }

        if (stage == 6 && ((ticks >= 20
                && clientPassed("A", "intentional_teleport")) || ticks >= 1_200)) {
            boolean farTeleportPassed = Math.abs(playerA.getX() - 64.5) < 0.75;
            playerA.teleportTo(world, geometry.circumferenceBlocks() - 4.0, 120.0, 0.5,
                    Set.<Relative>of(), 90.0f, 10.0f, false);
            playerB.teleportTo(world, 2.0, 120.0, 0.5,
                    Set.<Relative>of(), -90.0f, 10.0f, false);
            reconnectBaselineB = playerB;
            RingWorldMod.LOGGER.info("[multiplayer] intentional long teleport server result={}; returned players to seam",
                    farTeleportPassed);
            stage = 7;
            ticks = 0;
        }
    }

    private static void tickReconnect(ServerPlayer playerA, ServerPlayer playerB) {
        ticks++;
        if (playerB == null) sawReconnectDisconnect = true;
        boolean newConnection = sawReconnectDisconnect && playerB != null && playerB != reconnectBaselineB;
        if (newConnection && clientPassed("B", "reconnect")) {
            boolean clientMatrix = clientPassed("A", "seam_visibility")
                    && clientPassed("B", "seam_visibility")
                    && clientPassed("A", "melee_combat")
                    && clientPassed("B", "melee_combat")
                    && clientPassed("A", "block_interaction")
                    && clientPassed("B", "block_interaction")
                    && clientPassed("A", "vehicle_visibility")
                    && clientPassed("B", "vehicle_visibility")
                    && clientPassed("A", "intentional_teleport")
                    && clientPassed("A", "teleport_return")
                    && clientPassed("B", "reconnect");
            boolean passed = playerA != null && serverSeamPassed && combatPassed && interactionPassed
                    && vehiclePassed && clientMatrix;
            baselineScenarioPassed = passed;
            RingWorldMod.LOGGER.info(
                    "[multiplayer] baseline scenario result={} (serverSeam={}, combat={}, interaction={}, vehicle={}, reconnect={}, clientMatrix={})",
                    passed, serverSeamPassed, combatPassed, interactionPassed, vehiclePassed,
                    newConnection, clientMatrix);
            stage = 8;
            ticks = 0;
        } else if (ticks >= 2_400) {
            RingWorldMod.LOGGER.error(
                    "[multiplayer] baseline scenario result=false (disconnectSeen={}, newConnection={}, results={})",
                    sawReconnectDisconnect, newConnection, CLIENT_RESULTS);
            baselineScenarioPassed = false;
            stage = 8;
            ticks = 0;
        }
    }

    static boolean clientPassed(String role, String phase) {
        return Boolean.TRUE.equals(CLIENT_RESULTS.get(role + ':' + phase));
    }

    private static ServerPlayer playerNamed(ServerLevel world, String name) {
        for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
            if (player.level() == world && player.getName().getString().equals(name)) return player;
        }
        return null;
    }

    private static void preparePlayer(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void prepareCreativePlayer(ServerPlayer player) {
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void clearStaleTestVehicles(ServerLevel world) {
        List<Entity> staleTestVehicles = new ArrayList<>();
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof Boat || entity.getVehicle() instanceof Boat) {
                staleTestVehicles.add(entity);
            }
        }
        staleTestVehicles.forEach(Entity::discard);
    }

    private static void prepareSeamLane(ServerLevel world, RingGeometry geometry, int y) {
        int circumference = geometry.circumferenceBlocks();
        for (int offset = -17; offset <= 17; offset++) {
            int x = geometry.wrapBlockX(circumference + offset);
            for (int z = -6; z <= 6; z++) {
                for (int laneY = y - 1; laneY <= y + 5; laneY++) {
                    boolean shell = Math.abs(offset) == 17 || Math.abs(z) == 6
                            || laneY == y - 1 || laneY == y + 5;
                    world.setBlock(new BlockPos(x, laneY, z),
                            shell ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void prepareSeamChunks(ServerLevel world, RingGeometry geometry) {
        int circumferenceChunks = geometry.circumferenceChunks();
        // Covers the test clients' two-chunk view distance on both canonical
        // sides of the seam, with one extra column for tracking transitions.
        for (int xOffset = -3; xOffset <= 3; xOffset++) {
            int chunkX = Math.floorMod(xOffset, circumferenceChunks);
            for (int chunkZ = -3; chunkZ <= 3; chunkZ++) {
                world.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static BlockPos combatResultMarker() {
        return new BlockPos(0, 123, 4);
    }

    private static BlockPos seamArmMarker() {
        return new BlockPos(0, 123, -4);
    }
}
