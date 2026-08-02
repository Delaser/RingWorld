package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.mixin.CompassAngleStateFixtureAccessor;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Real-client acceptance for periodic filled maps and all vanilla compass targets. */
public final class RingMapCompassCaptureClient {
    public static final String ENABLE_PROPERTY = "ringworld.captureMapCompass";
    private static final int FIXTURE_Y = 200;
    private static final int TIMEOUT_TICKS = 6_000;
    private static final int SETTLE_TICKS = 40;

    private boolean worldScreenOpened;
    private boolean worldStarted;
    private boolean setupRequested;
    private int stage;
    private int ticks;
    private int stageTicks;

    public boolean enabled() { return Boolean.getBoolean(ENABLE_PROPERTY); }

    public boolean startWorldIfEnabled(Minecraft client) {
        if (!enabled() || client.level != null || worldStarted) return false;
        if (!worldScreenOpened) {
            CreateWorldScreen.openFresh(client, () -> worldScreenOpened = false);
            worldScreenOpened = true;
            return true;
        }
        if (client.screen instanceof CreateWorldScreen screen) {
            WorldCreationUiState creator = screen.getUiState();
            creator.setName("RingWorld Map Compass Capture");
            creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            creator.setAllowCommands(true);
            creator.setSeed("-2162056627494116761");
            ((CreateWorldScreenInvoker) screen).ringworld$createLevel();
            worldStarted = true;
        }
        return true;
    }

    public boolean tick(Minecraft client) {
        if (!enabled()) return false;
        if (++ticks > TIMEOUT_TICKS) return finish(client, false, "timed out at stage " + stage);
        if (client.player == null || client.level == null || ClientRingState.geometry() == null
                || !client.isGameLoadFinished()) return true;

        RingGeometry geometry = ClientRingState.geometry();
        switch (stage) {
            case 0 -> {
                if (!setupRequested) {
                    setupRequested = true;
                    scheduleSetup(client, geometry);
                }
                if (!mapsReady(client)) return true;
                stage = 1;
                stageTicks = 0;
            }
            case 1 -> {
                if (!settled()) return true;
                ItemStack map = client.player.getInventory().getItem(0);
                if (!verifyMap(client, map, true, geometry))
                    return finish(client, false, "high-centred map mismatch");
                select(client, 0);
                capture(client, "ringworld-map-high-to-low");
                select(client, 1);
                configureMapSide(client, geometry, false);
                stage = 2;
                stageTicks = 0;
            }
            case 2 -> {
                if (!settled()) return true;
                ItemStack map = client.player.getInventory().getItem(1);
                if (!verifyMap(client, map, false, geometry))
                    return finish(client, false, "low-centred map mismatch");
                capture(client, "ringworld-map-low-to-high");
                select(client, 3);
                configureCompassSide(client, geometry, true);
                stage = 3;
                stageTicks = 0;
            }
            case 3 -> {
                if (!compassSideReady(client, geometry, true) || !settled()) return true;
                if (!verifyCompasses(client, geometry, true))
                    return finish(client, false, "high-to-low compass mismatch");
                capture(client, "ringworld-compass-high-to-low");
                configureCompassSide(client, geometry, false);
                stage = 4;
                stageTicks = 0;
            }
            case 4 -> {
                if (!compassSideReady(client, geometry, false) || !settled()) return true;
                if (!verifyCompasses(client, geometry, false))
                    return finish(client, false, "low-to-high compass mismatch");
                select(client, 3);
                capture(client, "ringworld-compass-low-to-high");
                return finish(client, true, "both map directions and all compass targets passed");
            }
            default -> { return true; }
        }
        return true;
    }

