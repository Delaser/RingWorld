package dev.ringworld.server;

import com.mojang.datafixers.util.Either;
import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingPortalDestinationBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Additional real-client lifecycle and stateful-block seam regression. */
final class RingWorldExtendedMultiplayerTest {
    private static final int TIMEOUT_TICKS = 1_200;
    private static final String NAVIGATOR_TAG = "ringworld_extended_seam_navigator";

    private static int stage;
    private static int ticks;
    private static boolean baselinePassed;
    private static boolean serverFixturePassed;
    private static boolean aliasRecoveryPassed;
    private static boolean sleepAttempted;
    private static boolean sleepStarted;
    private static ServerPlayer sleepingReconnectBaseline;
    private static boolean sleepingDisconnectObserved;
    private static boolean sleepingReconnectPassed;
    private static boolean sleepRestartAttempted;
    private static boolean sleepRestarted;
    private static int sleepRestartTick;
    private static boolean damageWakePassed;
    private static boolean bedDestroyedPassed;
    private static boolean deathObserved;
    private static boolean deathRespawnPassed;
    private static boolean netherPortalPassed;
    private static boolean netherPortalRoutingPassed;
    private static boolean endPortalPassed;
    private static boolean weatherPassed;
    private static boolean outboundPortalWaitPassed;
    private static long portalWaitStarted;
    private static int expectedPortalWait;
    private static boolean navigationStarted;
    private static ServerPlayer preDeathPlayer;
    private static BlockPos overworldNetherPortal;
    private static BlockPos expectedOverworldPortalReturn;
    private static Zombie seamNavigator;
    private static final RingMultiplayerPhaseTelemetry COLD_TELEMETRY = new RingMultiplayerPhaseTelemetry();
    private static RingMultiplayerReadinessGate postEndWeatherGate;
    private static boolean weatherArmed;

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
            case 7 -> awaitNetherAndReturn(world, geometry, playerA);
            case 8 -> awaitOverworldAndStartEnd(world, geometry, playerA);
            case 9 -> awaitEndAndReturn(world, playerA);
            case 10 -> awaitFinalOverworld(world, geometry, playerA);
            case 11 -> awaitWeather(world, geometry, playerA, playerB);
            case 12 -> {
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
        var weather = world.getWeatherData();
        weather.setClearWeatherTime(6_000);
        weather.setRainTime(0);
        weather.setThunderTime(0);
        weather.setRaining(false);
        weather.setThundering(false);
        world.setRainLevel(0.0F);
        world.setThunderLevel(0.0F);
        COLD_TELEMETRY.record("extended-fixture-before", world);
        playerA.teleportTo(world, geometry.circumferenceBlocks() - 2.5, 120.0, 0.5,
                Set.<Relative>of(), 90.0f, 10.0f, false);
        playerB.teleportTo(world, 2.5, 120.0, 0.5,
                Set.<Relative>of(), -90.0f, 10.0f, false);

        BlockPos chest = chestPos();
        BlockPos chestHigh = chestHighPos(geometry);
        BlockPos lectern = lecternPos();
        world.setBlock(chestHigh, Blocks.AIR.defaultBlockState(), 3);
        world.setBlock(chest, Blocks.AIR.defaultBlockState(), 3);
        BlockState highChestState = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockState lowChestState = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT);
        world.setBlock(chestHigh, highChestState, 3);
        world.setBlock(chest, lowChestState, 3);
        seedDoubleChest(world, geometry);
        RingWorldMod.LOGGER.info("[multiplayer-extended] double chest prepared {}",
                doubleChestDiagnostic(world, geometry));
        aliasRecoveryPassed = verifyAliasCollisionRecovery(world, geometry);
        RingWorldMod.LOGGER.info(
                "[multiplayer-extended] alias block-entity recovery policy result={}",
                aliasRecoveryPassed);
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
        world.setBlock(new BlockPos(geometry.circumferenceBlocks() - 2, 120, 6),
                Blocks.STONE.defaultBlockState(), 3);
        world.setBlock(new BlockPos(1, 120, 6), Blocks.STONE.defaultBlockState(), 3);
        world.setBlock(new BlockPos(highX, 121, 6), Blocks.STONE.defaultBlockState(), 3);
        world.setBlock(new BlockPos(0, 121, 6), Blocks.STONE.defaultBlockState(), 3);
        world.setBlock(fluidDestination(), Blocks.AIR.defaultBlockState(), 3);
        BlockPos source = new BlockPos(highX, 120, 6);
        world.setBlock(source, Blocks.AIR.defaultBlockState(), 3);
        world.setBlock(source, Blocks.WATER.defaultBlockState(), 3);
        world.scheduleTick(source, Fluids.WATER, 1);

        armSeamNavigator(world, geometry);

        prepareCrossSeamExplosionCell(world, geometry);
        world.explode(null, highX + 0.5, 124.5, 9.5, 2.5F,
                Level.ExplosionInteraction.BLOCK);
        COLD_TELEMETRY.record("extended-fixture-after", world);

