package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.net.RingMultiplayerTestPayload;
import dev.ringworld.world.RingGeometry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.AllowedAddressResolver;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.InactivityFpsLimit;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;

/** A real-network, two-process client driver. It is dormant outside its JVM test flag. */
final class MultiplayerTestClient {
    private final String role = System.getProperty("ringworld.multiplayerTestRole", "").trim();
    private boolean optionsApplied;
    private boolean connectionRequested;
    private boolean reconnectPending;
    private int menuTicks;
    private int stalledConnectionTicks;
    private int stage;
    private int stageTicks;

    private int positionedTicks;
    private boolean seamArmed;
    private double previousRemoteX = Double.NaN;
    private double maximumRemoteStep;
    private int remoteMissingTicks;
    private boolean remoteCrossedZero;

    private boolean blockSeen;
    private boolean interactionSent;
    private int interactionAge;
    private int attacksSent;

    private boolean vehicleSeen;
    private double previousVehicleX = Double.NaN;
    private double maximumVehicleStep;
    private int vehicleMissingTicks;

    private boolean sawFarTeleport;
    private boolean sawRemoteFarTeleport;
    private boolean reconnectResultSent;

    boolean tick(MinecraftClient client) {
        if (role.isEmpty()) return false;
        if (!optionsApplied) {
            client.options.getViewDistance().setValue(2);
            client.options.getSimulationDistance().setValue(2);
            client.options.getEnableVsync().setValue(false);
            client.options.getInactivityFpsLimit().setValue(InactivityFpsLimit.MINIMIZED);
            client.options.pauseOnLostFocus = false;
            client.options.onboardAccessibility = false;
            optionsApplied = true;
        }
        if (client.world == null || client.player == null) {
            connectWhenReady(client);
            return true;
        }
        if (client.currentScreen instanceof GameMenuScreen) client.setScreen(null);
        stageTicks++;
        switch (stage) {
            case 0 -> runSeamScenario(client);
            case 1 -> runCombatScenario(client);
            case 2 -> runInteractionScenario(client);
            case 3 -> runVehicleScenario(client);
            case 4 -> runTeleportScenario(client);
            case 5 -> runReconnectResult(client);
            default -> { }
        }
        return true;
    }

    private void connectWhenReady(MinecraftClient client) {
        if (reconnectPending && client.currentScreen != null) {
            connectionRequested = false;
            reconnectPending = false;
            menuTicks = 0;
            stalledConnectionTicks = 0;
            RingWorldMod.LOGGER.info("[multiplayer:{}] disconnected as planned; starting reconnect", role);
        }
        if (connectionRequested) {
            if (++stalledConnectionTicks % 200 == 0) {
                RingWorldMod.LOGGER.info("[multiplayer:{}] connection state screen={} networkHandler={}",
                        role, client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName()
                                + " title=" + client.currentScreen.getNarratedTitle().getString(),
                        client.getNetworkHandler() != null);
            }
            return;
        }
        Screen parent = client.currentScreen;
        if (parent == null) return;
        if (++menuTicks < 40) return;
        int port = Integer.getInteger("ringworld.multiplayerTestPort", 25566);
        String addressText = "127.0.0.1:" + port;
        ServerInfo server = new ServerInfo("RingWorld automated multiplayer", addressText,
                ServerInfo.ServerType.OTHER);
        connectionRequested = true;
        ServerAddress address = ServerAddress.parse(addressText);
        RingWorldMod.LOGGER.info("[multiplayer:{}] connecting to {} from {}; resolved={}", role, addressText,
                parent.getClass().getSimpleName(), AllowedAddressResolver.DEFAULT.resolve(address));
        // A non-null cookie store marks this as a 1.21 server-transfer login.
        ConnectScreen.connect(parent, client, address, server, false, null);
    }