    private void scheduleSetup(Minecraft client, RingGeometry geometry) {
        MinecraftServer server = client.getSingleplayerServer();
        UUID playerId = client.player.getUUID();
        server.execute(() -> {
            ServerLevel world = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) return;
            player.setGameMode(GameType.CREATIVE);
            prepareSurface(world, geometry);
            ItemStack high = createMap(world, player, geometry, true);
            ItemStack low = createMap(world, player, geometry, false);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, high);
            player.getInventory().setItem(1, low);
            player.getInventory().setItem(2, new ItemStack(Items.COMPASS));
            player.getInventory().setItem(3, lodestoneCompass(2));
            player.getInventory().setItem(4, new ItemStack(Items.RECOVERY_COMPASS));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastFullState();
            configureServerTargets(world, player, 2);
            refreshMap(world, player, high, 2);
            sendMap(world, player, low);
            RingWorldMod.LOGGER.info("[map-compass-capture] server fixture ready highId={} highCenter={} lowId={} lowCenter={}",
                    high.get(DataComponents.MAP_ID), MapItem.getSavedData(high, world).centerX,
                    low.get(DataComponents.MAP_ID), MapItem.getSavedData(low, world).centerX);
        });
    }

    private static void prepareSurface(ServerLevel world, RingGeometry geometry) {
        int circumference = geometry.circumferenceBlocks();
        for (int dz = -3; dz <= 3; dz++) for (int dx = -2; dx <= 2; dx++) {
            world.setBlockAndUpdate(new BlockPos(geometry.wrapBlockX(dx), FIXTURE_Y, dz),
                    Blocks.RED_WOOL.defaultBlockState());
            world.setBlockAndUpdate(new BlockPos(geometry.wrapBlockX(circumference - 1 + dx), FIXTURE_Y, dz),
                    Blocks.BLUE_WOOL.defaultBlockState());
        }
        for (int x : new int[] {1, circumference - 2}) {
            BlockPos banner = new BlockPos(x, FIXTURE_Y, 5);
            world.setBlockAndUpdate(banner.below(), Blocks.STONE.defaultBlockState());
            world.setBlockAndUpdate(banner, Blocks.WHITE_BANNER.defaultBlockState());
        }
    }

    private static ItemStack createMap(ServerLevel world, ServerPlayer player,
                                       RingGeometry geometry, boolean highCentre) {
        int circumference = geometry.circumferenceBlocks();
        int center = highCentre ? circumference - 2 : 2;
        int playerX = highCentre ? 2 : circumference - 2;
        int bannerX = highCentre ? 1 : circumference - 2;
        int frameX = highCentre ? 2 : circumference - 3;
        ItemStack map = MapItem.create(world, center, 0, (byte) 0, true, true);
        MapItemSavedData data = MapItem.getSavedData(map, world);
        player.teleportTo(world, playerX + 0.5, FIXTURE_Y + 2.0, 0.5,
                Set.<Relative>of(), 0.0F, 0.0F, false);
        player.getInventory().add(map.copy());
        data.tickCarriedBy(player, map, null);
        for (int i = 0; i < 32; i++) ((MapItem) map.getItem()).update(world, player, data);
        if (!data.toggleBanner(world, new BlockPos(bannerX, FIXTURE_Y, 5)))
            throw new IllegalStateException("map banner toggle failed");
        ItemFrame frame = new ItemFrame(world, new BlockPos(frameX, FIXTURE_Y, -5), Direction.NORTH);
        frame.setItem(map.copy(), false);
        data.tickCarriedBy(player, map, frame);
        return map;
    }

    private static void sendMap(ServerLevel world, ServerPlayer player, ItemStack stack) {
        MapId id = stack.get(DataComponents.MAP_ID);
        MapItemSavedData data = MapItem.getSavedData(stack, world);
        var packet = data.getUpdatePacket(id, player);
        if (packet != null) player.connection.send(packet);
    }

    private static void refreshMap(ServerLevel world, ServerPlayer player, ItemStack stack, int playerX) {
        player.teleportTo(world, playerX + 0.5, FIXTURE_Y + 2.0, 0.5,
                Set.<Relative>of(), 0.0F, 0.0F, false);
        MapItemSavedData data = MapItem.getSavedData(stack, world);
        data.tickCarriedBy(player, stack, null);
        sendMap(world, player, stack);
    }

    private void configureMapSide(Minecraft client, RingGeometry geometry, boolean highCentre) {
        int slot = highCentre ? 0 : 1;
        int playerX = highCentre ? 2 : geometry.circumferenceBlocks() - 2;
        MinecraftServer server = client.getSingleplayerServer();
        UUID playerId = client.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            refreshMap(server.overworld(), player, player.getInventory().getItem(slot), playerX);
        });
    }

    private static ItemStack lodestoneCompass(int targetX) {
        ItemStack stack = new ItemStack(Items.COMPASS);
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(
                GlobalPos.of(Level.OVERWORLD, new BlockPos(targetX, FIXTURE_Y + 2, 0))), false));
        return stack;
    }

    private static void configureServerTargets(ServerLevel world, ServerPlayer player, int targetX) {
        BlockPos target = new BlockPos(targetX, FIXTURE_Y + 2, 0);
        LevelData.RespawnData respawn = LevelData.RespawnData.of(Level.OVERWORLD, target, 0.0F, 0.0F);
        world.setRespawnData(respawn);
        player.connection.send(new ClientboundSetDefaultSpawnPositionPacket(respawn));
        player.setLastDeathLocation(Optional.of(GlobalPos.of(Level.OVERWORLD, target)));
        player.getInventory().setItem(3, lodestoneCompass(targetX));
        player.inventoryMenu.broadcastFullState();
    }

    private void configureCompassSide(Minecraft client, RingGeometry geometry, boolean highHolder) {
        int targetX = highHolder ? 2 : geometry.circumferenceBlocks() - 2;
        int holderX = highHolder ? geometry.circumferenceBlocks() - 4 : 4;
        MinecraftServer server = client.getSingleplayerServer();
        UUID playerId = client.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel world = server.overworld();
            configureServerTargets(world, player, targetX);
            player.teleportTo(world, holderX + 0.5, FIXTURE_Y + 2.0, 0.5,
                    Set.<Relative>of(), 0.0F, 0.0F, false);
        });
    }

    private boolean compassSideReady(Minecraft client, RingGeometry geometry, boolean highHolder) {
        int targetX = highHolder ? 2 : geometry.circumferenceBlocks() - 2;
        int holderX = highHolder ? geometry.circumferenceBlocks() - 4 : 4;
        LodestoneTracker tracker = client.player.getInventory().getItem(3).get(DataComponents.LODESTONE_TRACKER);
        return tracker != null && tracker.target().map(GlobalPos::pos).map(BlockPos::getX)
                .filter(x -> x == targetX).isPresent()
                && client.level.getRespawnData().pos().getX() == targetX
                && Math.abs(geometry.shortestCircumferenceDelta(holderX + 0.5, client.player.getX())) < 1.0;
    }

    private boolean verifyMap(Minecraft client, ItemStack stack, boolean highCentre, RingGeometry geometry) {
        MapItemSavedData data = MapItem.getSavedData(stack, client.level);
        if (data == null) return false;
        int expectedCenter = MapItemSavedData.createFresh(
                highCentre ? geometry.circumferenceBlocks() - 2 : 2,
                0, (byte) 0, true, true, Level.OVERWORLD).centerX;
        int imageX = highCentre ? geometry.circumferenceBlocks() + 2 : -3;
        int pixelX = 64 + imageX - expectedCenter;
        if (pixelX < 0 || pixelX >= 128) {
            RingWorldMod.LOGGER.info("[map-compass-capture] invalid map pixel centre={} image={} pixel={}",
                    expectedCenter, imageX, pixelX);
            return false;
        }
        int expectedHue = highCentre ? MapColor.COLOR_RED.id : MapColor.COLOR_BLUE.id;
        int actualHue = Byte.toUnsignedInt(data.colors[pixelX + 64 * 128]) / 4;
        int sign = highCentre ? 1 : -1;
        boolean player = false, banner = false, frame = false;
        for (MapDecoration decoration : data.getDecorations()) {
            if (Integer.signum(decoration.x()) != sign) continue;
            RingWorldMod.LOGGER.info("[map-compass-capture] decoration type={} x={} y={}",
                    decoration.type(), decoration.x(), decoration.y());
            if (decoration.type().equals(MapDecorationTypes.PLAYER)
                    || decoration.type().equals(MapDecorationTypes.PLAYER_OFF_MAP)
                    || decoration.type().equals(MapDecorationTypes.PLAYER_OFF_LIMITS)) player = true;
            if (decoration.type().equals(MapDecorationTypes.FRAME)) frame = true;
            if (decoration.type().equals(MapDecorationTypes.WHITE_BANNER)) banner = true;
        }
        RingWorldMod.LOGGER.info("[map-compass-capture] map centre={} pixel={} hue={}/{} player={} banner={} frame={}",
                expectedCenter, pixelX, actualHue, expectedHue, player, banner, frame);
        return actualHue == expectedHue && player && banner && frame;
    }

    private boolean verifyCompasses(Minecraft client, RingGeometry geometry, boolean highHolder) {
        int targetX = highHolder ? 2 : geometry.circumferenceBlocks() - 2;
        int imageX = highHolder ? geometry.circumferenceBlocks() + 2 : -2;
        client.player.setLastDeathLocation(Optional.of(GlobalPos.of(Level.OVERWORLD,
                new BlockPos(targetX, FIXTURE_Y + 2, 0))));
        boolean spawn = stable(client, client.player.getInventory().getItem(2), CompassAngleState.CompassTarget.SPAWN);
        boolean lodestone = matchesNearestImage(client, client.player.getInventory().getItem(3), imageX);
        ItemStack recovery = client.player.getInventory().getItem(4);
        float actual = calculate(client, recovery, CompassAngleState.CompassTarget.RECOVERY, 41);
        client.player.setLastDeathLocation(Optional.of(GlobalPos.of(Level.OVERWORLD,
                new BlockPos(imageX, FIXTURE_Y + 2, 0))));
        float expected = calculate(client, recovery, CompassAngleState.CompassTarget.RECOVERY, 41);
        boolean recoveryMatches = circularDistance(actual, expected) < 0.001F;
        client.player.setLastDeathLocation(Optional.of(GlobalPos.of(Level.OVERWORLD,
                new BlockPos(targetX, FIXTURE_Y + 2, 0))));
        boolean exact = exactTargetRandomizes(client, imageX);
        RingWorldMod.LOGGER.info("[map-compass-capture] compass {} spawn={} lodestone={} recovery={} exact={}",
                highHolder ? "high-to-low" : "low-to-high", spawn, lodestone, recoveryMatches, exact);
        return spawn && lodestone && recoveryMatches && exact;
    }

    private boolean matchesNearestImage(Minecraft client, ItemStack canonical, int imageX) {
        ItemStack image = canonical.copy();
        image.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(
                GlobalPos.of(Level.OVERWORLD, new BlockPos(imageX, FIXTURE_Y + 2, 0))), false));
        return circularDistance(calculate(client, canonical, CompassAngleState.CompassTarget.LODESTONE, 31),
                calculate(client, image, CompassAngleState.CompassTarget.LODESTONE, 31)) < 0.001F;
    }

    private boolean stable(Minecraft client, ItemStack stack, CompassAngleState.CompassTarget target) {
        return circularDistance(calculate(client, stack, target, 17),
                calculate(client, stack, target, 53)) < 0.001F;
    }

    private boolean exactTargetRandomizes(Minecraft client, int targetImageX) {
        double oldX = client.player.getX(), oldY = client.player.getY(), oldZ = client.player.getZ();
        client.player.setPos(targetImageX + 0.5, FIXTURE_Y + 2.5, 0.5);
        ItemStack lodestone = client.player.getInventory().getItem(3);
        float first = calculate(client, lodestone, CompassAngleState.CompassTarget.LODESTONE, 7);
        float second = calculate(client, lodestone, CompassAngleState.CompassTarget.LODESTONE, 71);
        client.player.setPos(oldX, oldY, oldZ);
        return circularDistance(first, second) > 0.01F;
    }

    private float calculate(Minecraft client, ItemStack stack, CompassAngleState.CompassTarget target, int seed) {
        CompassAngleState state = new CompassAngleState(false, target);
        return ((CompassAngleStateFixtureAccessor) (Object) state)
                .ringworld$calculate(stack, client.level, seed, client.player);
    }

    private static float circularDistance(float a, float b) {
        float delta = Math.abs(a - b);
        return Math.min(delta, 1.0F - delta);
    }

    private boolean mapsReady(Minecraft client) {
        boolean ready = client.player.getInventory().getItem(0).get(DataComponents.MAP_ID) != null
                && client.player.getInventory().getItem(1).get(DataComponents.MAP_ID) != null
                && MapItem.getSavedData(client.player.getInventory().getItem(0), client.level) != null
                && MapItem.getSavedData(client.player.getInventory().getItem(1), client.level) != null;
        if (ready) RingWorldMod.LOGGER.info("[map-compass-capture] client maps ready slot0={} center0={} slot1={} center1={}",
                client.player.getInventory().getItem(0).get(DataComponents.MAP_ID),
                MapItem.getSavedData(client.player.getInventory().getItem(0), client.level).centerX,
                client.player.getInventory().getItem(1).get(DataComponents.MAP_ID),
                MapItem.getSavedData(client.player.getInventory().getItem(1), client.level).centerX);
        return ready;
    }

    private boolean settled() { return ++stageTicks >= SETTLE_TICKS; }

    private static void select(Minecraft client, int slot) {
        client.player.getInventory().setSelectedSlot(slot);
        client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        client.setScreen(null);
    }

    private static void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1,
                message -> RingWorldMod.LOGGER.info("[map-compass-capture] screenshot {}: {}", name, message.getString()));
    }

    private boolean finish(Minecraft client, boolean passed, String detail) {
        stage = Integer.MAX_VALUE;
        RingWorldMod.LOGGER.info("[map-compass-capture] {}: {}", passed ? "PASS" : "FAIL", detail);
        client.stop();
        return true;
    }
}
