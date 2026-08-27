package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import dev.ringworld.server.RingWorldVanillaFixtureRegistries;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Isolated real-renderer proof that rigid block/entity models share the curved
 * terrain pose instead of sliding vertically as their flat distance changes.
 */
public final class CurvedObjectCaptureClient {
    public static final String ENABLE_PROPERTY = "ringworld.curvedObjectCapture";
    private static final int SETTLE_TICKS = 160;
    private static final int MAX_CAPTURE_TICKS = 1_200;
    private boolean focusPolicyApplied;
    private boolean fixtureRequested;
    private int stage;
    private int settleTicks;
    private int completionTicks;
    private boolean worldScreenOpened;
    private boolean worldStarted;
    private int captureTicks;

    public boolean startWorldIfEnabled(Minecraft client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || client.level != null || worldStarted) {
            return false;
        }
        if (!worldScreenOpened) {
            if (!(RingMinecraftClientAccess.screen(client) instanceof TitleScreen)) return true;
            CreateWorldScreen.openFresh(client, () -> worldScreenOpened = false);
            worldScreenOpened = true;
            return true;
        }
        if (RingMinecraftClientAccess.screen(client) instanceof CreateWorldScreen screen) {
            WorldCreationUiState creator = screen.getUiState();
            creator.setName("RingWorld Curved Object Capture");
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
        applyFocusPolicy(client);
        if (client.player == null || client.level == null) return true;
        if (RingMinecraftClientAccess.screen(client) instanceof PauseScreen) RingMinecraftClientAccess.setScreen(client, null);
        if (RingMinecraftClientAccess.screen(client) != null) return true;

        if (++captureTicks > MAX_CAPTURE_TICKS) {
            RingWorldMod.LOGGER.error(
                    "[curved-object-capture] result=FAIL, fixture chunks never became render-ready");
            client.stop();
            return true;
        }

        if (!fixtureRequested) {
            fixtureRequested = true;
            requestFixture(client);
            return true;
        }
        if (stage >= 2) {
            if (++completionTicks >= 20) {
                RingWorldMod.LOGGER.info("[curved-object-capture] result=PASS, captures complete");
                client.stop();
            }
            return true;
        }

        if (stage == 0 && client.player.getX() < 2.0) {
            client.player.setYRot(-90.0F);
            client.player.setXRot(0.0F);
            if (++settleTicks < SETTLE_TICKS || !fixtureIsPresent(client)
                    || !client.levelRenderer.hasRenderedAllSections()) return true;
            capture(client, "ringworld-curved-objects-far.png", "far");
            settleTicks = 0;
            stage = 1;
            client.getConnection().sendCommand("tp @s 32.5 122 0.5 -90 8");
            return true;
        }
        if (stage == 1 && client.player.getX() > 31.0) {
            client.player.setYRot(-90.0F);
            client.player.setXRot(8.0F);
            if (++settleTicks < SETTLE_TICKS || !fixtureIsPresent(client)
                    || !client.levelRenderer.hasRenderedAllSections()) return true;
            capture(client, "ringworld-curved-objects-near.png", "near");
            stage = 2;
        }
        return true;
    }

    private static boolean fixtureIsPresent(Minecraft client) {
        return client.level.getBlockState(new BlockPos(40, 120, -2)).is(RingWorldVanillaFixtureRegistries.block("chest"))
                && client.level.getBlockEntity(new BlockPos(40, 120, -2)) != null
                && client.level.getBlockState(new BlockPos(48, 120, 0)).is(RingWorldVanillaFixtureRegistries.block("lectern"))
                && client.level.getBlockEntity(new BlockPos(48, 120, 0)) != null
                && client.level.getBlockState(new BlockPos(56, 120, 2)).is(RingWorldVanillaFixtureRegistries.block("ender_chest"))
                && client.level.getBlockEntity(new BlockPos(56, 120, 2)) != null
                && client.level.getBlockState(new BlockPos(52, 120, -2)).is(RingWorldVanillaFixtureRegistries.block("oak_sign"))
                && client.level.getBlockEntity(new BlockPos(52, 120, -2)) != null
                && client.level.getBlockState(new BlockPos(60, 120, 0))
                        .is(RingWorldVanillaFixtureRegistries.block("red_bed"))
                && client.level.getBlockEntity(new BlockPos(60, 120, 0)) != null
                && client.level.getBlockState(new BlockPos(62, 120, 2)).is(RingWorldVanillaFixtureRegistries.block("shulker_box"))
                && client.level.getBlockEntity(new BlockPos(62, 120, 2)) != null
                && client.level.getBlockState(new BlockPos(66, 120, 0)).is(RingWorldVanillaFixtureRegistries.block("white_banner"))
                && client.level.getBlockEntity(new BlockPos(66, 120, 0)) != null;
    }