        RingWorldMod.LOGGER.info("[multiplayer-extended] fixture armed across canonical seam");
        advance(1);
    }

    private static void awaitFixture(ServerLevel world, RingGeometry geometry,
                                     ServerPlayer playerA, ServerPlayer playerB) {
        boolean serverChest = doubleChestValid(world, geometry);
        boolean serverLectern = world.getBlockEntity(lecternPos()) instanceof LecternBlockEntity lectern
                && lectern.hasBook()
                && world.getBlockState(lecternPos()).getValue(LecternBlock.HAS_BOOK);
        boolean redstone = world.getBlockState(redstoneLampPos()).getOptionalValue(
                BlockStateProperties.LIT).orElse(false);
        // A sealed two-cell trough clears X=0 before placing its only water
        // source at C-1.
        // Observing water here proves the scheduled flow crossed the canonical seam;
        // observing C-1 would only re-observe the source block.
        boolean waterReachedDestination = !world.getFluidState(fluidDestination()).isEmpty();
        boolean navigatorFolded = seamNavigator != null && seamNavigator.isAlive()
                && seamNavigator.getX() >= 0.0
                && seamNavigator.getX() < geometry.circumferenceBlocks() / 2.0;
        boolean navigatorPathDone = seamNavigator != null
                && seamNavigator.getNavigation().isDone();
        boolean navigatorReachedTarget = navigatorFolded
                && geometry.isWithinPeriodicBox(
                        seamNavigator.getX(), seamNavigator.getY(), seamNavigator.getZ(),
                        2.5, 120.0, 15.5, 1.75, 1.5, 1.75);
        boolean explosion = world.getBlockState(explosionTarget()).isAir();
        serverFixturePassed = serverChest && aliasRecoveryPassed && serverLectern
                && redstone && waterReachedDestination
                && navigationStarted && navigatorPathDone && navigatorReachedTarget && explosion;
        boolean clientsPassed = RingWorldMultiplayerTest.clientPassed("A", "extended_fixture")
                && RingWorldMultiplayerTest.clientPassed("B", "extended_fixture");
        if (serverFixturePassed && clientsPassed) {
            prepareBed(world, geometry, playerA);
            advance(2);
        } else if (ticks >= TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error(
                    "[multiplayer-extended] fixture result=false (doubleChest={}, aliasRecovery={}, lectern={}, redstone={}, waterReachedDestination={}, navigationStarted={}, navigatorFolded={}, navigatorPathDone={}, navigatorReachedTarget={}, navigatorX={}, navigatorZ={}, explosion={}, clientA={}, clientB={})",
                    serverChest, aliasRecoveryPassed, serverLectern, redstone,
                    waterReachedDestination, navigationStarted,
                    navigatorFolded, navigatorPathDone, navigatorReachedTarget,
                    seamNavigator == null ? Double.NaN : seamNavigator.getX(),
                    seamNavigator == null ? Double.NaN : seamNavigator.getZ(), explosion,
                    RingWorldMultiplayerTest.clientPassed("A", "extended_fixture"),
                    RingWorldMultiplayerTest.clientPassed("B", "extended_fixture"));
            advance(12);
        }
    }

    private static void prepareBed(ServerLevel world, RingGeometry geometry, ServerPlayer playerA) {
        BlockPos foot = bedFoot();
        BlockPos head = bedHead();
        var base = RingWorldVanillaFixtureRegistries.block("red_bed")
                .defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
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
        // NeoForge asks the environment-attribute system whether sleeping is
        // allowed before vanilla performs the same check. Let the clock change
        // advance through one server tick so both loaders observe the new
        // night phase instead of a same-tick cached daytime BedRule.
        RingWorldMod.LOGGER.info("[multiplayer-extended] seam bed prepared for next-tick sleep");
    }

    private static void armSeamNavigator(ServerLevel world, RingGeometry geometry) {
        List<Entity> staleNavigators = new ArrayList<>();
        for (Entity entity : world.getAllEntities()) {
            if (entity.entityTags().contains(NAVIGATOR_TAG)) staleNavigators.add(entity);
        }
        staleNavigators.forEach(Entity::discard);
        int circumference = geometry.circumferenceBlocks();
        for (int x = circumference - 8; x <= circumference + 4; x++) {
            int canonicalX = geometry.wrapBlockX(x);
            world.setBlock(new BlockPos(canonicalX, 119, 15), Blocks.STONE.defaultBlockState(), 3);
            world.setBlock(new BlockPos(canonicalX, 120, 15), Blocks.AIR.defaultBlockState(), 3);
            world.setBlock(new BlockPos(canonicalX, 121, 15), Blocks.AIR.defaultBlockState(), 3);
            for (int z : new int[]{14, 16}) {
                world.setBlock(new BlockPos(canonicalX, 120, z), Blocks.STONE.defaultBlockState(), 3);
                world.setBlock(new BlockPos(canonicalX, 121, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        seamNavigator = new Zombie(world);
        seamNavigator.addTag(NAVIGATOR_TAG);
        seamNavigator.setPersistenceRequired();
        seamNavigator.setInvulnerable(true);
        seamNavigator.setPos(circumference - 5.5, 120.0, 15.5);
        world.addFreshEntity(seamNavigator);
        seamNavigator.setOnGround(true);
        navigationStarted = false;
        navigationStarted = seamNavigator.getNavigation().moveTo(2.5, 120.0, 15.5, 1.0);
        RingWorldMod.LOGGER.info(
                "[multiplayer-extended] seam navigator armed started={} sourceX={} target={} path={}",
                navigationStarted, seamNavigator.getX(), seamNavigator.getNavigation().getTargetPos(),
                seamNavigator.getNavigation().getPath() == null ? null
                        : seamNavigator.getNavigation().getPath().getTarget());
    }

    /**
     * Makes the cross-seam explosion independent of generated terrain. Glass
     * is non-dropping, so a seed-specific slope cannot create an unbounded
     * item or falling-block population during the fixture.
     */
    private static void prepareCrossSeamExplosionCell(ServerLevel world, RingGeometry geometry) {
        int highX = geometry.circumferenceBlocks() - 1;
        for (int xOffset = -4; xOffset <= 3; xOffset++) {
            int x = geometry.wrapBlockX(highX + xOffset);
            for (int y = 121; y <= 128; y++) {
                for (int z = 7; z <= 12; z++) {
                    world.setBlock(new BlockPos(x, y, z), Blocks.GLASS.defaultBlockState(), 2);
                }
            }
        }
        world.setBlock(explosionTarget(), Blocks.GLASS.defaultBlockState(), 3);
    }

    private static void attemptSleep(ServerPlayer playerA) {
        sleepAttempted = true;
        BlockPos head = bedHead();
        Either<Player.BedSleepingProblem, net.minecraft.util.Unit> result = playerA.startSleepInBed(head);
        sleepStarted = result.right().isPresent();
        if (sleepStarted) sleepingReconnectBaseline = playerA;
        RingWorldMod.LOGGER.info("[multiplayer-extended] seam bed sleep start={} problem={} canonicalBed={}",
                sleepStarted, result.left().orElse(null), playerA.getSleepingPos().orElse(null));
    }

    private static void awaitSleep(ServerLevel world, RingGeometry geometry, ServerPlayer playerA) {
        if (playerA == null) {
            if (sleepingReconnectBaseline != null) sleepingDisconnectObserved = true;
            if (ticks >= TIMEOUT_TICKS) {
                RingWorldMod.LOGGER.error(
                        "[multiplayer-extended] seam bed reconnect result=false reason=reconnect-timeout disconnect={}",
                        sleepingDisconnectObserved);
                advance(12);
            }
            return;
        }
        if (!sleepAttempted) {
            attemptSleep(playerA);
            return;
        }
        boolean canonicalBed = playerA.getSleepingPos()
                .map(pos -> pos.equals(bedHead()) && pos.getX() >= 0
                        && pos.getX() < geometry.circumferenceBlocks())
                .orElse(false);
        boolean replacementInstance = sleepingReconnectBaseline != null
                && playerA != sleepingReconnectBaseline;
        // A cold server can miss the brief null-player interval when the
        // disconnect and login complete between two ticks. A different
        // ServerPlayer object is itself definitive reconnect evidence.
        if (replacementInstance) sleepingDisconnectObserved = true;
        boolean replacement = sleepingDisconnectObserved && replacementInstance;
        boolean canonicalPlayer = playerA.getX() >= 0.0
                && playerA.getX() < geometry.circumferenceBlocks();
        boolean adjacentToBed = Math.abs(geometry.shortestCircumferenceDelta(
                playerA.getX(), bedHead().getX())) < 4.0
                && Math.abs(playerA.getY() - bedHead().getY()) < 4.0
                && Math.abs(playerA.getZ() - bedHead().getZ()) < 4.0;
        boolean bedStillLoaded = world.hasChunkAt(bedHead())
                && world.getBlockState(bedHead()).is(RingWorldVanillaFixtureRegistries.block("red_bed"));
        if (replacement && !playerA.isSleeping() && playerA.getSleepingPos().isEmpty()
                && canonicalPlayer && adjacentToBed && bedStillLoaded
                && RingWorldMultiplayerTest.clientPassed("A", "bed_reconnect")
                && !sleepRestartAttempted) {
            sleepingReconnectPassed = true;
            sleepRestartAttempted = true;
            // The disposable server's default mode is creative. Reassert the
            // intended survival state on the replacement ServerPlayer before
            // testing ordinary post-reconnect sleep damage.
            prepareSurvivalPlayer(playerA);
            Either<Player.BedSleepingProblem, net.minecraft.util.Unit> result =
                    playerA.startSleepInBed(bedHead());
            sleepRestarted = result.right().isPresent();
            sleepRestartTick = ticks;
            RingWorldMod.LOGGER.info(
                    "[multiplayer-extended] seam bed reconnect result=true canonicalX={} vanillaAwake=true; sleep restart={} problem={}",
                    playerA.getX(), sleepRestarted, result.left().orElse(null));
            return;
        }
        if (sleepingReconnectPassed && sleepRestarted
                && ticks - sleepRestartTick >= 100 && playerA.isSleeping()
                && canonicalBed
                && RingWorldMultiplayerTest.clientPassed("A", "bed_sleep_restart")) {
            BlockPos sleepingPosBeforeDamage = playerA.getSleepingPos().orElse(null);
            boolean damaged = playerA.hurtServer(world, world.damageSources().generic(), 1.0F);
            RingWorldMod.LOGGER.info(
                    "[multiplayer-extended] post-reconnect sleep result=true canonicalX={} canonicalBed={}; damage applied={}",
                    playerA.getX(), sleepingPosBeforeDamage, damaged);
            advance(3);
        } else if (ticks >= TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error(
                    "[multiplayer-extended] seam bed reconnect result=false started={} disconnect={} replacement={} sleeping={} canonicalBed={} canonicalPlayer={} adjacent={} bedLoaded={} sleepClient={} reconnectClient={} restart={} restartClient={}",
                    sleepStarted, sleepingDisconnectObserved, replacement, playerA.isSleeping(),
                    canonicalBed, canonicalPlayer, adjacentToBed, bedStillLoaded,
                    RingWorldMultiplayerTest.clientPassed("A", "bed_sleep"),
                    RingWorldMultiplayerTest.clientPassed("A", "bed_reconnect"), sleepRestarted,
                    RingWorldMultiplayerTest.clientPassed("A", "bed_sleep_restart"));
            advance(12);
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
            advance(12);
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
            advance(12);
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
            advance(12);
        }
    }

    private static void startNetherPortal(ServerLevel overworld, RingGeometry geometry,
                                          ServerPlayer playerA) {
        if (playerA == null || playerA.level() != overworld) return;
        prepareSurvivalPlayer(playerA);
        BlockPos requested = new BlockPos(geometry.circumferenceBlocks() - 8, 120, 12);
        COLD_TELEMETRY.record("nether-before-create", overworld);
        Optional<net.minecraft.util.BlockUtil.FoundRectangle> created = overworld.getPortalForcer()
                .createPortal(requested, Direction.Axis.Z);
        if (created.isEmpty()) {
            RingWorldMod.LOGGER.error("[multiplayer-extended] Nether portal result=false reason=source-create");
            advance(12);
            return;
        }
        overworldNetherPortal = created.get().minCorner;
        playerA.teleportTo(overworld, overworldNetherPortal.getX() + 0.5,
                overworldNetherPortal.getY(), overworldNetherPortal.getZ() + 0.5,
                Set.<Relative>of(), playerA.getYRot(), playerA.getXRot(), false);
        expectedPortalWait = ((Portal) Blocks.NETHER_PORTAL)
                .getPortalTransitionTime(overworld, playerA);
        portalWaitStarted = overworld.getGameTime();
        RingWorldMod.LOGGER.info(
                "[multiplayer-extended] ordinary Nether portal wait armed source={} expectedTicks={}",
                overworldNetherPortal, expectedPortalWait);
        COLD_TELEMETRY.record("nether-wait-armed", overworld);
        advance(7);
    }

    private static void awaitNetherAndReturn(ServerLevel overworld, RingGeometry geometry,
                                              ServerPlayer playerA) {
        if (playerA == null) return;
        if (playerA.level().dimension() == Level.NETHER
                && RingWorldMultiplayerTest.clientPassed("A", "nether_enter")) {
            ServerLevel nether = (ServerLevel) playerA.level();
            COLD_TELEMETRY.record("nether-entered", nether);
            long elapsed = overworld.getGameTime() - portalWaitStarted;
            outboundPortalWaitPassed = elapsed >= expectedPortalWait;
            Optional<BlockPos> exit = nether.getPortalForcer().findClosestPortalPosition(
                    playerA.blockPosition(), true, nether.getWorldBorder());
            if (exit.isEmpty()) {
                RingWorldMod.LOGGER.error("[multiplayer-extended] Nether portal result=false reason=no-linked-exit");
                advance(12);
                return;
            }
            int targetX = geometry.circumferenceBlocks() / 2 + 17;
            BlockPos lowRawTarget = new BlockPos(
                    targetX - geometry.circumferenceBlocks() * 3,
                    120,
                    geometry.minWidthZ() - 10_000);
            BlockPos highRawTarget = new BlockPos(
                    targetX + geometry.circumferenceBlocks() * 4,
                    120,
                    geometry.maxWidthZ() + 10_000);
            Optional<BlockPos> seamFound = overworld.getPortalForcer().findClosestPortalPosition(
                    new BlockPos(2, overworldNetherPortal.getY(), overworldNetherPortal.getZ()),
                    false,
                    overworld.getWorldBorder());
            Optional<net.minecraft.util.BlockUtil.FoundRectangle> lowCreated =
                    overworld.getPortalForcer().createPortal(lowRawTarget, Direction.Axis.Z);
            Optional<net.minecraft.util.BlockUtil.FoundRectangle> highCreated =
                    overworld.getPortalForcer().createPortal(highRawTarget, Direction.Axis.Z);
            Optional<BlockPos> lowFound = overworld.getPortalForcer().findClosestPortalPosition(
                    lowRawTarget, false, overworld.getWorldBorder());
            Optional<BlockPos> highFound = overworld.getPortalForcer().findClosestPortalPosition(
                    highRawTarget, false, overworld.getWorldBorder());
            boolean routing = seamFound.isPresent()
                    && Math.abs(geometry.shortestCircumferenceDelta(
                            overworldNetherPortal.getX(), seamFound.get().getX())) <= 16.0
                    && lowCreated.isPresent() && highCreated.isPresent()
                    && lowFound.isPresent() && highFound.isPresent()
                    && lowFound.get().getX() >= 0
                    && lowFound.get().getX() < geometry.circumferenceBlocks()
                    && highFound.get().getX() >= 0
                    && highFound.get().getX() < geometry.circumferenceBlocks()
                    && Math.abs(geometry.shortestCircumferenceDelta(targetX, lowFound.get().getX())) <= 16.0
                    && Math.abs(geometry.shortestCircumferenceDelta(targetX, highFound.get().getX())) <= 16.0
                    && RingPortalDestinationBounds.isSafePortalBlock(geometry, lowFound.get())
                    && RingPortalDestinationBounds.isSafePortalBlock(geometry, highFound.get());
            if (!routing) {
                RingWorldMod.LOGGER.error(
                        "[multiplayer-extended] multi-lap Nether portal routing result=false seamFound={} lowCreated={} highCreated={} lowFound={} highFound={}",
                        seamFound, lowCreated, highCreated, lowFound, highFound);
                advance(12);
                return;
            }
            netherPortalRoutingPassed = true;
            expectedOverworldPortalReturn = highFound.get();
            // Vanilla performs the 8:1 scaling from this deliberately remote
            // Nether pose. Four complete X laps must resolve to targetX and
            // the extreme positive Z must remain inside the safe ring band.
            playerA.teleportTo(nether,
                    highRawTarget.getX() / 8.0,
                    playerA.getY(),
                    highRawTarget.getZ() / 8.0,
                    Set.<Relative>of(), playerA.getYRot(), playerA.getXRot(), false);
            var transition = ((Portal) Blocks.NETHER_PORTAL)
                    .getPortalDestination(nether, playerA, exit.get());
            if (transition == null) {
                RingWorldMod.LOGGER.error("[multiplayer-extended] Nether portal result=false reason=no-return-transition");
                advance(12);
                return;
            }
            playerA.teleport(transition);
            playerA.setPortalCooldown();
            RingWorldMod.LOGGER.info(
                    "[multiplayer-extended] ordinary Nether portal wait result={} elapsedTicks={} expectedTicks={}",
                    outboundPortalWaitPassed, elapsed, expectedPortalWait);
            RingWorldMod.LOGGER.info(
                    "[multiplayer-extended] multi-lap Nether portal routing result=true seam={} low={} high={} expectedReturn={}",
                    seamFound.get(), lowFound.get(), highFound.get(), expectedOverworldPortalReturn);
            COLD_TELEMETRY.record("nether-return-armed", overworld);
            advance(8);
        } else if (ticks >= TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error("[multiplayer-extended] Nether portal result=false reason=enter-timeout dimension={} client={}",
                    playerA.level().dimension().identifier(),
                    RingWorldMultiplayerTest.clientPassed("A", "nether_enter"));
            advance(12);
        }
    }

    private static void awaitOverworldAndStartEnd(ServerLevel overworld, RingGeometry geometry,
                                                   ServerPlayer playerA) {
        if (playerA == null) return;
        boolean returned = playerA.level() == overworld
                && playerA.getX() >= 0.0 && playerA.getX() < geometry.circumferenceBlocks()
                && Math.abs(geometry.shortestCircumferenceDelta(
                        expectedOverworldPortalReturn.getX(), playerA.getX())) < 16.0
                && Math.abs(expectedOverworldPortalReturn.getZ() - playerA.getZ()) < 16.0
                && RingPortalDestinationBounds.isSafePortalBlock(
                        geometry, playerA.blockPosition())
                && RingWorldMultiplayerTest.clientPassed("A", "nether_return");
        if (returned) {
            netherPortalPassed = outboundPortalWaitPassed && netherPortalRoutingPassed;
            double netherReturnX = playerA.getX();
            COLD_TELEMETRY.record("nether-returned", overworld);
            BlockPos endPortal = new BlockPos(geometry.circumferenceBlocks() - 12, 120, 16);
            overworld.setBlock(endPortal, Blocks.END_PORTAL.defaultBlockState(), 3);
            playerA.teleportTo(overworld, endPortal.getX() + 0.5, endPortal.getY() + 1.0,
                    endPortal.getZ() + 0.5, Set.<Relative>of(), 0.0F, 0.0F, false);
            var transition = ((Portal) Blocks.END_PORTAL)
                    .getPortalDestination(overworld, playerA, endPortal);
            if (transition == null) {
                RingWorldMod.LOGGER.error("[multiplayer-extended] End portal result=false reason=no-outbound-transition");
                advance(12);
                return;
            }
            playerA.teleport(transition);
            playerA.setPortalCooldown();
            RingWorldMod.LOGGER.info("[multiplayer-extended] physical Nether portal result=true returnX={}; End outbound armed",
                    netherReturnX);
            COLD_TELEMETRY.record("end-outbound-armed", (ServerLevel) playerA.level());
            advance(9);
        } else if (ticks >= TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error("[multiplayer-extended] Nether portal result=false reason=return-timeout dimension={} x={} client={}",
                    playerA.level().dimension().identifier(), playerA.getX(),
                    RingWorldMultiplayerTest.clientPassed("A", "nether_return"));
            advance(12);
        }
    }

    private static void awaitEndAndReturn(ServerLevel overworld, ServerPlayer playerA) {
        if (playerA == null) return;
        if (playerA.level().dimension() == Level.END
                && RingWorldMultiplayerTest.clientPassed("A", "end_enter")) {
            ServerLevel end = (ServerLevel) playerA.level();
            COLD_TELEMETRY.record("end-entered", end);
            BlockPos returnPortal = playerA.blockPosition();
            end.setBlock(returnPortal, Blocks.END_PORTAL.defaultBlockState(), 3);
            var transition = ((Portal) Blocks.END_PORTAL)
                    .getPortalDestination(end, playerA, returnPortal);
            if (transition == null) {
                RingWorldMod.LOGGER.error("[multiplayer-extended] End portal result=false reason=no-return-transition");
                advance(12);
                return;
            }
            playerA.teleport(transition);
            playerA.setPortalCooldown();
            COLD_TELEMETRY.record("end-return-armed", overworld);
            advance(10);
        } else if (ticks >= TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error("[multiplayer-extended] End portal result=false reason=enter-timeout dimension={} client={}",
                    playerA.level().dimension().identifier(),
                    RingWorldMultiplayerTest.clientPassed("A", "end_enter"));
            advance(12);
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
            COLD_TELEMETRY.record("end-returned", overworld);
            postEndWeatherGate = new RingMultiplayerReadinessGate();
            weatherArmed = false;
            advance(11);
        } else if (ticks >= TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error("[multiplayer-extended] End portal result=false reason=return-timeout dimension={} x={} client={}",
                    playerA.level().dimension().identifier(), playerA.getX(),
                    RingWorldMultiplayerTest.clientPassed("A", "end_return"));
            advance(12);
        }
    }

    private static void awaitWeather(ServerLevel world, RingGeometry geometry,
                                     ServerPlayer playerA, ServerPlayer playerB) {
        if (playerA == null || playerB == null) return;
        if (!weatherArmed) {
            if (postEndWeatherGate == null) postEndWeatherGate = new RingMultiplayerReadinessGate();
            RingMultiplayerReadinessGate.Result readiness = postEndWeatherGate.observe(System.nanoTime());
            if (readiness == RingMultiplayerReadinessGate.Result.TIMED_OUT) {
                COLD_TELEMETRY.record("weather-stability-timeout", world);
                RingWorldMod.LOGGER.error(
                        "[multiplayer-cold] post-End weather stability timed out "
                                + "(observedTicks={}, consecutiveOnTimeTicks={}, longestTickIntervalMs={})",
                        postEndWeatherGate.observedTicks(), postEndWeatherGate.consecutiveOnTimeTicks(),
                        postEndWeatherGate.longestTickIntervalNanos() / 1_000_000.0);
                advance(12);
                return;
            }
            if (readiness != RingMultiplayerReadinessGate.Result.READY) return;
            weatherArmed = true;
            ticks = 0;
            COLD_TELEMETRY.record("weather-stability-ready", world);
        }
        if (ticks == 1) {
            prepareCreativePlayer(playerA);
            prepareCreativePlayer(playerB);
            playerA.teleportTo(world, geometry.circumferenceBlocks() - 2.5, 120.0, 0.5,
                    Set.<Relative>of(), 90.0F, 10.0F, false);
            playerB.teleportTo(world, 2.5, 120.0, 0.5,
                    Set.<Relative>of(), -90.0F, 10.0F, false);
            var weather = world.getWeatherData();
            weather.setClearWeatherTime(0);
            weather.setRainTime(6_000);
            weather.setThunderTime(6_000);
            weather.setRaining(true);
            weather.setThundering(true);
        }
        if (ticks >= 40 && ticks % 10 == 0) {
            LightningBolt lightning = RingWorldVanillaFixtureRegistries.createEntity(
                    "lightning_bolt", LightningBolt.class, world, EntitySpawnReason.TRIGGERED);
            if (lightning != null) {
                lightning.setVisualOnly(true);
                lightning.setPos(0.5, 121.0, 0.5);
                world.addFreshEntity(lightning);
            }
        }
        boolean serverWeather = world.isRaining() && world.isThundering();
        boolean clients = RingWorldMultiplayerTest.clientPassed("A", "seam_weather")
                && RingWorldMultiplayerTest.clientPassed("B", "seam_weather");
        if (serverWeather && clients) {
            weatherPassed = true;
            RingWorldMod.LOGGER.info(
                    "[multiplayer-extended] seam thunder/lightning result=true rain={} thunder={}",
                    world.getRainLevel(1.0F), world.getThunderLevel(1.0F));
            advance(12);
        } else if (ticks >= TIMEOUT_TICKS) {
            RingWorldMod.LOGGER.error(
                    "[multiplayer-extended] seam thunder/lightning result=false server={} clientA={} clientB={}",
                    serverWeather, RingWorldMultiplayerTest.clientPassed("A", "seam_weather"),
                    RingWorldMultiplayerTest.clientPassed("B", "seam_weather"));
            advance(12);
        }
    }

    private static void finish(ServerLevel world, RingGeometry geometry,
                               ServerPlayer playerA, ServerPlayer playerB) {
        boolean clientFixture = RingWorldMultiplayerTest.clientPassed("A", "extended_fixture")
                && RingWorldMultiplayerTest.clientPassed("B", "extended_fixture");
        boolean clientLifecycle = RingWorldMultiplayerTest.clientPassed("A", "bed_sleep")
                && RingWorldMultiplayerTest.clientPassed("A", "bed_reconnect")
                && RingWorldMultiplayerTest.clientPassed("A", "bed_sleep_restart")
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
        boolean passed = baselinePassed && serverFixturePassed && sleepingReconnectPassed
                && damageWakePassed
                && bedDestroyedPassed && deathObserved && deathRespawnPassed
                && netherPortalPassed && netherPortalRoutingPassed && endPortalPassed && weatherPassed
                && clientFixture && clientLifecycle
                && canonicalPlayers;
        COLD_TELEMETRY.record("terminal-result", world);
        RingWorldMod.LOGGER.info(
                "[multiplayer] full scenario result={} (baseline={}, fixture={}, sleepingReconnect={}, damageWake={}, bedDestroyed={}, deathRespawn={}, netherPortal={}, netherPortalRouting={}, endPortal={}, weather={}, clientFixture={}, clientLifecycle={}, canonicalPlayers={})",
                passed, baselinePassed, serverFixturePassed, sleepingReconnectPassed, damageWakePassed,
                bedDestroyedPassed, deathRespawnPassed, netherPortalPassed, netherPortalRoutingPassed, endPortalPassed,
                weatherPassed, clientFixture, clientLifecycle, canonicalPlayers);
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

    private static void seedDoubleChest(ServerLevel world, RingGeometry geometry) {
        Container fromHigh = doubleChestContainer(world, chestHighPos(geometry));
        Container fromLow = doubleChestContainer(world, chestPos());
        if (fromHigh == null || fromLow == null
                || fromHigh.getContainerSize() != 54 || fromLow.getContainerSize() != 54) {
            return;
        }
        fromHigh.setItem(0, new ItemStack(Items.DIAMOND, 3));
        fromLow.setItem(53, new ItemStack(Items.EMERALD, 5));
        fromHigh.setChanged();
        fromLow.setChanged();
    }

    private static boolean doubleChestValid(ServerLevel world, RingGeometry geometry) {
        BlockPos high = chestHighPos(geometry);
        BlockPos low = chestPos();
        Container fromHigh = doubleChestContainer(world, high);
        Container fromLow = doubleChestContainer(world, low);
        boolean combined = fromHigh != null && fromLow != null
                && fromHigh.getContainerSize() == 54 && fromLow.getContainerSize() == 54;
        boolean sharedInventory = combined
                && fromHigh.getItem(0).is(Items.DIAMOND) && fromHigh.getItem(0).getCount() == 3
                && fromLow.getItem(0).is(Items.DIAMOND) && fromLow.getItem(0).getCount() == 3
                && fromHigh.getItem(53).is(Items.EMERALD) && fromHigh.getItem(53).getCount() == 5
                && fromLow.getItem(53).is(Items.EMERALD) && fromLow.getItem(53).getCount() == 5;
        boolean canonicalAliases = world.getBlockEntity(new BlockPos(
                geometry.circumferenceBlocks(), low.getY(), low.getZ())) == world.getBlockEntity(low)
                && world.getBlockEntity(new BlockPos(-1, high.getY(), high.getZ()))
                == world.getBlockEntity(high);
        boolean connectedStates = connectedChestState(world.getBlockState(high))
                && connectedChestState(world.getBlockState(low));
        return combined && sharedInventory && canonicalAliases && connectedStates;
    }

    private static String doubleChestDiagnostic(ServerLevel world, RingGeometry geometry) {
        BlockPos high = chestHighPos(geometry);
        BlockPos low = chestPos();
        Container fromHigh = doubleChestContainer(world, high);
        Container fromLow = doubleChestContainer(world, low);
        return "highState=" + world.getBlockState(high)
                + ", lowState=" + world.getBlockState(low)
                + ", highSize=" + (fromHigh == null ? -1 : fromHigh.getContainerSize())
                + ", lowSize=" + (fromLow == null ? -1 : fromLow.getContainerSize())
                + ", highSlot0=" + (fromHigh == null ? null : fromHigh.getItem(0))
                + ", lowSlot0=" + (fromLow == null ? null : fromLow.getItem(0))
                + ", highSlot53=" + (fromHigh == null || fromHigh.getContainerSize() <= 53
                        ? null : fromHigh.getItem(53))
                + ", lowSlot53=" + (fromLow == null || fromLow.getContainerSize() <= 53
                        ? null : fromLow.getItem(53))
                + ", lowAliasSame=" + (world.getBlockEntity(new BlockPos(
                        geometry.circumferenceBlocks(), low.getY(), low.getZ()))
                        == world.getBlockEntity(low))
                + ", highAliasSame=" + (world.getBlockEntity(new BlockPos(
                        -1, high.getY(), high.getZ())) == world.getBlockEntity(high));
    }

    /**
     * Simulates an old/modded alias entry colliding with an existing canonical
     * owner. Both inventories must remain independently addressable and
     * serializable until an administrator explicitly removes the alias.
     */
    private static boolean verifyAliasCollisionRecovery(ServerLevel world, RingGeometry geometry) {
        BlockPos canonical = new BlockPos(4, 120, -7);
        BlockPos alias = canonical.offset(geometry.circumferenceBlocks(), 0, 0);
        world.setBlock(canonical, Blocks.AIR.defaultBlockState(), 3);
        BlockState chestState = Blocks.CHEST.defaultBlockState();
        world.setBlock(canonical, chestState, 3);
        if (!(world.getBlockEntity(canonical) instanceof ChestBlockEntity initialCanonicalChest)) {
            return false;
        }
        initialCanonicalChest.setItem(0, new ItemStack(Items.GOLD_INGOT, 2));
        CompoundTag savedCanonical = initialCanonicalChest.saveWithFullMetadata(world.registryAccess());
        LevelChunk chunk = world.getChunkAt(canonical);
        chunk.removeBlockEntity(canonical);

        ChestBlockEntity aliasChest = new ChestBlockEntity(alias, chestState);
        aliasChest.setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        CompoundTag savedAlias = aliasChest.saveWithFullMetadata(world.registryAccess());
        RingBlockEntityLoadContext.withGeometry(geometry, () -> {
            // Deliberately queue and promote the alias first. Recovery must
            // not depend on the serialized list or pending-map iteration order.
            chunk.setBlockEntityNbt(savedAlias);
            chunk.setBlockEntityNbt(savedCanonical);
        });
        // Exercise the real save boundary before either packed entry is read.
        // The alias and canonical payloads must serialize independently.
        CompoundTag pendingAliasNbt = chunk.getBlockEntityNbtForSaving(alias, world.registryAccess());
        CompoundTag pendingCanonicalNbt = chunk.getBlockEntityNbtForSaving(canonical, world.registryAccess());
        BlockEntity loadedAlias = chunk.getBlockEntity(alias);
        BlockEntity loadedCanonical = chunk.getBlockEntity(canonical);
        CompoundTag aliasNbt = chunk.getBlockEntityNbtForSaving(alias, world.registryAccess());
        ChestBlockEntity canonicalChest = loadedCanonical instanceof ChestBlockEntity chest
                ? chest : null;
        ChestBlockEntity loadedAliasChest = loadedAlias instanceof ChestBlockEntity chest
                ? chest : null;
        boolean preserved = canonicalChest != null && loadedAliasChest != null
                && pendingAliasNbt != null
                && pendingAliasNbt.getIntOr("x", Integer.MIN_VALUE) == alias.getX()
                && pendingCanonicalNbt != null
                && pendingCanonicalNbt.getIntOr("x", Integer.MIN_VALUE) == canonical.getX()
                && chunk.getBlockEntity(canonical) == canonicalChest
                && chunk.getBlockEntity(alias) == loadedAliasChest
                && canonicalChest.getItem(0).is(Items.GOLD_INGOT)
                && canonicalChest.getItem(0).getCount() == 2
                && loadedAliasChest.getItem(0).is(Items.IRON_INGOT)
                && loadedAliasChest.getItem(0).getCount() == 4
                && aliasNbt != null;

        chunk.removeBlockEntity(alias);
        boolean exactAliasRemoved = !chunk.getBlockEntities().containsKey(alias)
                && chunk.getBlockEntity(canonical) == canonicalChest;
        world.setBlock(canonical, Blocks.AIR.defaultBlockState(), 3);

        BlockPos directCanonical = new BlockPos(6, 120, -7);
        BlockPos directAlias = directCanonical.offset(geometry.circumferenceBlocks(), 0, 0);
        world.setBlock(directCanonical, chestState, 3);
        LevelChunk directChunk = world.getChunkAt(directCanonical);
        directChunk.removeBlockEntity(directCanonical);
        ChestBlockEntity directAliasChest = new ChestBlockEntity(directAlias, chestState);
        directAliasChest.setItem(0, new ItemStack(Items.IRON_INGOT, 7));
        ChestBlockEntity directCanonicalChest = new ChestBlockEntity(directCanonical, chestState);
        directCanonicalChest.setItem(0, new ItemStack(Items.GOLD_INGOT, 8));
        RingBlockEntityLoadContext.withGeometry(geometry, () -> {
            directChunk.setBlockEntity(directAliasChest);
            directChunk.setBlockEntity(directCanonicalChest);
        });
        RingBlockEntityOwnership.reconcileLoadedAliases(directChunk, geometry);
        boolean directAliasFirstPreserved = directChunk.getBlockEntity(directAlias)
                == directAliasChest
                && directChunk.getBlockEntity(directCanonical) == directCanonicalChest
                && directAliasChest.getItem(0).is(Items.IRON_INGOT)
                && directAliasChest.getItem(0).getCount() == 7
                && directCanonicalChest.getItem(0).is(Items.GOLD_INGOT)
                && directCanonicalChest.getItem(0).getCount() == 8;
        directChunk.removeBlockEntity(directAlias);
        world.setBlock(directCanonical, Blocks.AIR.defaultBlockState(), 3);

        BlockPos repairedCanonical = new BlockPos(5, 120, -7);
        BlockPos repairedAlias = repairedCanonical.offset(geometry.circumferenceBlocks(), 0, 0);
        world.setBlock(repairedCanonical, chestState, 3);
        LevelChunk repairedChunk = world.getChunkAt(repairedCanonical);
        repairedChunk.removeBlockEntity(repairedCanonical);
        ChestBlockEntity loneAlias = new ChestBlockEntity(repairedAlias, chestState);
        loneAlias.setItem(0, new ItemStack(Items.COPPER_INGOT, 6));
        CompoundTag savedLoneAlias = loneAlias.saveWithFullMetadata(world.registryAccess());
        RingBlockEntityLoadContext.withGeometry(
                geometry, () -> repairedChunk.setBlockEntityNbt(savedLoneAlias));
        BlockEntity repaired = repairedChunk.getBlockEntity(repairedAlias);
        boolean singleAliasRepaired = repaired instanceof ChestBlockEntity repairedChest
                && repaired.getBlockPos().equals(repairedCanonical)
                && repairedChunk.getBlockEntity(repairedCanonical) == repaired
                && repairedChunk.getBlockEntity(repairedAlias) == repaired
                && repairedChest.getItem(0).is(Items.COPPER_INGOT)
                && repairedChest.getItem(0).getCount() == 6
                && !repairedChunk.getBlockEntities().containsKey(repairedAlias);
        world.setBlock(repairedCanonical, Blocks.AIR.defaultBlockState(), 3);
        return preserved && exactAliasRemoved && directAliasFirstPreserved
                && singleAliasRepaired;
    }

    private static Container doubleChestContainer(ServerLevel world, BlockPos position) {
        BlockState state = world.getBlockState(position);
        return state.getBlock() instanceof ChestBlock chest
                ? ChestBlock.getContainer(chest, state, world, position, true)
                : null;
    }

    private static boolean connectedChestState(BlockState state) {
        return state.is(Blocks.CHEST) && state.getValue(ChestBlock.TYPE)
                != net.minecraft.world.level.block.state.properties.ChestType.SINGLE;
    }

    private static BlockPos chestPos() { return new BlockPos(0, 120, -3); }
    private static BlockPos chestHighPos(RingGeometry geometry) {
        return new BlockPos(geometry.circumferenceBlocks() - 1, 120, -3);
    }
    private static BlockPos lecternPos() { return new BlockPos(1, 120, -3); }
    private static BlockPos redstoneLampPos() { return new BlockPos(0, 120, -5); }
    private static BlockPos fluidDestination() { return new BlockPos(0, 120, 6); }
    private static BlockPos explosionTarget() { return new BlockPos(0, 124, 9); }
    private static BlockPos bedFoot() { return new BlockPos(0, 120, -1); }
    private static BlockPos bedHead() { return new BlockPos(1, 120, -1); }
}
