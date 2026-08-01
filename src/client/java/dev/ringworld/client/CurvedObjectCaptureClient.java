package dev.ringworld.client;

import dev.ringworld.RingWorldMod;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

/**
 * Isolated real-renderer proof that block entities share the curved terrain
 * pose instead of sliding vertically as their flat distance changes.
 */
final class CurvedObjectCaptureClient {
    static final String ENABLE_PROPERTY = "ringworld.curvedObjectCapture";
    private static final int SETTLE_TICKS = 160;
    private boolean focusPolicyApplied;
    private boolean fixtureRequested;
    private int stage;
    private int settleTicks;
    private int completionTicks;

    boolean tick(Minecraft client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return false;
        applyFocusPolicy(client);
        // Let the ordinary test-mode creator build the isolated world first.
        if (client.player == null || client.level == null) return false;
        if (client.screen instanceof PauseScreen) client.setScreen(null);
        if (client.screen != null) return true;

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
            if (++settleTicks < SETTLE_TICKS || !client.levelRenderer.hasRenderedAllSections()) return true;
            capture(client, "ringworld-curved-objects-far.png", "far");
            settleTicks = 0;
            stage = 1;
            client.getConnection().sendCommand("tp @s 32.5 122 0.5 -90 8");
            return true;
        }
        if (stage == 1 && client.player.getX() > 31.0) {
            client.player.setYRot(-90.0F);
            client.player.setXRot(8.0F);
            if (++settleTicks < SETTLE_TICKS || !client.levelRenderer.hasRenderedAllSections()) return true;
            capture(client, "ringworld-curved-objects-near.png", "near");
            stage = 2;
        }
        return true;
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
        for (int x = 0; x <= 80; x++) {
            for (int z = -4; z <= 4; z++) {
                world.setBlock(new BlockPos(x, 119, z),
                        (x + z & 1) == 0
                                ? Blocks.STONE_BRICKS.defaultBlockState()
                                : Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 3);
                for (int y = 120; y <= 140; y++) {
                    world.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        world.setBlock(new BlockPos(40, 120, -2), Blocks.CHEST.defaultBlockState(), 3);
        BlockPos lecternPos = new BlockPos(48, 120, 0);
        world.setBlock(lecternPos, Blocks.LECTERN.defaultBlockState()
                .setValue(LecternBlock.HAS_BOOK, true), 3);
        if (world.getBlockEntity(lecternPos) instanceof LecternBlockEntity lectern) {
            lectern.setBook(new ItemStack(Items.WRITABLE_BOOK));
            lectern.setChanged();
            world.sendBlockUpdated(lecternPos, world.getBlockState(lecternPos),
                    world.getBlockState(lecternPos), 3);
        }
        world.setBlock(new BlockPos(56, 120, 2), Blocks.ENDER_CHEST.defaultBlockState(), 3);

        var copperGolem = EntityType.COPPER_GOLEM.create(world, EntitySpawnReason.COMMAND);
        if (copperGolem != null) {
            copperGolem.setPos(64.5, 120.0, -2.0);
            copperGolem.setNoAi(true);
            copperGolem.setPersistenceRequired();
            world.addFreshEntity(copperGolem);
        }
        ItemEntity item = new ItemEntity(world, 68.5, 120.25, 0.0,
                new ItemStack(Items.DIAMOND));
        item.setNoGravity(true);
        item.setNeverPickUp();
        world.addFreshEntity(item);
        var boat = EntityType.OAK_BOAT.create(world, EntitySpawnReason.COMMAND);
        if (boat != null) {
            boat.setPos(72.5, 120.15, 2.0);
            boat.setNoGravity(true);
            world.addFreshEntity(boat);
        }
    }

    private static void capture(Minecraft client, String name, String distance) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), 1,
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