    private static void requestFixture(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            RingWorldMod.LOGGER.error("[curved-object-capture] no integrated server");
            return;
        }
        var playerId = client.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !(player.level() instanceof ServerLevel world)) return;
            createFixture(world);
            player.teleportTo(world, 0.5, 122.0, 0.5,
                    java.util.Set.of(), -90.0F, 0.0F, false);
            RingWorldMod.LOGGER.info("[curved-object-capture] fixture ready");
        });
    }

    private static void createFixture(ServerLevel world) {
        for (int x = 0; x <= 88; x++) {
            for (int z = -4; z <= 4; z++) {
                world.setBlock(new BlockPos(x, 119, z),
                        (x + z & 1) == 0
                                ? RingWorldVanillaFixtureRegistries.block("stone_bricks").defaultBlockState()
                                : RingWorldVanillaFixtureRegistries.block("mossy_stone_bricks").defaultBlockState(), 3);
                for (int y = 120; y <= 140; y++) {
                    world.setBlock(new BlockPos(x, y, z), RingWorldVanillaFixtureRegistries.block("air").defaultBlockState(), 3);
                }
            }
        }
        world.setBlock(new BlockPos(40, 120, -2), RingWorldVanillaFixtureRegistries.block("chest").defaultBlockState(), 3);
        BlockPos lecternPos = new BlockPos(48, 120, 0);
        world.setBlock(lecternPos, RingWorldVanillaFixtureRegistries.block("lectern").defaultBlockState()
                .setValue(LecternBlock.HAS_BOOK, true), 3);
        if (world.getBlockEntity(lecternPos) instanceof LecternBlockEntity lectern) {
            lectern.setBook(new ItemStack(RingWorldVanillaFixtureRegistries.item("writable_book")));
            lectern.setChanged();
            world.sendBlockUpdated(lecternPos, world.getBlockState(lecternPos),
                    world.getBlockState(lecternPos), 3);
        }
        world.setBlock(new BlockPos(52, 120, -2), RingWorldVanillaFixtureRegistries.block("oak_sign").defaultBlockState(), 3);
        world.setBlock(new BlockPos(56, 120, 2), RingWorldVanillaFixtureRegistries.block("ender_chest").defaultBlockState(), 3);
        var bedState = RingWorldVanillaFixtureRegistries.block("red_bed")
                .defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        world.setBlock(new BlockPos(60, 120, 0), bedState.setValue(BedBlock.PART, BedPart.FOOT), 2);
        world.setBlock(new BlockPos(61, 120, 0), bedState.setValue(BedBlock.PART, BedPart.HEAD), 2);
        world.setBlock(new BlockPos(62, 120, 2), RingWorldVanillaFixtureRegistries.block("shulker_box").defaultBlockState(), 3);
        world.setBlock(new BlockPos(66, 120, 0), RingWorldVanillaFixtureRegistries.block("white_banner").defaultBlockState(), 3);

        var copperGolem = RingWorldVanillaFixtureRegistries
                .entityType("copper_golem", CopperGolem.class).create(world, EntitySpawnReason.COMMAND);
        if (copperGolem != null) {
            copperGolem.setPos(64.5, 120.0, -2.0);
            copperGolem.setNoAi(true);
            copperGolem.setPersistenceRequired();
            world.addFreshEntity(copperGolem);
        }
        ItemEntity item = new ItemEntity(world, 68.5, 120.25, 0.0,
                new ItemStack(RingWorldVanillaFixtureRegistries.item("diamond")));
        item.setNoGravity(true);
        item.setNeverPickUp();
        world.addFreshEntity(item);
        var boat = RingWorldVanillaFixtureRegistries.entityType("oak_boat",
                net.minecraft.world.entity.vehicle.boat.Boat.class).create(world, EntitySpawnReason.COMMAND);
        if (boat != null) {
            boat.setPos(72.5, 120.15, 2.0);
            boat.setNoGravity(true);
            world.addFreshEntity(boat);
        }
        var cow = RingWorldVanillaFixtureRegistries.entityType("cow", Cow.class)
                .create(world, EntitySpawnReason.COMMAND);
        if (cow != null) {
            cow.setPos(76.5, 120.0, -2.0);
            cow.setNoAi(true);
            cow.setPersistenceRequired();
            world.addFreshEntity(cow);
        }
        var zombie = RingWorldVanillaFixtureRegistries.entityType("zombie", Zombie.class)
                .create(world, EntitySpawnReason.COMMAND);
        if (zombie != null) {
            zombie.setPos(80.5, 120.0, 2.0);
            zombie.setNoAi(true);
            zombie.setInvulnerable(true);
            zombie.setPersistenceRequired();
            world.addFreshEntity(zombie);
        }
    }

    private static void capture(Minecraft client, String name, String distance) {
        RingMinecraftClientAccess.grabScreenshot(client.gameDirectory, name, RingMinecraftClientAccess.mainRenderTarget(client), 1,
                message -> RingWorldMod.LOGGER.info(
                        "[curved-object-capture] {} screenshot: {}",
                        distance, message.getString()));
    }

    private void applyFocusPolicy(Minecraft client) {
        if (focusPolicyApplied) return;
        client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
        client.options.pauseOnLostFocus = false;
        focusPolicyApplied = true;
    }
}
