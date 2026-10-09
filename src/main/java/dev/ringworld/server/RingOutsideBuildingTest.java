package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingBuildingSettings;
import dev.ringworld.world.RingGeometry;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in real placement/save/reopen proof; does not change the full release fixture. */
final class RingOutsideBuildingTest {
    private static final boolean RESUME = Boolean.getBoolean("ringworld.outsideBuildingResume");
    private static final Map<String, Boolean> results = new HashMap<>();
    private static int stage;
    private static int ticks;
    private static boolean armed;
    static void record(String role, String phase, boolean passed) { results.put(role + ':' + phase, passed); }

    static void tick(ServerLevel world, RingGeometry geometry) {
        if (++ticks > 2400) throw new IllegalStateException("Outside building watchdog stage=" + stage + " results=" + results);
        ServerPlayer a = world.players().stream().filter(p -> p.getName().getString().equals("RingTesterA")).findFirst().orElse(null);
        ServerPlayer b = world.players().stream().filter(p -> p.getName().getString().equals("RingTesterB")).findFirst().orElse(null);
        if (a == null || b == null || !passed("A", "client_ready") || !passed("B", "client_ready")) return;
        int edge = stage == 1 ? geometry.minWidthZ() : geometry.maxWidthZ();
        int outside = edge + (stage == 1 ? -1 : 1);
        int x = stage == 4 || RESUME ? 64 + (stage == 1 ? 16 : 0) : 64 + stage * 16;
        if (!armed) {
            if (stage == 0) {
                require(RingBuildingSettings.get(world).outsideBuilding() == !RESUME, "default/reopened saved policy");
                world.getServer().getCommands().performPrefixedCommand(world.getServer().createCommandSourceStack(), "op RingTesterA");
            }
            if (!RESUME) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = 0; dz <= 2; dz++)
                    world.setBlock(new BlockPos(x + dx, 120, edge + (stage == 1 ? dz : -dz)), Blocks.STONE.defaultBlockState(), 3);
            } else {
                world.getChunk(4, (geometry.maxWidthZ() + 1) >> 4);
                world.getChunk(5, (geometry.minWidthZ() - 1) >> 4);
                require(world.getBlockState(new BlockPos(64, 120, geometry.maxWidthZ() + 1)).is(Blocks.STONE), "positive build persisted");
                require(world.getBlockState(new BlockPos(80, 120, geometry.minWidthZ() - 1)).is(Blocks.STONE), "negative build persisted");
            }
            if (stage == 5 && !RESUME) {
                x = 0;
                for (int dx = -2; dx <= 2; dx++)
                    world.setBlock(new BlockPos(dx, 120, outside), Blocks.STONE.defaultBlockState(), 3);
            }
            a.setGameMode(GameType.CREATIVE); b.setGameMode(GameType.CREATIVE);
            boolean collision = stage == 4 || RESUME;
            a.setNoGravity(!collision); b.setNoGravity(true);
            a.getAbilities().flying = !collision; b.getAbilities().flying = true;
            a.onUpdateAbilities(); b.onUpdateAbilities();
            a.setDeltaMovement(Vec3.ZERO); b.setDeltaMovement(Vec3.ZERO);
            a.getInventory().setItem(0, new ItemStack(RingWorldVanillaFixtureRegistries.item("stone"), 64));
            a.containerMenu.broadcastChanges();
            a.teleportTo(world, stage == 5 && !RESUME ? geometry.circumferenceBlocks() - 0.5 : x + 0.5,
                    121, collision || stage == 5 ? outside + 0.5 : edge + (stage == 1 ? 1.5 : -0.5),
                    Set.<Relative>of(), 0, 45, false);
            b.teleportTo(world, x + 4.5, 121, edge + (stage == 1 ? 1.5 : -0.5), Set.<Relative>of(), 90, 20, false);
            RingTerrainAtlasServer.sendBuildingSettings(a); RingTerrainAtlasServer.sendBuildingSettings(b);
            armed = true; ticks = 0;
            RingWorldMod.LOGGER.info("[outside-building] armed resume={} stage={}", RESUME, stage);
        }
        if (stage == 3 && ticks == 30 && !RESUME) {
            require(!RingBuildingSettings.get(world).outsideBuilding(), "off command applied");
            ItemStack stone = new ItemStack(RingWorldVanillaFixtureRegistries.item("stone"));
            BlockPos anchor = new BlockPos(x, 120, edge);
            b.gameMode.useItemOn(b, world, stone, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(anchor), Direction.SOUTH, anchor, false));
            require(world.getBlockState(new BlockPos(x, 120, outside)).isAir(), "server rejects exterior block placement");
            b.setYRot(0);
            b.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RingWorldVanillaFixtureRegistries.item("red_bed")));
            b.gameMode.useItemOn(b, world, new ItemStack(RingWorldVanillaFixtureRegistries.item("red_bed")),
                    InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(anchor), Direction.UP, anchor, false));
            require(world.getBlockState(anchor.above()).isAir(), "crossing bed rejected before foot placement");
            BucketItem water = (BucketItem)RingWorldVanillaFixtureRegistries.item("water_bucket");
            require(!water.emptyContents(b, world, new BlockPos(x, 120, outside), null), "server rejects direct bucket placement");
            RingWorldMod.LOGGER.info("[outside-building] server block/bed/bucket rejection PASS");
        }
        if (ticks < 60 || !passed("A", "outside_" + stage) || !passed("B", "outside_" + stage)) return;
        if (RESUME || stage == 4) require(Math.abs(a.getY() - 121) < 0.1 && a.onGround(), "exterior platform collision");
        if (!RESUME && (stage == 0 || stage == 1))
            require(world.getBlockState(new BlockPos(x, 120, outside)).is(Blocks.STONE), "real player placement replicated");
        if (!RESUME && stage == 3) require(!RingBuildingSettings.get(world).outsideBuilding(), "unauthorised requests cannot enable");
        if (!RESUME && stage == 5) {
            require(a.getX() < 4, "natural X fold remains canonical");
            require(world.getBlockState(new BlockPos(0, 121, outside)).is(Blocks.STONE), "exterior seam placement");
        }
        RingWorldMod.LOGGER.info("[outside-building] stage={} result=true resume={}", stage, RESUME);
        stage++; armed = false; ticks = 0;
        if (stage == (RESUME ? 2 : 6)) {
            RingBuildingSettings.get(world).setOutsideBuilding(false);
            RingWorldMod.LOGGER.info("[outside-building] result=true resume={} saving and stopping", RESUME);
            world.getServer().halt(false);
        }
    }
    private static boolean passed(String role, String phase) { return Boolean.TRUE.equals(results.get(role + ':' + phase)); }
    private static void require(boolean condition, String reason) { if (!condition) throw new IllegalStateException("Outside building: " + reason); }
}
