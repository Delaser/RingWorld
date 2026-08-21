//package dev.ringworld.client;
//
//import dev.ringworld.RingWorldMod;
//import dev.ringworld.client.mixin.CompassAngleStateFixtureAccessor;
//import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
//import dev.ringworld.world.RingGeometry;
//import net.minecraft.client.Minecraft;
//import net.minecraft.client.Screenshot;
//import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
//import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
//import net.minecraft.client.gui.screens.TitleScreen;
//import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
//import net.minecraft.core.BlockPos;
//import net.minecraft.core.Direction;
//import net.minecraft.core.GlobalPos;
//import net.minecraft.core.component.DataComponents;
//import net.minecraft.network.chat.Component;
//import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
//import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
//import net.minecraft.server.MinecraftServer;
//import net.minecraft.server.level.ServerLevel;
//import net.minecraft.server.level.ServerPlayer;
//import net.minecraft.world.entity.Relative;
//import net.minecraft.world.entity.decoration.ItemFrame;
//import net.minecraft.world.item.ItemStack;
//import net.minecraft.world.item.Items;
//import net.minecraft.world.item.MapItem;
//import net.minecraft.world.item.component.LodestoneTracker;
//import net.minecraft.world.item.component.MapPostProcessing;
//import net.minecraft.world.level.GameType;
//import net.minecraft.world.level.Level;
//import net.minecraft.world.level.block.Blocks;
//import net.minecraft.world.level.material.MapColor;
//import net.minecraft.world.level.saveddata.maps.MapDecoration;
//import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
//import net.minecraft.world.level.saveddata.maps.MapId;
//import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
//import net.minecraft.world.level.storage.LevelData;
//
//import java.util.Optional;
//import java.util.Set;
//import java.util.UUID;
//
//// Disabled for initial 1.21.1 backport:
//// Test-only map/compass acceptance fixture depends on the newer
//// CompassAngleState/CompassTarget/ItemOwner property system.
//// Re-enable after compass behavior is ported to the 1.21.1
//// ItemProperties / ItemPropertyFunction implementation.
///** Real-client acceptance for periodic filled maps and all vanilla compass targets. */
//public final class RingMapCompassCaptureClient {
//    public static final String ENABLE_PROPERTY = "ringworld.captureMapCompass";
//    private static final String WORLD_NAME = "RingWorld Map Compass Capture";
//    private static final int FIXTURE_Y = 200;
//    private static final int TIMEOUT_TICKS = 6_000;
//    private static final int SETTLE_TICKS = 40;
//    private static final int DISCONNECT_CLEAR_TIMEOUT_TICKS = 200;
//    private static final int PERSISTENCE_ENTITY_TIMEOUT_TICKS = 400;
//    private static final int SCALED_MAP = 0;
//    private static final int UNSCALED_MAP = 1;
//    private static final int SPAWN_COMPASS = 2;
//    private static final int LODESTONE_COMPASS = 3;
//    private static final int RECOVERY_COMPASS = 4;
//
//    private boolean worldScreenOpened;
//    private boolean worldStarted;
//    private boolean setupRequested;
//    private boolean reopenRequested;
//    private boolean disconnectClearedState;
//    private volatile UUID liveFrameId;
//    private volatile Boolean persistedFrameVerified;
//    private volatile boolean persistedVerificationPending;
//    private volatile int serverMutationStage;
//    private volatile String serverMutationFailure;
//    private int stage;
//    private int ticks;
//    private int stageTicks;
//
//    public boolean enabled() { return Boolean.getBoolean(ENABLE_PROPERTY); }
//
//    public boolean startWorldIfEnabled(Minecraft client) {
//        if (!enabled() || client.level != null || worldStarted) return false;
//        client.options.pauseOnLostFocus = false;
//        if (!worldScreenOpened) {
//            if (!(client.screen instanceof TitleScreen)) return true;
//            CreateWorldScreen.openFresh(client, () -> worldScreenOpened = false);
//            worldScreenOpened = true;
//            return true;
//        }
//        if (client.screen instanceof CreateWorldScreen screen) {
//            WorldCreationUiState creator = screen.getUiState();
//            creator.setName(WORLD_NAME);
//            creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
//            creator.setAllowCommands(true);
//            creator.setSeed("-2162056627494116761");
//            ((CreateWorldScreenInvoker) screen).ringworld$createLevel();
//            worldStarted = true;
//        }
//        return true;
//    }
//
//    public boolean tick(Minecraft client) {
//        if (!enabled()) return false;
//        if (++ticks > TIMEOUT_TICKS) return finish(client, false, "timed out at stage " + stage);
//        String mutationFailure = serverMutationFailure;
//        if (mutationFailure != null) return finish(client, false, mutationFailure);
//        if (stage == 10) return reopenWorld(client);
//        if (client.player == null || client.level == null || ClientRingState.geometry() == null
//                || !client.isGameLoadFinished()) return true;
//
//        RingGeometry geometry = ClientRingState.geometry();
//        switch (stage) {
//            case 0 -> {
//                if (!setupRequested) {
//                    setupRequested = true;
//                    scheduleSetup(client, geometry);
//                }
//                if (!mapsReady(client)) return true;
//                stage = 1;
//                stageTicks = 0;
//            }
//            case 1 -> {
//                if (!serverMutationReady(1) || !settled()) return true;
//                ItemStack map = client.player.getInventory().getItem(SCALED_MAP);
//                if (!verifyMap(client, map, true, geometry, true, 0, false))
//                    return finish(client, false, "high-centred map mismatch");
//                scaleHighMap(client, geometry);
//                stage = 2;
//                stageTicks = 0;
//            }
//            case 2 -> {
//                if (!serverMutationReady(2) || !settled()) return true;
//                ItemStack map = client.player.getInventory().getItem(SCALED_MAP);
//                if (!verifyMap(client, map, true, geometry, true, 1, false))
//                    return finish(client, false, "scaled high-centred map mismatch");
//                select(client, SCALED_MAP);
//                capture(client, "ringworld-map-scale");
//                removeHighBanner(client, geometry);
//                stage = 3;
//                stageTicks = 0;
//            }
//            case 3 -> {
//                if (!serverMutationReady(3) || !settled()) return true;
//                ItemStack map = client.player.getInventory().getItem(SCALED_MAP);
//                if (!verifyMap(client, map, true, geometry, false, 1, false))
//                    return finish(client, false, "scaled high-centred banner removal mismatch");
//                capture(client, "ringworld-map-banner-removed");
//                restoreHighBanner(client, geometry);
//                stage = 4;
//                stageTicks = 0;
//            }
//            case 4 -> {
//                if (!serverMutationReady(4) || !settled()) return true;
//                ItemStack map = client.player.getInventory().getItem(SCALED_MAP);
//                if (!verifyMap(client, map, true, geometry, true, 1, false))
//                    return finish(client, false, "scaled high-centred banner restore mismatch");
//                lockHighMap(client);
//                stage = 5;
//                stageTicks = 0;
//            }
//            case 5 -> {
//                if (!serverMutationReady(5) || !settled()) return true;
//                ItemStack map = client.player.getInventory().getItem(SCALED_MAP);
//                if (!verifyMap(client, map, true, geometry, true, 1, true))
//                    return finish(client, false, "locked high-centred map mismatch");
//                select(client, SCALED_MAP);
//                capture(client, "ringworld-map-high-to-low");
//                select(client, UNSCALED_MAP);
//                configureMapSide(client, geometry, false, 6);
//                stage = 6;
//                stageTicks = 0;
//            }
//            case 6 -> {
//                if (!serverMutationReady(6) || !settled()) return true;
//                ItemStack map = client.player.getInventory().getItem(UNSCALED_MAP);
//                if (!verifyMap(client, map, false, geometry, true, 0, false))
//                    return finish(client, false, "low-centred map mismatch");
//                capture(client, "ringworld-map-low-to-high");
//                select(client, LODESTONE_COMPASS);
//                configureCompassSide(client, geometry, true, 7);
//                stage = 7;
//                stageTicks = 0;
//            }
//            case 7 -> {
//                if (!serverMutationReady(7) || !compassSideReady(client, geometry, true) || !settled()) return true;
//                if (!verifyCompasses(client, geometry, true))
//                    return finish(client, false, "high-to-low compass mismatch");
//                capture(client, "ringworld-compass-high-to-low");
//                configureCompassSide(client, geometry, false, 8);
//                stage = 8;
//                stageTicks = 0;
//            }
//            case 8 -> {
//                if (!serverMutationReady(8) || !compassSideReady(client, geometry, false) || !settled()) return true;
//                if (!verifyCompasses(client, geometry, false))
//                    return finish(client, false, "low-to-high compass mismatch");
//                select(client, LODESTONE_COMPASS);
//                capture(client, "ringworld-compass-low-to-high");
//                // Persist the high-to-low target set. The preceding low-to-high
//                // compass check intentionally changed these server-owned values.
//                configureCompassSide(client, geometry, true, 9);
//                stage = 9;
//                stageTicks = 0;
//            }
//            case 9 -> {
//                if (!serverMutationReady(9) || !compassSideReady(client, geometry, true) || !settled()) return true;
//                RingWorldMod.LOGGER.info("[map-compass-capture] requesting normal save-and-disconnect before persistence verification");
//                client.disconnectFromWorld(Component.literal("RingWorld map compass persistence regression"));
//                stage = 10;
//                stageTicks = 0;
//            }
//            case 11 -> {
//                if (!mapsReady(client)) return true;
//                requestPersistedFrameVerification(client);
//                configureMapSide(client, geometry, true, 12);
//                stage = 12;
//                stageTicks = 0;
//            }
//            case 12 -> {
//                if (!serverMutationReady(12) || !settled()) return true;
//                if (persistedFrameVerified == null) {
//                    if (stageTicks > PERSISTENCE_ENTITY_TIMEOUT_TICKS) {
//                        return finish(client, false, "live map frame did not load after reopen");
//                    }
//                    requestPersistedFrameVerification(client);
//                    return true;
//                }
//                ItemStack map = client.player.getInventory().getItem(SCALED_MAP);
//                if (!verifyMap(client, map, true, geometry, true, 1, true)
//                        || !persistedCompassTargets(client, 2)
//                        || !persistedFrameVerified) {
//                    return finish(client, false,
//                            "map, live frame, or compass persistence mismatch after reopen");
//                }
//                select(client, SCALED_MAP);
//                capture(client, "ringworld-map-reopened");
//                configureCompassSide(client, geometry, true, 13);
//                stage = 13;
//                stageTicks = 0;
//            }
//            case 13 -> {
//                if (!serverMutationReady(13) || !compassSideReady(client, geometry, true) || !settled()) return true;
//                if (!verifyCompasses(client, geometry, true))
//                    return finish(client, false, "reopened high-to-low compass mismatch");
//                select(client, LODESTONE_COMPASS);
//                capture(client, "ringworld-compass-reopened");
//                return finish(client, true, "maps scale/lock, banner removal, persistence, and compass targets passed");
//            }
//            default -> { return true; }
//        }
//        return true;
//    }
//
//    private void scheduleSetup(Minecraft client, RingGeometry geometry) {
//        MinecraftServer server = client.getSingleplayerServer();
//        UUID playerId = client.player.getUUID();
//        executeServerTask(server, 1, "initial map fixture setup", () -> {
//            ServerLevel world = server.overworld();
//            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
//            if (player == null) return;
//            player.setGameMode(GameType.CREATIVE);
//            prepareSurface(world, geometry);
//            ItemStack high = createMap(world, player, geometry, true);
//            ItemStack low = createMap(world, player, geometry, false);
//            player.getInventory().clearContent();
//            player.getInventory().setItem(SCALED_MAP, high);
//            player.getInventory().setItem(UNSCALED_MAP, low);
//            player.getInventory().setItem(SPAWN_COMPASS, new ItemStack(Items.COMPASS));
//            player.getInventory().setItem(LODESTONE_COMPASS, lodestoneCompass(2));
//            player.getInventory().setItem(RECOVERY_COMPASS, new ItemStack(Items.RECOVERY_COMPASS));
//            player.getInventory().setSelectedSlot(SCALED_MAP);
//            player.inventoryMenu.broadcastFullState();
//            configureServerTargets(world, player, 2);
//            refreshMap(world, player, high, 2, true);
//            sendMap(world, player, low);
//            RingWorldMod.LOGGER.info("[map-compass-capture] server fixture ready highId={} highCenter={} lowId={} lowCenter={}",
//                    high.get(DataComponents.MAP_ID), MapItem.getSavedData(high, world).centerX,
//                    low.get(DataComponents.MAP_ID), MapItem.getSavedData(low, world).centerX);
//        });
//    }
//
//    private static void prepareSurface(ServerLevel world, RingGeometry geometry) {
//        int circumference = geometry.circumferenceBlocks();
//        for (int dz = -3; dz <= 3; dz++) for (int dx = -2; dx <= 2; dx++) {
//            world.setBlockAndUpdate(new BlockPos(geometry.wrapBlockX(dx), FIXTURE_Y, dz),
//                    Blocks.RED_WOOL.defaultBlockState());
//            world.setBlockAndUpdate(new BlockPos(geometry.wrapBlockX(circumference - 1 + dx), FIXTURE_Y, dz),
//                    Blocks.BLUE_WOOL.defaultBlockState());
//        }
//        for (int x : new int[] {1, circumference - 2}) {
//            BlockPos banner = new BlockPos(x, FIXTURE_Y, 5);
//            world.setBlockAndUpdate(banner.below(), Blocks.STONE.defaultBlockState());
//            world.setBlockAndUpdate(banner, Blocks.WHITE_BANNER.defaultBlockState());
//        }
//    }
//
//    private ItemStack createMap(ServerLevel world, ServerPlayer player,
//                                RingGeometry geometry, boolean highCentre) {
//        int circumference = geometry.circumferenceBlocks();
//        int center = highCentre ? circumference - 2 : 2;
//        int playerX = highCentre ? 2 : circumference - 2;
//        int bannerX = highCentre ? 1 : circumference - 2;
//        int frameX = highCentre ? 2 : circumference - 3;
//        ItemStack map = MapItem.create(world, center, 0, (byte) 0, true, true);
//        MapItemSavedData data = MapItem.getSavedData(map, world);
//        player.teleportTo(world, playerX + 0.5, FIXTURE_Y + 2.0, 0.5,
//                Set.<Relative>of(), 0.0F, 0.0F, false);
//        player.getInventory().add(map.copy());
//        data.tickCarriedBy(player, map, null);
//        for (int i = 0; i < 32; i++) ((MapItem) map.getItem()).update(world, player, data);
//        if (!data.toggleBanner(world, new BlockPos(bannerX, FIXTURE_Y, 5)))
//            throw new IllegalStateException("map banner toggle failed");
//        BlockPos framePosition = new BlockPos(frameX, FIXTURE_Y, -5);
//        world.setBlockAndUpdate(framePosition.relative(Direction.SOUTH), Blocks.STONE.defaultBlockState());
//        ItemFrame frame = new ItemFrame(world, framePosition, Direction.NORTH);
//        if (!frame.survives() || !world.addFreshEntity(frame)) {
//            throw new IllegalStateException("could not add initial live map frame");
//        }
//        if (highCentre) liveFrameId = frame.getUUID();
//        frame.setItem(map.copy(), false);
//        data.tickCarriedBy(player, map, frame);
//        return map;
//    }
//
//    private static void sendMap(ServerLevel world, ServerPlayer player, ItemStack stack) {
//        MapId id = stack.get(DataComponents.MAP_ID);
//        MapItemSavedData data = MapItem.getSavedData(stack, world);
//        var packet = data.getUpdatePacket(id, player);
//        if (packet != null) player.connection.send(packet);
//    }
//
//    private static void refreshMap(ServerLevel world, ServerPlayer player, ItemStack stack, int playerX,
//                                   boolean updatePixels) {
//        player.teleportTo(world, playerX + 0.5, FIXTURE_Y + 2.0, 0.5,
//                Set.<Relative>of(), 0.0F, 0.0F, false);
//        MapItemSavedData data = MapItem.getSavedData(stack, world);
//        data.tickCarriedBy(player, stack, null);
//        if (updatePixels && !data.locked) {
//            for (int i = 0; i < 48; i++) ((MapItem) stack.getItem()).update(world, player, data);
//        }
//        sendMap(world, player, stack);
//    }
//
//    private void configureMapSide(Minecraft client, RingGeometry geometry, boolean highCentre, int readyStage) {
//        int slot = highCentre ? SCALED_MAP : UNSCALED_MAP;
//        int playerX = highCentre ? 2 : geometry.circumferenceBlocks() - 2;
//        MinecraftServer server = client.getSingleplayerServer();
//        UUID playerId = client.player.getUUID();
//        executeServerTask(server, readyStage, "map-side update for stage " + readyStage, () -> {
//            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
//            refreshMap(server.overworld(), player, player.getInventory().getItem(slot), playerX, false);
//        });
//    }
//
//    private void scaleHighMap(Minecraft client, RingGeometry geometry) {
//        MinecraftServer server = client.getSingleplayerServer();
//        UUID playerId = client.player.getUUID();
//        executeServerTask(server, 2, "map scale", () -> {
//            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
//            ServerLevel world = server.overworld();
//            ItemStack map = player.getInventory().getItem(SCALED_MAP);
//            map.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
//            ((MapItem) map.getItem()).onCraftedPostProcess(map, world);
//            MapItemSavedData data = MapItem.getSavedData(map, world);
//            if (data == null || data.scale != 1 || data.locked) {
//                throw new IllegalStateException("map scaling did not create an unlocked scale-one map");
//            }
//            refreshMap(world, player, map, 2, true);
//            if (!data.toggleBanner(world, highBanner(geometry))) {
//                throw new IllegalStateException("scaled map banner toggle failed");
//            }
//            ensureLiveFrame(world, player, map, 2);
//            player.inventoryMenu.broadcastFullState();
//            sendMap(world, player, map);
//        });
//    }
//
//    private void removeHighBanner(Minecraft client, RingGeometry geometry) {
//        MinecraftServer server = client.getSingleplayerServer();
//        UUID playerId = client.player.getUUID();
//        executeServerTask(server, 3, "banner removal", () -> {
//            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
//            ServerLevel world = server.overworld();
//            world.removeBlock(highBanner(geometry), false);
//            refreshMap(world, player, player.getInventory().getItem(SCALED_MAP), 2, true);
//        });
//    }
//
//    private void restoreHighBanner(Minecraft client, RingGeometry geometry) {
//        MinecraftServer server = client.getSingleplayerServer();
//        UUID playerId = client.player.getUUID();
//        executeServerTask(server, 4, "banner restoration", () -> {
//            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
//            ServerLevel world = server.overworld();
//            BlockPos banner = highBanner(geometry);
//            world.setBlockAndUpdate(banner, Blocks.WHITE_BANNER.defaultBlockState());
//            MapItemSavedData data = MapItem.getSavedData(player.getInventory().getItem(SCALED_MAP), world);
//            if (data == null || !data.toggleBanner(world, banner)) {
//                throw new IllegalStateException("scaled map banner restore toggle failed");
//            }
//            refreshMap(world, player, player.getInventory().getItem(SCALED_MAP), 2, true);
//        });
//    }
//
//    private void lockHighMap(Minecraft client) {
//        MinecraftServer server = client.getSingleplayerServer();
//        UUID playerId = client.player.getUUID();
//        executeServerTask(server, 5, "map lock", () -> {
//            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
//            ServerLevel world = server.overworld();
//            ItemStack map = player.getInventory().getItem(SCALED_MAP);
//            map.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.LOCK);
//            ((MapItem) map.getItem()).onCraftedPostProcess(map, world);
//            MapItemSavedData data = MapItem.getSavedData(map, world);
//            if (data == null || data.scale != 1 || !data.locked) {
//                throw new IllegalStateException("map locking did not create a locked scale-one map");
//            }
//            // Locked maps retain banner decorations, but frame persistence is created by a carried-map tick.
//            ensureLiveFrame(world, player, map, 2);
//            refreshMap(world, player, map, 2, false);
//            player.inventoryMenu.broadcastFullState();
//            sendMap(world, player, map);
//        });
//    }
//
//    private static BlockPos highBanner(RingGeometry geometry) {
//        return new BlockPos(1, FIXTURE_Y, 5);
//    }
//
//    private void ensureLiveFrame(ServerLevel world, ServerPlayer player, ItemStack map, int frameX) {
//        ItemFrame frame = liveFrameId == null ? null
//                : world.getEntityInAnyDimension(liveFrameId) instanceof ItemFrame existing
//                ? existing : null;
//        if (frame == null) {
//            BlockPos framePosition = new BlockPos(frameX, FIXTURE_Y, -5);
//            world.setBlockAndUpdate(framePosition.relative(Direction.SOUTH),
//                    Blocks.STONE.defaultBlockState());
//            frame = new ItemFrame(world, framePosition, Direction.NORTH);
//            if (!frame.survives()) {
//                throw new IllegalStateException("live map frame has no valid support");
//            }
//            if (!world.addFreshEntity(frame)) {
//                throw new IllegalStateException("could not add live map frame to fixture world");
//            }
//            liveFrameId = frame.getUUID();
//        }
//        MapId previousId = frame.getItem().get(DataComponents.MAP_ID);
//        MapId replacementId = map.get(DataComponents.MAP_ID);
//        if (previousId != null && !previousId.equals(replacementId)) {
//            MapItemSavedData previous = MapItem.getSavedData(previousId, world);
//            if (previous != null) previous.removedFromFrame(frame.getPos(), frame.getId());
//        }
//        frame.setItem(map.copy(), false);
//        MapItemSavedData data = MapItem.getSavedData(map, world);
//        if (data == null) throw new IllegalStateException("live frame map data is unavailable");
//        data.tickCarriedBy(player, map, frame);
//    }
//
//    private static ItemStack lodestoneCompass(int targetX) {
//        ItemStack stack = new ItemStack(Items.COMPASS);
//        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(
//                GlobalPos.of(Level.OVERWORLD, new BlockPos(targetX, FIXTURE_Y + 2, 0))), false));
//        return stack;
//    }
//
//    private static void configureServerTargets(ServerLevel world, ServerPlayer player, int targetX) {
//        BlockPos target = new BlockPos(targetX, FIXTURE_Y + 2, 0);
//        LevelData.RespawnData respawn = LevelData.RespawnData.of(Level.OVERWORLD, target, 0.0F, 0.0F);
//        world.setRespawnData(respawn);
//        player.connection.send(new ClientboundSetDefaultSpawnPositionPacket(respawn));
//        player.setLastDeathLocation(Optional.of(GlobalPos.of(Level.OVERWORLD, target)));
//        player.getInventory().setItem(LODESTONE_COMPASS, lodestoneCompass(targetX));
//        player.inventoryMenu.broadcastFullState();
//    }
//
//    private void configureCompassSide(Minecraft client, RingGeometry geometry, boolean highHolder, int readyStage) {
//        int targetX = highHolder ? 2 : geometry.circumferenceBlocks() - 2;
//        int holderX = highHolder ? geometry.circumferenceBlocks() - 4 : 4;
//        MinecraftServer server = client.getSingleplayerServer();
//        UUID playerId = client.player.getUUID();
//        executeServerTask(server, readyStage, "compass-side update for stage " + readyStage, () -> {
//            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
//            ServerLevel world = server.overworld();
//            configureServerTargets(world, player, targetX);
//            player.teleportTo(world, holderX + 0.5, FIXTURE_Y + 2.0, 0.5,
//                    Set.<Relative>of(), 0.0F, 0.0F, false);
//        });
//    }
//
//    private boolean serverMutationReady(int expectedStage) {
//        return serverMutationStage >= expectedStage;
//    }
//
//    private void executeServerTask(MinecraftServer server, int readyStage,
//                                   String operation, Runnable action) {
//        server.execute(() -> {
//            try {
//                action.run();
//                if (readyStage >= 0) serverMutationStage = readyStage;
//            } catch (Throwable failure) {
//                String message = failure.getMessage();
//                serverMutationFailure = operation + " failed: "
//                        + (message == null || message.isBlank()
//                        ? failure.getClass().getSimpleName() : message);
//                RingWorldMod.LOGGER.error("[map-compass-capture] " + serverMutationFailure, failure);
//            }
//        });
//    }
//
//    private boolean compassSideReady(Minecraft client, RingGeometry geometry, boolean highHolder) {
//        int targetX = highHolder ? 2 : geometry.circumferenceBlocks() - 2;
//        int holderX = highHolder ? geometry.circumferenceBlocks() - 4 : 4;
//        LodestoneTracker tracker = client.player.getInventory().getItem(LODESTONE_COMPASS)
//                .get(DataComponents.LODESTONE_TRACKER);
//        return tracker != null && tracker.target().map(GlobalPos::pos).map(BlockPos::getX)
//                .filter(x -> x == targetX).isPresent()
//                && client.level.getRespawnData().pos().getX() == targetX
//                && Math.abs(geometry.shortestCircumferenceDelta(holderX + 0.5, client.player.getX())) < 1.0;
//    }
//
//    private boolean verifyMap(Minecraft client, ItemStack stack, boolean highCentre, RingGeometry geometry,
//                              boolean expectedBanner, int expectedScale, boolean expectedLocked) {
//        MapItemSavedData data = MapItem.getSavedData(stack, client.level);
//        if (data == null) return false;
//        MapItemSavedData expected = MapItemSavedData.createFresh(
//                highCentre ? geometry.circumferenceBlocks() - 2 : 2,
//                0, (byte) expectedScale, true, true, Level.OVERWORLD);
//        int expectedCenter = expected.centerX;
//        int imageX = highCentre ? geometry.circumferenceBlocks() + 2 : -3;
//        int pixelX = 64 + Math.floorDiv(imageX - expectedCenter, 1 << expectedScale);
//        int pixelZ = 64 + Math.floorDiv(-expected.centerZ, 1 << expectedScale);
//        if (pixelX < 0 || pixelX >= 128 || pixelZ < 0 || pixelZ >= 128) {
//            RingWorldMod.LOGGER.info(
//                    "[map-compass-capture] invalid map pixel centre=({}, {}) image=({}, 0) pixel=({}, {})",
//                    expectedCenter, expected.centerZ, imageX, pixelX, pixelZ);
//            return false;
//        }
//        int expectedHue = highCentre ? MapColor.COLOR_RED.id : MapColor.COLOR_BLUE.id;
//        int actualHue = Byte.toUnsignedInt(data.colors[pixelX + pixelZ * 128]) / 4;
//        int sign = Integer.signum(imageX - expectedCenter);
//        boolean player = false, banner = false, frame = false;
//        for (MapDecoration decoration : data.getDecorations()) {
//            if (Integer.signum(decoration.x()) != sign) continue;
//            RingWorldMod.LOGGER.info("[map-compass-capture] decoration type={} x={} y={}",
//                    decoration.type(), decoration.x(), decoration.y());
//            if (decoration.type().equals(MapDecorationTypes.PLAYER)
//                    || decoration.type().equals(MapDecorationTypes.PLAYER_OFF_MAP)
//                    || decoration.type().equals(MapDecorationTypes.PLAYER_OFF_LIMITS)) player = true;
//            if (decoration.type().equals(MapDecorationTypes.FRAME)) frame = true;
//            if (decoration.type().equals(MapDecorationTypes.WHITE_BANNER)) banner = true;
//        }
//        RingWorldMod.LOGGER.info("[map-compass-capture] map centre(client/expected)=({}, {})/({}, {}) scale={} locked={} pixel=({}, {}) hue={}/{} player={} banner={}/{} frame={}",
//                data.centerX, data.centerZ, expectedCenter, expected.centerZ, data.scale, data.locked,
//                pixelX, pixelZ, actualHue, expectedHue,
//                player, banner, expectedBanner, frame);
//        // Client map state intentionally uses a zero-centred presentation
//        // container; the server-owned map centre is verified after reopen.
//        return data.scale == expectedScale && data.locked == expectedLocked
//                && actualHue == expectedHue && player && banner == expectedBanner && frame;
//    }
//
//    private boolean verifyCompasses(Minecraft client, RingGeometry geometry, boolean highHolder) {
//        int targetX = highHolder ? 2 : geometry.circumferenceBlocks() - 2;
//        int imageX = highHolder ? geometry.circumferenceBlocks() + 2 : -2;
//        client.player.setLastDeathLocation(Optional.of(GlobalPos.of(Level.OVERWORLD,
//                new BlockPos(targetX, FIXTURE_Y + 2, 0))));
//        boolean spawn = stable(client, client.player.getInventory().getItem(SPAWN_COMPASS), CompassAngleState.CompassTarget.SPAWN);
//        boolean lodestone = matchesNearestImage(client, client.player.getInventory().getItem(LODESTONE_COMPASS), imageX);
//        ItemStack recovery = client.player.getInventory().getItem(RECOVERY_COMPASS);
//        float actual = calculate(client, recovery, CompassAngleState.CompassTarget.RECOVERY, 41);
//        client.player.setLastDeathLocation(Optional.of(GlobalPos.of(Level.OVERWORLD,
//                new BlockPos(imageX, FIXTURE_Y + 2, 0))));
//        float expected = calculate(client, recovery, CompassAngleState.CompassTarget.RECOVERY, 41);
//        boolean recoveryMatches = circularDistance(actual, expected) < 0.001F;
//        client.player.setLastDeathLocation(Optional.of(GlobalPos.of(Level.OVERWORLD,
//                new BlockPos(targetX, FIXTURE_Y + 2, 0))));
//        boolean exact = exactTargetRandomizes(client, imageX);
//        RingWorldMod.LOGGER.info("[map-compass-capture] compass {} spawn={} lodestone={} recovery={} exact={}",
//                highHolder ? "high-to-low" : "low-to-high", spawn, lodestone, recoveryMatches, exact);
//        return spawn && lodestone && recoveryMatches && exact;
//    }
//
//    private boolean matchesNearestImage(Minecraft client, ItemStack canonical, int imageX) {
//        ItemStack image = canonical.copy();
//        image.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(
//                GlobalPos.of(Level.OVERWORLD, new BlockPos(imageX, FIXTURE_Y + 2, 0))), false));
//        return circularDistance(calculate(client, canonical, CompassAngleState.CompassTarget.LODESTONE, 31),
//                calculate(client, image, CompassAngleState.CompassTarget.LODESTONE, 31)) < 0.001F;
//    }
//
//    private boolean stable(Minecraft client, ItemStack stack, CompassAngleState.CompassTarget target) {
//        return circularDistance(calculate(client, stack, target, 17),
//                calculate(client, stack, target, 53)) < 0.001F;
//    }
//
//    private boolean exactTargetRandomizes(Minecraft client, int targetImageX) {
//        double oldX = client.player.getX(), oldY = client.player.getY(), oldZ = client.player.getZ();
//        client.player.setPos(targetImageX + 0.5, FIXTURE_Y + 2.5, 0.5);
//        ItemStack lodestone = client.player.getInventory().getItem(LODESTONE_COMPASS);
//        // Reuse one property state so its no-target wobble contributes the
//        // same random base to both seeds. Constructing two states made this
//        // assertion probabilistic because their independent random offsets
//        // could occasionally cancel the deterministic seed difference.
//        CompassAngleState state = new CompassAngleState(
//                false, CompassAngleState.CompassTarget.LODESTONE);
//        CompassAngleStateFixtureAccessor fixture = (CompassAngleStateFixtureAccessor)(Object)state;
//        float first = fixture.ringworld$calculate(lodestone, client.level, 7, client.player);
//        float second = fixture.ringworld$calculate(lodestone, client.level, 71, client.player);
//        float difference = circularDistance(first, second);
//        RingWorldMod.LOGGER.info(
//                "[map-compass-capture] exact-target holder=({}, {}, {}) imageX={} rotations={}/{} delta={}",
//                client.player.getX(), client.player.getY(), client.player.getZ(), targetImageX,
//                first, second, difference);
//        client.player.setPos(oldX, oldY, oldZ);
//        return difference > 0.01F;
//    }
//
//    private float calculate(Minecraft client, ItemStack stack, CompassAngleState.CompassTarget target, int seed) {
//        CompassAngleState state = new CompassAngleState(false, target);
//        return ((CompassAngleStateFixtureAccessor) (Object) state)
//                .ringworld$calculate(stack, client.level, seed, client.player);
//    }
//
//    private static float circularDistance(float a, float b) {
//        float delta = Math.abs(a - b);
//        return Math.min(delta, 1.0F - delta);
//    }
//
//    private boolean mapsReady(Minecraft client) {
//        boolean ready = client.player.getInventory().getItem(SCALED_MAP).get(DataComponents.MAP_ID) != null
//                && client.player.getInventory().getItem(UNSCALED_MAP).get(DataComponents.MAP_ID) != null
//                && MapItem.getSavedData(client.player.getInventory().getItem(SCALED_MAP), client.level) != null
//                && MapItem.getSavedData(client.player.getInventory().getItem(UNSCALED_MAP), client.level) != null;
//        if (ready) RingWorldMod.LOGGER.info("[map-compass-capture] client maps ready slot0={} center0={} slot1={} center1={}",
//                client.player.getInventory().getItem(SCALED_MAP).get(DataComponents.MAP_ID),
//                MapItem.getSavedData(client.player.getInventory().getItem(SCALED_MAP), client.level).centerX,
//                client.player.getInventory().getItem(UNSCALED_MAP).get(DataComponents.MAP_ID),
//                MapItem.getSavedData(client.player.getInventory().getItem(UNSCALED_MAP), client.level).centerX);
//        return ready;
//    }
//
//    private boolean reopenWorld(Minecraft client) {
//        if (client.level != null || client.getSingleplayerServer() != null) return true;
//        if (!disconnectClearedState) {
//            disconnectClearedState = RingWorldClientSession.isCleared();
//            if (!disconnectClearedState) {
//                if (++stageTicks > DISCONNECT_CLEAR_TIMEOUT_TICKS) {
//                    return finish(client, false,
//                            "disconnect did not clear RingWorld client state within "
//                                    + DISCONNECT_CLEAR_TIMEOUT_TICKS + " ticks");
//                }
//                return true;
//            }
//        }
//        if (!reopenRequested) {
//            reopenRequested = true;
//            RingWorldMod.LOGGER.info("[map-compass-capture] reopening '{}' after clean client teardown", WORLD_NAME);
//            stage = 11;
//            stageTicks = 0;
//            client.createWorldOpenFlows().openWorld(WORLD_NAME,
//                    () -> finish(client, false, "persistence reopen cancelled"));
//        }
//        return true;
//    }
//
//    private void requestPersistedFrameVerification(Minecraft client) {
//        if (persistedVerificationPending) return;
//        persistedVerificationPending = true;
//        MinecraftServer server = client.getSingleplayerServer();
//        UUID playerId = client.player.getUUID();
//        UUID expectedFrameId = liveFrameId;
//        RingGeometry geometry = ClientRingState.geometry();
//        executeServerTask(server, -1, "reopened map/frame persistence verification", () -> {
//            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
//            ItemStack expectedMap = player == null ? ItemStack.EMPTY
//                    : player.getInventory().getItem(SCALED_MAP);
//            MapId expectedMapId = expectedMap.get(DataComponents.MAP_ID);
//            MapItemSavedData serverMap = MapItem.getSavedData(expectedMap, server.overworld());
//            MapItemSavedData expected = MapItemSavedData.createFresh(
//                    geometry.circumferenceBlocks() - 2, 0, (byte) 1,
//                    true, true, Level.OVERWORLD);
//            boolean mapMatches = serverMap != null && serverMap.centerX == expected.centerX
//                    && serverMap.centerZ == expected.centerZ
//                    && serverMap.scale == 1 && serverMap.locked;
//            var entity = expectedFrameId == null ? null
//                    : server.overworld().getEntityInAnyDimension(expectedFrameId);
//            boolean frameLoaded = entity instanceof ItemFrame;
//            boolean frameMatches = frameLoaded && expectedMapId != null
//                    && expectedMapId.equals(((ItemFrame)entity).getItem().get(DataComponents.MAP_ID));
//            persistedFrameVerified = !mapMatches ? Boolean.FALSE
//                    : frameLoaded ? frameMatches : null;
//            persistedVerificationPending = false;
//            RingWorldMod.LOGGER.info(
//                    "[map-compass-capture] reopened server map persisted={} live item frame loaded={} persisted={}",
//                    mapMatches, frameLoaded, frameMatches);
//        });
//    }
//
//    private boolean persistedCompassTargets(Minecraft client, int targetX) {
//        LodestoneTracker tracker = client.player.getInventory().getItem(LODESTONE_COMPASS)
//                .get(DataComponents.LODESTONE_TRACKER);
//        boolean lodestone = tracker != null && tracker.target().map(GlobalPos::pos).map(BlockPos::getX)
//                .filter(x -> x == targetX).isPresent();
//        boolean spawn = client.level.getRespawnData().pos().getX() == targetX;
//        boolean recovery = client.player.getLastDeathLocation().map(GlobalPos::pos).map(BlockPos::getX)
//                .filter(x -> x == targetX).isPresent();
//        RingWorldMod.LOGGER.info("[map-compass-capture] reopened targets spawn={} lodestone={} recovery={} stateCleared={}",
//                spawn, lodestone, recovery, disconnectClearedState);
//        return disconnectClearedState && spawn && lodestone && recovery;
//    }
//
//    private boolean settled() { return ++stageTicks >= SETTLE_TICKS; }
//
//    private static void select(Minecraft client, int slot) {
//        client.player.getInventory().setSelectedSlot(slot);
//        client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
//        client.setScreen(null);
//    }
//
//    private static void capture(Minecraft client, String name) {
//        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1,
//                message -> RingWorldMod.LOGGER.info("[map-compass-capture] screenshot {}: {}", name, message.getString()));
//    }
//
//    private boolean finish(Minecraft client, boolean passed, String detail) {
//        stage = Integer.MAX_VALUE;
//        RingWorldMod.LOGGER.info("[map-compass-capture] {}: {}", passed ? "PASS" : "FAIL", detail);
//        client.stop();
//        return true;
//    }
//}
