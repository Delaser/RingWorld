package dev.ringworld.server;

import com.mojang.datafixers.util.Either;
import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/** Additional real-client lifecycle and stateful-block seam regression. */
final class RingWorldExtendedMultiplayerTest {
    private static final int TIMEOUT_TICKS = 1_200;

    private static int stage;
    private static int ticks;
    private static boolean baselinePassed;
    private static boolean serverFixturePassed;
    private static boolean sleepStarted;
    private static boolean damageWakePassed;
    private static boolean bedDestroyedPassed;
    private static boolean deathObserved;
    private static boolean deathRespawnPassed;
    private static boolean netherPortalPassed;
    private static boolean endPortalPassed;
    private static ServerPlayer preDeathPlayer;
    private static BlockPos overworldNetherPortal;

    private RingWorldExtendedMultiplayerTest() { }

    /** @return true once the expanded matrix has emitted its terminal result. */
    static boolean tick(ServerLevel world, RingGeometry geometry, ServerPlayer playerA,
                        ServerPlayer playerB, boolean priorPassed) {
        playerA = currentPlayer(world, "RingTesterA", playerA);
        playerB = currentPlayer(world, "RingTesterB", playerB);
        ticks++;
        switch (stage) {
            case 0 -> prepareFixture(world, geometry, playerA, playerB, priorPassed);
            case 1 -> awaitFixture(world, geometry, playerA, playerB);
            case 2 -> awaitSleep(world, geometry, playerA);
            case 3 -> awaitDamageWake(world, playerA);
            case 4 -> awaitBedDestruction(world, playerA);
            case 5 -> awaitDeath(world, geometry, playerA);
            case 6 -> startNetherPortal(world, geometry, playerA);
            case 7 -> awaitNetherAndReturn(world, playerA);
            case 8 -> awaitOverworldAndStartEnd(world, geometry, playerA);
            case 9 -> awaitEndAndReturn(world, playerA);
            case 10 -> awaitFinalOverworld(world, geometry, playerA);
            case 11 -> {
                finish(world, geometry, playerA, playerB);
                return true;
            }
            default -> throw new IllegalStateException("Unknown extended multiplayer stage " + stage);
        }
        return false;
    }