    private void runSeamScenario(MinecraftClient client) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || client.interactionManager == null
                || client.interactionManager.getCurrentGameMode() != GameMode.SURVIVAL) return;
        boolean atTestPose = role.equals("A")
                ? Math.abs(geometry.shortestCircumferenceDelta(
                        client.player.getX(), geometry.circumferenceBlocks() - 4.0)) < 0.75
                : Math.abs(geometry.shortestCircumferenceDelta(client.player.getX(), 2.0)) < 0.75;
        if (!seamArmed && !atTestPose) return;

        AbstractClientPlayerEntity remote = findRemotePlayer(client);
        if (remote == null) {
            if (seamArmed) remoteMissingTicks++;
            return;
        }
        if (!seamArmed) {
            double expectedRemoteX = geometry.nearestImageX(
                    role.equals("A") ? 2.0 : geometry.circumferenceBlocks() - 4.0,
                    client.player.getX());
            if (Math.abs(remote.getX() - expectedRemoteX) >= 0.75) return;
        }
        positionedTicks++;
        if (!seamArmed && positionedTicks >= 80) {
            seamArmed = true;
            previousRemoteX = remote.getX();
            sendResult("movement_started", true, client.player.getX());
            RingWorldMod.LOGGER.info(
                    "[multiplayer:{}] armed localX={} remote={} remoteLogicalX={} shortestDistance={}",
                    role, client.player.getX(), remote.getName().getString(), remote.getX(),
                    Math.abs(geometry.shortestCircumferenceDelta(client.player.getX(), remote.getX())));
        }
        if (!seamArmed) return;

        double remoteStep = Math.abs(remote.getX() - previousRemoteX);
        maximumRemoteStep = Math.max(maximumRemoteStep, remoteStep);
        if (previousRemoteX < 0.0 && remote.getX() >= 0.0) remoteCrossedZero = true;
        previousRemoteX = remote.getX();

        if (role.equals("A") && client.player.getX() < geometry.circumferenceBlocks()) {
            double nextX = Math.min(geometry.circumferenceBlocks(),
                    client.player.getX() + 0.25);
            client.player.setPosition(nextX, client.player.getY(), client.player.getZ());
            client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                    nextX, client.player.getY(), client.player.getZ(),
                    client.player.getYaw(), client.player.getPitch(),
                    client.player.isOnGround(), client.player.horizontalCollision));
            return;
        }

        boolean localCrossed = role.equals("B")
                ? remoteCrossedZero
                : client.player.getX() >= geometry.circumferenceBlocks();
        if (!localCrossed) return;

        boolean remoteStillAdjacent = Math.abs(geometry.shortestCircumferenceDelta(
                client.player.getX(), remote.getX())) < 12.0;
        boolean passed = remoteMissingTicks == 0 && maximumRemoteStep <= 1.25 && remoteStillAdjacent;
        RingWorldMod.LOGGER.info(
                "[multiplayer:{}] client seam result={} localX={} remoteX={} maxRemoteStep={} missingTicks={}",
                role, passed, client.player.getX(), remote.getX(), maximumRemoteStep, remoteMissingTicks);
        sendResult("seam_visibility", passed, maximumRemoteStep);
        ScreenshotRecorder.saveScreenshot(client.runDirectory,
                "ringworld-multiplayer-" + role.toLowerCase() + ".png",
                client.getFramebuffer(), 1,
                message -> RingWorldMod.LOGGER.info(
                        "[multiplayer:{}] screenshot: {}", role, message.getString()));
        stage = 1;
        stageTicks = 0;
    }

    private void runCombatScenario(MinecraftClient client) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || client.interactionManager == null) return;

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
        if (client.world.getBlockState(marker).isOf(Blocks.LIME_CONCRETE)) {
            RingWorldMod.LOGGER.info("[multiplayer:{}] cross-seam melee result=true attacksSent={}",
                    role, attacksSent);
            sendResult("melee_combat", true, attacksSent);
            stage = 2;
            stageTicks = 0;
            return;
        }
        if (client.world.getBlockState(marker).isOf(Blocks.RED_CONCRETE) || stageTicks >= 1_300) {
            RingWorldMod.LOGGER.error("[multiplayer:{}] cross-seam melee result=false attacksSent={}",
                    role, attacksSent);
            sendResult("melee_combat", false, attacksSent);
            stage = 2;
            stageTicks = 0;
            return;
        }

        AbstractClientPlayerEntity remote = findRemotePlayer(client);
        if (role.equals("A") && remote != null && stageTicks >= 20 && stageTicks % 20 == 0) {
            client.interactionManager.attackEntity(client.player, remote);
            client.player.swingHand(Hand.MAIN_HAND);
            attacksSent++;
            RingWorldMod.LOGGER.info(
                    "[multiplayer:A] sent cross-seam melee attack localX={} remoteX={} periodicDistance={}",
                    client.player.getX(), remote.getX(),
                    Math.abs(geometry.shortestCircumferenceDelta(client.player.getX(), remote.getX())));
        }
    }

    private void runInteractionScenario(MinecraftClient client) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || client.interactionManager == null) return;
        int logicalX = (int)Math.floor(geometry.nearestImageX(1.0, client.player.getX()));
        BlockPos target = new BlockPos(logicalX, 119, 0);
        boolean targetPresent = client.world.getBlockState(target).isOf(Blocks.GOLD_BLOCK);
        blockSeen |= targetPresent;
        if (stageTicks % 100 == 0) {
            RingWorldMod.LOGGER.info(
                    "[multiplayer:{}] seam block probe target={} state={} seen={} playerX={}",
                    role, target, client.world.getBlockState(target).getBlock(), blockSeen,
                    client.player.getX());
        }

        // Give the observer client time to receive and render the armed gold
        // block before A removes it; otherwise both updates can coalesce.
        if (role.equals("A") && blockSeen && !interactionSent && stageTicks >= 100) {
            interactionSent = client.interactionManager.attackBlock(target, Direction.UP);
            if (interactionSent) {
                interactionAge = 0;
                RingWorldMod.LOGGER.info("[multiplayer:A] attacked logical seam block {}", target);
            }
        }
        if (interactionSent) interactionAge++;

        if (blockSeen && !targetPresent && (!role.equals("A") || interactionAge >= 5)) {
            RingWorldMod.LOGGER.info("[multiplayer:{}] cross-seam block update result=true at {}", role, target);
            sendResult("block_interaction", true, stageTicks);
            stage = 3;
            stageTicks = 0;
        } else if (stageTicks >= 600) {
            RingWorldMod.LOGGER.error("[multiplayer:{}] cross-seam block update result=false", role);
            sendResult("block_interaction", false, stageTicks);
            stage = 3;
            stageTicks = 0;
        }
    }

    private void runVehicleScenario(MinecraftClient client) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;
        BoatEntity boat = findBoat(client);
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
            vehicleSeen = true;
            previousVehicleX = boat.getX();
            RingWorldMod.LOGGER.info("[multiplayer:{}] acquired seam vehicle at x={}", role, boat.getX());
        } else {
            maximumVehicleStep = Math.max(maximumVehicleStep, Math.abs(boat.getX() - previousVehicleX));
            previousVehicleX = boat.getX();
        }
        boolean crossed = role.equals("A")
                ? boat.getX() >= geometry.circumferenceBlocks()
                : boat.getX() >= 0.0;
        if (crossed) {
            boolean passed = vehicleMissingTicks == 0 && maximumVehicleStep <= 1.0;
            RingWorldMod.LOGGER.info(
                    "[multiplayer:{}] vehicle visibility result={} x={} maxStep={} missingTicks={}",
                    role, passed, boat.getX(), maximumVehicleStep, vehicleMissingTicks);
            sendResult("vehicle_visibility", passed, maximumVehicleStep);
            stage = 4;
            stageTicks = 0;
        }
    }

    private void runTeleportScenario(MinecraftClient client) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;
        if (role.equals("A")) {
            if (!sawFarTeleport && Math.abs(client.player.getX() - 64.5) < 1.0) {
                sawFarTeleport = true;
                RingWorldMod.LOGGER.info("[multiplayer:A] intentional long teleport preserved at x={}",
                        client.player.getX());
                sendResult("intentional_teleport", true, client.player.getX());
            } else if (sawFarTeleport
                    && Math.abs(client.player.getX() - (geometry.circumferenceBlocks() - 4.0)) < 1.0) {
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

        AbstractClientPlayerEntity remote = findRemotePlayer(client);
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
            client.getNetworkHandler().getConnection().disconnect(Text.literal("RingWorld automated reconnect"));
        }
    }

    private void runReconnectResult(MinecraftClient client) {
        if (!role.equals("B") || reconnectResultSent || stageTicks < 40) return;
        RingGeometry geometry = ClientRingState.geometry();
        AbstractClientPlayerEntity remote = findRemotePlayer(client);
        if ((geometry == null || remote == null) && stageTicks < 600) return;
        boolean passed = geometry != null && remote != null
                && Math.abs(geometry.shortestCircumferenceDelta(client.player.getX(), remote.getX())) < 12.0;
        RingWorldMod.LOGGER.info("[multiplayer:B] reconnect result={} localX={} remote={}",
                passed, client.player.getX(), remote == null ? "missing" : remote.getX());
        sendResult("reconnect", passed, remote == null ? Double.NaN : remote.getX());
        reconnectResultSent = true;
        stage = 6;
    }

    private void sendResult(String phase, boolean passed, double value) {
        if (ClientPlayNetworking.canSend(RingMultiplayerTestPayload.ID)) {
            ClientPlayNetworking.send(new RingMultiplayerTestPayload(role, phase, passed, value));
        }
    }

    private AbstractClientPlayerEntity findRemotePlayer(MinecraftClient client) {
        if (client.world == null || client.player == null) return null;
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player != client.player && player.getName().getString().startsWith("RingTester")) return player;
        }
        return null;
    }

    private BoatEntity findBoat(MinecraftClient client) {
        if (client.world == null) return null;
        for (Entity entity : client.world.getEntities()) {
            if (entity instanceof BoatEntity boat) return boat;
        }
        return null;
    }
}
