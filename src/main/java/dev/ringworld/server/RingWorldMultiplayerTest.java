package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.WorldProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static boolean vehiclePassed;
    private static boolean sawVehicleHighSide;
    private static boolean sawCanonicalVehicleWrap;
    private static int vehicleId = -1;
    private static ServerPlayerEntity reconnectBaselineB;
    private static boolean sawReconnectDisconnect;

    private RingWorldMultiplayerTest() { }

    public static void recordClientResult(String role, String phase, boolean passed) {
        if (!Boolean.getBoolean("ringworld.multiplayerTest")) return;
        CLIENT_RESULTS.put(role + ':' + phase, passed);
        if (role.equals("A") && phase.equals("movement_started") && passed) {
            maximumAPacketStep = 0.0;
        }
    }

    public static void recordPlayerMovementPacket(ServerPlayerEntity player, double projectedX,
                                                  RingGeometry geometry) {
        if (!Boolean.getBoolean("ringworld.multiplayerTest") || stage != 1
                || !Boolean.TRUE.equals(CLIENT_RESULTS.get("A:movement_started"))
                || !player.getName().getString().equals("RingTesterA")) return;
        maximumAPacketStep = Math.max(maximumAPacketStep,
                Math.abs(geometry.shortestCircumferenceDelta(player.getX(), projectedX)));
    }

    static void prepareWaitingPlayer(ServerPlayerEntity player) {
        if (!Boolean.getBoolean("ringworld.multiplayerTest")) return;
        prepareCreativePlayer(player);
    }

    static void tick(ServerWorld world, RingGeometry geometry) {
        if (!Boolean.getBoolean("ringworld.multiplayerTest")) return;
        ServerPlayerEntity playerA = playerNamed(world, "RingTesterA");
        ServerPlayerEntity playerB = playerNamed(world, "RingTesterB");

        if (!initialized) {
            // Persisted test worlds can leave yesterday's arm marker behind.
            // Build the seam fixture before clients connect. On a new world,
            // synchronously generating both sides of the seam can take long
            // enough to trip the login timeout if this is deferred until the
            // players have joined.
            prepareSeamChunks(world, geometry);
            prepareSeamLane(world, geometry, 120);
            List<Entity> staleTestBoats = new ArrayList<>();
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof BoatEntity) staleTestBoats.add(entity);
            }
            staleTestBoats.forEach(Entity::discard);
            world.setSpawnPoint(WorldProperties.SpawnPoint.create(
                    world.getRegistryKey(), new BlockPos(0, 120, 0), 0.0f, 0.0f));
            world.setBlockState(seamArmMarker(), Blocks.RED_CONCRETE.getDefaultState(), 3);
            world.setBlockState(combatResultMarker(), Blocks.AIR.getDefaultState(), 3);
            initialized = true;
            RingWorldMod.LOGGER.info("[multiplayer] seam test region ready; waiting for clients");
        }

        if (stage == 7) {
            tickReconnect(playerA, playerB);
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
        if (stage == 0 && ticks == 1) {
            prepareCreativePlayer(playerA);
            prepareCreativePlayer(playerB);
        }
        if (stage == 0 && ticks >= 100) {
            world.setBlockState(combatResultMarker(), Blocks.AIR.getDefaultState(), 3);
            preparePlayer(playerA);
            preparePlayer(playerB);
            playerA.teleport(world, geometry.circumferenceBlocks() - 4.0, 120.0, 0.5,
                    Set.<PositionFlag>of(), 90.0f, 10.0f, false);
            playerB.teleport(world, 2.0, 120.0, 0.5,
                    Set.<PositionFlag>of(), -90.0f, 10.0f, false);
            world.setBlockState(seamArmMarker(), Blocks.BLUE_CONCRETE.getDefaultState(), 3);
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
                List<Entity> visibleToB = world.getOtherEntities(playerB,
                        playerB.getBoundingBox().expand(12.0), entity -> entity == playerA);
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
                world.setBlockState(combatResultMarker(), Blocks.LIME_CONCRETE.getDefaultState(), 3);
                RingWorldMod.LOGGER.info(
                        "[multiplayer] cross-seam melee result=true (A={}, B={}, B health={})",
                        playerA.getX(), playerB.getX(), playerB.getHealth());
                prepareCreativePlayer(playerA);
                prepareCreativePlayer(playerB);
                stage = 3;
                ticks = 0;
            } else if (ticks >= 1_200) {
                world.setBlockState(combatResultMarker(), Blocks.RED_CONCRETE.getDefaultState(), 3);
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
            if (ticks == 40) {
                world.setBlockState(target, Blocks.GOLD_BLOCK.getDefaultState(), 3);
                RingWorldMod.LOGGER.info("[multiplayer] cross-seam interaction armed at canonical {}", target);
            }
            if (ticks > 40 && world.getBlockState(target).isAir()) {
                interactionPassed = true;
                BoatEntity boat = EntityType.OAK_BOAT.create(world, SpawnReason.COMMAND);
                if (boat != null) {
                    boat.setPosition(geometry.circumferenceBlocks() - 2.0, 120.0, 3.5);
                    boat.setNoGravity(true);
                    // Hold the fixture until both clients have acquired it.
                    // This removes network-startup timing from the seam test.
                    boat.setVelocity(Vec3d.ZERO);
                    world.spawnEntity(boat);
                    vehicleId = boat.getId();
                    sawVehicleHighSide = boat.getX() > geometry.circumferenceBlocks() / 2.0;
                }
                RingWorldMod.LOGGER.info("[multiplayer] cross-seam interaction result=true; vehicleId={}", vehicleId);
                stage = 4;
                ticks = 0;
            } else if (ticks >= 600) {
                RingWorldMod.LOGGER.error("[multiplayer] cross-seam interaction result=false (timeout)");
                stage = 4;
                ticks = 0;
            }
            return;
        }

        if (stage == 4) {
            Entity vehicle = world.getEntityById(vehicleId);
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
                vehicle.setPosition(nextX, vehicle.getY(), vehicle.getZ());
                vehicle.setVelocity(Vec3d.ZERO);
                if (sawVehicleHighSide && nextX < 3.0) {
                    sawCanonicalVehicleWrap = true;
                }
            }
            if (vehicle != null && sawCanonicalVehicleWrap && vehicle.getX() < 3.0) {
                vehiclePassed = true;
                RingWorldMod.LOGGER.info("[multiplayer] vehicle canonical crossing result=true x={}", vehicle.getX());
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
            playerA.teleport(world, 64.5, 120.0, 0.5,
                    Set.<PositionFlag>of(), playerA.getYaw(), playerA.getPitch(), false);
            RingWorldMod.LOGGER.info("[multiplayer] intentional long teleport sent to A x={}", playerA.getX());
            stage = 6;
            ticks = 0;
            return;
        }

        if (stage == 6 && ((ticks >= 20
                && clientPassed("A", "intentional_teleport")) || ticks >= 1_200)) {
            boolean farTeleportPassed = Math.abs(playerA.getX() - 64.5) < 0.75;
            playerA.teleport(world, geometry.circumferenceBlocks() - 4.0, 120.0, 0.5,
                    Set.<PositionFlag>of(), 90.0f, 10.0f, false);
            playerB.teleport(world, 2.0, 120.0, 0.5,
                    Set.<PositionFlag>of(), -90.0f, 10.0f, false);
            reconnectBaselineB = playerB;
            RingWorldMod.LOGGER.info("[multiplayer] intentional long teleport server result={}; returned players to seam",
                    farTeleportPassed);
            stage = 7;
            ticks = 0;
        }
    }

    private static void tickReconnect(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
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
            RingWorldMod.LOGGER.info(
                    "[multiplayer] full scenario result={} (serverSeam={}, combat={}, interaction={}, vehicle={}, reconnect={}, clientMatrix={})",
                    passed, serverSeamPassed, combatPassed, interactionPassed, vehiclePassed,
                    newConnection, clientMatrix);
            stage = 8;
        } else if (ticks >= 2_400) {
            RingWorldMod.LOGGER.error(
                    "[multiplayer] full scenario result=false (disconnectSeen={}, newConnection={}, results={})",
                    sawReconnectDisconnect, newConnection, CLIENT_RESULTS);
            stage = 8;
        }
    }

    private static boolean clientPassed(String role, String phase) {
        return Boolean.TRUE.equals(CLIENT_RESULTS.get(role + ':' + phase));
    }

    private static ServerPlayerEntity playerNamed(ServerWorld world, String name) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getName().getString().equals(name)) return player;
        }
        return null;
    }

    private static void preparePlayer(ServerPlayerEntity player) {
        player.changeGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.getAbilities().flying = false;
        player.sendAbilitiesUpdate();
        player.setVelocity(Vec3d.ZERO);
    }

    private static void prepareCreativePlayer(ServerPlayerEntity player) {
        player.changeGameMode(GameMode.CREATIVE);
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
        player.setVelocity(Vec3d.ZERO);
    }

    private static void prepareSeamLane(ServerWorld world, RingGeometry geometry, int y) {
        int circumference = geometry.circumferenceBlocks();
        for (int offset = -16; offset <= 16; offset++) {
            int x = geometry.wrapBlockX(circumference + offset);
            for (int z = -5; z <= 5; z++) {
                world.setBlockState(new BlockPos(x, y - 1, z), Blocks.GLASS.getDefaultState(), 2);
                for (int clearY = y; clearY <= y + 4; clearY++) {
                    world.setBlockState(new BlockPos(x, clearY, z), Blocks.AIR.getDefaultState(), 2);
                }
            }
        }
    }

    private static void prepareSeamChunks(ServerWorld world, RingGeometry geometry) {
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