    private static void prepareFixture(ServerLevel world, RingGeometry geometry,
                                       ServerPlayer playerA, ServerPlayer playerB,
                                       boolean priorPassed) {
        if (playerA == null || playerB == null) return;
        baselinePassed = priorPassed;
        prepareCreativePlayer(playerA);
        prepareCreativePlayer(playerB);
        playerA.teleportTo(world, geometry.circumferenceBlocks() - 2.5, 120.0, 0.5,
                Set.<Relative>of(), 90.0f, 10.0f, false);
        playerB.teleportTo(world, 2.5, 120.0, 0.5,
                Set.<Relative>of(), -90.0f, 10.0f, false);

        BlockPos chest = chestPos();
        BlockPos lectern = lecternPos();
        world.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        world.setBlock(lectern, Blocks.LECTERN.defaultBlockState(), 3);
        LecternBlock.tryPlaceBook(null, world, lectern, world.getBlockState(lectern),
                new ItemStack(Items.WRITABLE_BOOK));

        int highX = geometry.circumferenceBlocks() - 1;
        world.setBlock(new BlockPos(highX, 120, -5), Blocks.AIR.defaultBlockState(), 3);
        world.setBlock(redstoneLampPos(), Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
        // Place the source second so the real neighbour-update path, including
        // its C-1 -> 0 alias, is what lights the lamp.
        world.setBlock(new BlockPos(highX, 120, -5), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);

        for (int xOffset = -2; xOffset <= 0; xOffset++) {
            int x = geometry.wrapBlockX(geometry.circumferenceBlocks() + xOffset);
            world.setBlock(new BlockPos(x, 119, 6), Blocks.STONE.defaultBlockState(), 3);
        }
        world.setBlock(new BlockPos(highX, 120, 5), Blocks.STONE.defaultBlockState(), 3);
        world.setBlock(new BlockPos(highX, 120, 7), Blocks.STONE.defaultBlockState(), 3);
        world.setBlock(new BlockPos(0, 120, 5), Blocks.STONE.defaultBlockState(), 3);
        world.setBlock(new BlockPos(0, 120, 7), Blocks.STONE.defaultBlockState(), 3);
        world.setBlock(new BlockPos(1, 120, 6), Blocks.STONE.defaultBlockState(), 3);
        world.setBlock(fluidDestination(), Blocks.AIR.defaultBlockState(), 3);
        BlockPos source = new BlockPos(highX, 120, 6);
        world.setBlock(source, Blocks.AIR.defaultBlockState(), 3);
        world.setBlock(source, Blocks.WATER.defaultBlockState(), 3);
        world.scheduleTick(source, Fluids.WATER, 1);

        world.setBlock(explosionTarget(), Blocks.STONE.defaultBlockState(), 3);
        world.explode(null, highX + 0.5, 124.5, 9.5, 2.5F,
                Level.ExplosionInteraction.BLOCK);

        RingWorldMod.LOGGER.info("[multiplayer-extended] fixture armed across canonical seam");
        advance(1);
    }

    private static void awaitFixture(ServerLevel world, RingGeometry geometry,
                                     ServerPlayer playerA, ServerPlayer playerB) {
        boolean serverChest = world.getBlockEntity(chestPos()) != null;
        boolean serverLectern = world.getBlockEntity(lecternPos()) instanceof LecternBlockEntity lectern
                && lectern.hasBook()
                && world.getBlockState(lecternPos()).getValue(LecternBlock.HAS_BOOK);
        boolean redstone = world.getBlockState(redstoneLampPos()).getOptionalValue(
                BlockStateProperties.LIT).orElse(false);
        boolean fluid = !world.getFluidState(new BlockPos(
                geometry.circumferenceBlocks() - 1, 120, 6)).isEmpty();
        boolean explosion = world.getBlockState(explosionTarget()).isAir();
        serverFixturePassed = serverChest && serverLectern && redstone && fluid && explosion;
        boolean clientsPassed = RingWorldMultiplayerTest.clientPassed("A", "extended_fixture")
                && RingWorldMultiplayerTest.clientPassed("B", "extended_fixture");
        if (serverFixturePassed && clientsPassed) {
            prepareBed(world, geometry, playerA);
            advance(2);
        } else if (ticks >= TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error(
                    "[multiplayer-extended] fixture result=false (chest={}, lectern={}, redstone={}, fluid={}, explosion={}, clientA={}, clientB={})",
                    serverChest, serverLectern, redstone, fluid, explosion,
                    RingWorldMultiplayerTest.clientPassed("A", "extended_fixture"),
                    RingWorldMultiplayerTest.clientPassed("B", "extended_fixture"));
            advance(11);
        }
    }

    private static void prepareBed(ServerLevel world, RingGeometry geometry, ServerPlayer playerA) {
        BlockPos foot = bedFoot();
        BlockPos head = bedHead();
        var base = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        world.setBlock(foot, base.setValue(BedBlock.PART, BedPart.FOOT), 3);
        world.setBlock(head, base.setValue(BedBlock.PART, BedPart.HEAD), 3);
        var clock = world.dimensionType().defaultClock()
                .orElseThrow(() -> new IllegalStateException("Overworld has no default clock"));
        // World clocks are monotonic in 26.1. A reused fixture may already be
        // beyond day zero, so move to the next night's 13,000-tick phase
        // instead of attempting to rewind the clock to absolute tick 13,000.
        long currentTicks = world.clockManager().getTotalTicks(clock);
        long nextNight = (Math.floorDiv(currentTicks, 24_000L) + 1L) * 24_000L + 13_000L;
        world.clockManager().setTotalTicks(clock, nextNight);
        prepareSurvivalPlayer(playerA);
        playerA.teleportTo(world, geometry.circumferenceBlocks() - 1.5, 120.0, -1.5,
                Set.<Relative>of(), 90.0f, 10.0f, false);
        Either<Player.BedSleepingProblem, net.minecraft.util.Unit> result = playerA.startSleepInBed(head);
        sleepStarted = result.right().isPresent();
        RingWorldMod.LOGGER.info("[multiplayer-extended] seam bed sleep start={} problem={} canonicalBed={}",
                sleepStarted, result.left().orElse(null), playerA.getSleepingPos().orElse(null));
    }

    private static void awaitSleep(ServerLevel world, RingGeometry geometry, ServerPlayer playerA) {
        if (playerA == null) return;
        boolean canonicalBed = playerA.getSleepingPos()
                .map(pos -> pos.equals(bedHead()) && pos.getX() >= 0
                        && pos.getX() < geometry.circumferenceBlocks())
                .orElse(false);
        if (ticks >= 20 && sleepStarted && canonicalBed
                && RingWorldMultiplayerTest.clientPassed("A", "bed_sleep")) {
            boolean damaged = playerA.hurtServer(world, world.damageSources().generic(), 1.0F);
            RingWorldMod.LOGGER.info("[multiplayer-extended] sleeping player damage applied={}", damaged);
            advance(3);
        } else if (ticks >= TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error("[multiplayer-extended] seam bed sleep result=false started={} canonicalBed={} client={}",
                    sleepStarted, canonicalBed,
                    RingWorldMultiplayerTest.clientPassed("A", "bed_sleep"));
            advance(11);
        }
    }

    private static void awaitDamageWake(ServerLevel world, ServerPlayer playerA) {
        if (playerA == null) return;
        damageWakePassed = !playerA.isSleeping()
                && RingWorldMultiplayerTest.clientPassed("A", "bed_damage_wake");
        if (ticks >= 20 && damageWakePassed) {
            world.setBlock(bedFoot(), Blocks.AIR.defaultBlockState(), 3);
            world.setBlock(bedHead(), Blocks.AIR.defaultBlockState(), 3);
            advance(4);
        } else if (ticks >= TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error("[multiplayer-extended] damage wake result=false sleeping={} client={}",
                    playerA.isSleeping(),
                    RingWorldMultiplayerTest.clientPassed("A", "bed_damage_wake"));
            advance(11);
        }
    }

    private static void awaitBedDestruction(ServerLevel world, ServerPlayer playerA) {
        if (playerA == null) return;
        bedDestroyedPassed = playerA.getSleepingPos().isEmpty()
                && RingWorldMultiplayerTest.clientPassed("A", "bed_destroyed");
        if (ticks >= 20 && bedDestroyedPassed) {
            preDeathPlayer = playerA;
            prepareSurvivalPlayer(playerA);
            playerA.kill(world);
            advance(5);
        } else if (ticks >= TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error("[multiplayer-extended] bed destruction result=false serverBed={} client={}",
                    playerA.getSleepingPos().orElse(null),
                    RingWorldMultiplayerTest.clientPassed("A", "bed_destroyed"));
            advance(11);
        }
    }

    private static void awaitDeath(ServerLevel world, RingGeometry geometry, ServerPlayer playerA) {
        deathObserved |= RingWorldMultiplayerTest.clientPassed("A", "death_seen");
        boolean replacement = playerA != null && playerA != preDeathPlayer && playerA.isAlive();
        boolean canonical = replacement && playerA.getX() >= 0.0
                && playerA.getX() < geometry.circumferenceBlocks();
        boolean clientRespawn = RingWorldMultiplayerTest.clientPassed("A", "death_respawn");
        if (deathObserved && replacement && canonical && clientRespawn) {
            deathRespawnPassed = true;
            RingWorldMod.LOGGER.info("[multiplayer-extended] death/respawn result=true x={} canonical=true",
                    playerA.getX());
            advance(6);
        } else if (ticks >= TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error(
                    "[multiplayer-extended] death/respawn result=false (deathSeen={}, replacement={}, canonical={}, clientRespawn={})",
                    deathObserved, replacement, canonical, clientRespawn);
            advance(11);
        }
    }

    private static void startNetherPortal(ServerLevel overworld, RingGeometry geometry,
                                          ServerPlayer playerA) {
        if (playerA == null || playerA.level() != overworld) return;
        prepareCreativePlayer(playerA);
        BlockPos requested = new BlockPos(geometry.circumferenceBlocks() - 8, 120, 12);
        Optional<net.minecraft.util.BlockUtil.FoundRectangle> created = overworld.getPortalForcer()
                .createPortal(requested, Direction.Axis.Z);
        if (created.isEmpty()) {
            RingWorldMod.LOGGER.error("[multiplayer-extended] Nether portal result=false reason=source-create");
            advance(11);
            return;
        }
        overworldNetherPortal = created.get().minCorner;
        playerA.teleportTo(overworld, overworldNetherPortal.getX() + 0.5,
                overworldNetherPortal.getY(), overworldNetherPortal.getZ() + 0.5,
                Set.<Relative>of(), playerA.getYRot(), playerA.getXRot(), false);
        Portal portal = (Portal) Blocks.NETHER_PORTAL;
        var transition = portal.getPortalDestination(overworld, playerA, overworldNetherPortal);
        if (transition == null) {
            RingWorldMod.LOGGER.error("[multiplayer-extended] Nether portal result=false reason=no-outbound-transition");
            advance(11);
            return;
        }
        playerA.teleport(transition);
        // Directly invoking the real portal transition bypasses the normal
        // inside-block processor that applies this cooldown. Without it, the
        // destination portal can return the player during the Nether level's
        // tick before the Overworld-owned harness observes the linked exit.
        playerA.setPortalCooldown();
        RingWorldMod.LOGGER.info("[multiplayer-extended] physical Nether portal outbound source={}",
                overworldNetherPortal);
        advance(7);
    }

    private static void awaitNetherAndReturn(ServerLevel overworld, ServerPlayer playerA) {
        if (playerA == null) return;
        if (playerA.level().dimension() == Level.NETHER
                && RingWorldMultiplayerTest.clientPassed("A", "nether_enter")) {
            ServerLevel nether = (ServerLevel) playerA.level();
            Optional<BlockPos> exit = nether.getPortalForcer().findClosestPortalPosition(
                    playerA.blockPosition(), true, nether.getWorldBorder());
            if (exit.isEmpty()) {
                RingWorldMod.LOGGER.error("[multiplayer-extended] Nether portal result=false reason=no-linked-exit");
                advance(11);
                return;
            }
            var transition = ((Portal) Blocks.NETHER_PORTAL)
                    .getPortalDestination(nether, playerA, exit.get());
            if (transition == null) {
                RingWorldMod.LOGGER.error("[multiplayer-extended] Nether portal result=false reason=no-return-transition");
                advance(11);
                return;
            }
            playerA.teleport(transition);
            playerA.setPortalCooldown();
            advance(8);
        } else if (ticks >= TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error("[multiplayer-extended] Nether portal result=false reason=enter-timeout dimension={} client={}",
                    playerA.level().dimension().identifier(),
                    RingWorldMultiplayerTest.clientPassed("A", "nether_enter"));
            advance(11);
        }
    }

    private static void awaitOverworldAndStartEnd(ServerLevel overworld, RingGeometry geometry,
                                                   ServerPlayer playerA) {
        if (playerA == null) return;
        boolean returned = playerA.level() == overworld
                && playerA.getX() >= 0.0 && playerA.getX() < geometry.circumferenceBlocks()
                && Math.abs(geometry.shortestCircumferenceDelta(
                        overworldNetherPortal.getX(), playerA.getX())) < 16.0
                && RingWorldMultiplayerTest.clientPassed("A", "nether_return");
        if (returned) {
            netherPortalPassed = true;
            double netherReturnX = playerA.getX();
            BlockPos endPortal = new BlockPos(geometry.circumferenceBlocks() - 12, 120, 16);
            overworld.setBlock(endPortal, Blocks.END_PORTAL.defaultBlockState(), 3);
            playerA.teleportTo(overworld, endPortal.getX() + 0.5, endPortal.getY() + 1.0,
                    endPortal.getZ() + 0.5, Set.<Relative>of(), 0.0F, 0.0F, false);
            var transition = ((Portal) Blocks.END_PORTAL)
                    .getPortalDestination(overworld, playerA, endPortal);
            if (transition == null) {
                RingWorldMod.LOGGER.error("[multiplayer-extended] End portal result=false reason=no-outbound-transition");
                advance(11);
                return;
            }
            playerA.teleport(transition);
            playerA.setPortalCooldown();
            RingWorldMod.LOGGER.info("[multiplayer-extended] physical Nether portal result=true returnX={}; End outbound armed",
                    netherReturnX);
            advance(9);
        } else if (ticks >= TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error("[multiplayer-extended] Nether portal result=false reason=return-timeout dimension={} x={} client={}",
                    playerA.level().dimension().identifier(), playerA.getX(),
                    RingWorldMultiplayerTest.clientPassed("A", "nether_return"));
            advance(11);
        }
    }

    private static void awaitEndAndReturn(ServerLevel overworld, ServerPlayer playerA) {
        if (playerA == null) return;
        if (playerA.level().dimension() == Level.END
                && RingWorldMultiplayerTest.clientPassed("A", "end_enter")) {
            ServerLevel end = (ServerLevel) playerA.level();
            BlockPos returnPortal = playerA.blockPosition();
            end.setBlock(returnPortal, Blocks.END_PORTAL.defaultBlockState(), 3);
            var transition = ((Portal) Blocks.END_PORTAL)
                    .getPortalDestination(end, playerA, returnPortal);
            if (transition == null) {
                RingWorldMod.LOGGER.error("[multiplayer-extended] End portal result=false reason=no-return-transition");
                advance(11);
                return;
            }
            playerA.teleport(transition);
            playerA.setPortalCooldown();
            advance(10);
        } else if (ticks >= TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error("[multiplayer-extended] End portal result=false reason=enter-timeout dimension={} client={}",
                    playerA.level().dimension().identifier(),
                    RingWorldMultiplayerTest.clientPassed("A", "end_enter"));
            advance(11);
        }
    }

    private static void awaitFinalOverworld(ServerLevel overworld, RingGeometry geometry,
                                             ServerPlayer playerA) {
        if (playerA == null) return;
        boolean returned = playerA.level() == overworld
                && playerA.getX() >= 0.0 && playerA.getX() < geometry.circumferenceBlocks()
                && RingWorldMultiplayerTest.clientPassed("A", "end_return");
        if (returned) {
            endPortalPassed = true;
            RingWorldMod.LOGGER.info("[multiplayer-extended] physical End portal result=true returnX={} canonical=true",
                    playerA.getX());
            advance(11);
        } else if (ticks >= TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error("[multiplayer-extended] End portal result=false reason=return-timeout dimension={} x={} client={}",
                    playerA.level().dimension().identifier(), playerA.getX(),
                    RingWorldMultiplayerTest.clientPassed("A", "end_return"));
            advance(11);
        }
    }

    private static void finish(ServerLevel world, RingGeometry geometry,
                               ServerPlayer playerA, ServerPlayer playerB) {
        boolean clientFixture = RingWorldMultiplayerTest.clientPassed("A", "extended_fixture")
                && RingWorldMultiplayerTest.clientPassed("B", "extended_fixture");
        boolean clientLifecycle = RingWorldMultiplayerTest.clientPassed("A", "bed_sleep")
                && RingWorldMultiplayerTest.clientPassed("A", "bed_damage_wake")
                && RingWorldMultiplayerTest.clientPassed("A", "bed_destroyed")
                && RingWorldMultiplayerTest.clientPassed("A", "death_seen")
                && RingWorldMultiplayerTest.clientPassed("A", "death_respawn")
                && RingWorldMultiplayerTest.clientPassed("A", "nether_enter")
                && RingWorldMultiplayerTest.clientPassed("A", "nether_return")
                && RingWorldMultiplayerTest.clientPassed("A", "end_enter")
                && RingWorldMultiplayerTest.clientPassed("A", "end_return");
        boolean canonicalPlayers = playerA != null && playerB != null
                && playerA.getX() >= 0.0 && playerA.getX() < geometry.circumferenceBlocks()
                && playerB.getX() >= 0.0 && playerB.getX() < geometry.circumferenceBlocks();
        boolean passed = baselinePassed && serverFixturePassed && damageWakePassed
                && bedDestroyedPassed && deathObserved && deathRespawnPassed
                && netherPortalPassed && endPortalPassed && clientFixture && clientLifecycle
                && canonicalPlayers;
        RingWorldMod.LOGGER.info(
                "[multiplayer] full scenario result={} (baseline={}, fixture={}, damageWake={}, bedDestroyed={}, deathRespawn={}, netherPortal={}, endPortal={}, clientFixture={}, clientLifecycle={}, canonicalPlayers={})",
                passed, baselinePassed, serverFixturePassed, damageWakePassed,
                bedDestroyedPassed, deathRespawnPassed, netherPortalPassed, endPortalPassed,
                clientFixture, clientLifecycle, canonicalPlayers);
    }

    private static void prepareCreativePlayer(ServerPlayer player) {
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void prepareSurvivalPlayer(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void advance(int nextStage) {
        stage = nextStage;
        ticks = 0;
    }

    private static ServerPlayer currentPlayer(ServerLevel world, String name, ServerPlayer current) {
        if (current != null) return current;
        for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
            if (player.getName().getString().equals(name)) return player;
        }
        return null;
    }

    private static BlockPos chestPos() { return new BlockPos(0, 120, -3); }
    private static BlockPos lecternPos() { return new BlockPos(1, 120, -3); }
    private static BlockPos redstoneLampPos() { return new BlockPos(0, 120, -5); }
    private static BlockPos fluidDestination() { return new BlockPos(0, 120, 6); }
    private static BlockPos explosionTarget() { return new BlockPos(0, 124, 9); }
    private static BlockPos bedFoot() { return new BlockPos(0, 120, -1); }
    private static BlockPos bedHead() { return new BlockPos(1, 120, -1); }
}
