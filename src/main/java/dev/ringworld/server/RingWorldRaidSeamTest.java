package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.mixin.RaidFixtureAccessor;
import dev.ringworld.world.RingGeometry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.goal.PathfindToRaidGoal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * Opt-in, two-process dedicated-server proof for seam-straddling vanilla raids.
 *
 * <p>The first process creates real occupied village POIs, starts a real raid,
 * waits for its first vanilla wave, and saves while that wave is alive. The
 * second process reopens the same disposable world, proves membership and a
 * natural raider fold, then lets the real victory path grant Hero of the
 * Village. This class has no loader imports; Fabric and NeoForge both call the
 * ordinary {@link #tick(ServerLevel, RingGeometry)} hook.</p>
 */
public final class RingWorldRaidSeamTest {
    private static final String ENABLED = "ringworld.raidSeamTest";
    private static final String RELOAD_PHASE = "reload";
    private static final String PHASE = "ringworld.raidSeamTestPhase";
    private static final String STOP_AFTER_SAVE = "ringworld.raidSeamTestStopAfterSave";
    private static final String RAIDER_TAG = "ringworld_raid_seam_fixture_raider";
    private static final int Y = 120;
    private static final int Z = 30;
    private static final int TIMEOUT_TICKS = 600;

    private static int stage;
    private static int ticks;
    private static Raid raid;
    private static Raider navigationRaider;
    private static PathfindToRaidGoal<Raider> navigationGoal;
    private static double previousRaiderX = Double.NaN;
    private static boolean sawRaiderFold;
    private static boolean terminal;
    private static int restoreWaitTicks;
    private static boolean reloadPrepared;

    private RingWorldRaidSeamTest() { }

    public static boolean enabled() {
        return Boolean.getBoolean(ENABLED);
    }

    public static void tick(ServerLevel world, RingGeometry geometry) {
        if (!enabled() || terminal) return;
        boolean reload = RELOAD_PHASE.equalsIgnoreCase(System.getProperty(PHASE, "arm"));
        if (reload && !reloadPrepared) {
            loadSeamChunks(world, geometry);
            if (!persistedOccupiedPoi(world, highSidePoi(geometry))
                    || !persistedOccupiedPoi(world, lowSidePoi())) {
                fail(world, geometry, "saved occupied seam POIs did not restore");
                return;
            }
            reloadPrepared = true;
        }
        ServerPlayer playerA = playerNamed(world, "RingTesterA");
        ServerPlayer playerB = playerNamed(world, "RingTesterB");
        if (playerA == null || playerB == null) {
            if (ticks++ == 0) RingWorldMod.LOGGER.info("[raid-seam] waiting for RingTesterA and RingTesterB");
            return;
        }
        ticks++;
        if (reload) {
            tickReload(world, geometry, playerA, playerB);
        } else {
            tickArm(world, geometry, playerA, playerB);
        }
    }

    private static void tickArm(ServerLevel world, RingGeometry geometry,
                                ServerPlayer playerA, ServerPlayer playerB) {
        switch (stage) {
            case 0 -> {
                prepare(world, geometry, playerA, playerB);
                stage = 1;
                ticks = 0;
            }
            case 1 -> {
                if (raid == null || !raid.isActive()) {
                    fail(world, geometry, "raid did not become active");
                    return;
                }
                if (raid.getTotalRaidersAlive() > 0 && raid.isStarted()) {
                    navigationRaider = raid.getAllRaiders().stream().findFirst().orElse(null);
                    boolean spawned = navigationRaider != null
                            && canonical(geometry, navigationRaider)
                            && world.isPositionEntityTicking(navigationRaider.blockPosition());
                    boolean bossbar = bossbarContains(raid, playerA) && bossbarContains(raid, playerB);
                    if (!spawned || !bossbar) {
                        fail(world, geometry, "wave/bossbar assertion failed spawned=" + spawned + " bossbar=" + bossbar);
                        return;
                    }
                    navigationRaider.addTag(RAIDER_TAG);
                    world.setBlock(marker(), RingWorldVanillaFixtureRegistries
                            .block("lime_concrete").defaultBlockState(), 3);
                    boolean saved = world.getServer().saveEverything(false, true, false);
                    RingWorldMod.LOGGER.info("[raid-seam] arm-save-ready=true saved={} center={} raiders={} bossbarA={} bossbarB={}",
                            saved, raid.getCenter(), raid.getTotalRaidersAlive(),
                            bossbarContains(raid, playerA), bossbarContains(raid, playerB));
                    terminal = true;
                    if (Boolean.getBoolean(STOP_AFTER_SAVE)) world.getServer().halt(false);
                } else if (ticks > TIMEOUT_TICKS) {
                    fail(world, geometry, "timed out waiting for first vanilla wave");
                }
            }
            default -> throw new IllegalStateException("Unknown raid arm stage " + stage);
        }
    }

    private static void tickReload(ServerLevel world, RingGeometry geometry,
                                   ServerPlayer playerA, ServerPlayer playerB) {
        switch (stage) {
            case 0 -> {
                preparePlayers(world, geometry, playerA, playerB);
                if (!world.getBlockState(marker()).is(RingWorldVanillaFixtureRegistries.block("lime_concrete"))) {
                    fail(world, geometry, "missing arm marker; run arm phase first in the same disposable world");
                    return;
                }
                loadSeamChunks(world, geometry);
                raid = world.getRaidAt(lowSideProbe());
                navigationRaider = taggedRaider(world);
                boolean restored = raid != null && navigationRaider != null
                        && canonical(geometry, raid.getCenter())
                        && world.getRaidAt(highSideProbe(geometry)) == raid
                        && raid.getAllRaiders().contains(navigationRaider)
                        && navigationRaider.getCurrentRaid() == raid;
                if (!restored) {
                    if (++restoreWaitTicks > TIMEOUT_TICKS) {
                        fail(world, geometry, "saved raid/raider did not restore canonically"
                                + " raid=" + (raid != null) + " raider=" + (navigationRaider != null));
                    }
                    return;
                }
                stage = 1;
                ticks = 0;
            }
            case 1 -> {
                if (ticks < 25) return;
                if (!bossbarContains(raid, playerA) || !bossbarContains(raid, playerB)) {
                    fail(world, geometry, "saved raid bossbar did not include both seam players");
                    return;
                }
                armNavigation(world, geometry);
                stage = 2;
                ticks = 0;
            }
            case 2 -> {
                if (navigationRaider == null || navigationRaider.isRemoved()
                        || navigationRaider.getCurrentRaid() != raid || !raid.getAllRaiders().contains(navigationRaider)) {
                    fail(world, geometry, "raider was removed from its restored raid during navigation");
                    return;
                }
                // DefaultRandomPos may legitimately miss on an individual
                // sample. Retry vanilla's goal while navigation is idle so
                // one random miss cannot make the deterministic gate fail.
                if (navigationRaider.getNavigation().isDone() && !sawRaiderFold) {
                    navigationGoal.tick();
                }
                double step = geometry.shortestCircumferenceDelta(previousRaiderX, navigationRaider.getX());
                if (previousRaiderX - navigationRaider.getX() > geometry.circumferenceBlocks() / 2.0
                        && step > 0.0 && step < 4.0) sawRaiderFold = true;
                previousRaiderX = navigationRaider.getX();
                if (sawRaiderFold && navigationRaider.getX() < 3.0) {
                    ((RaidFixtureAccessor) raid).ringworld$setFixtureGroupsSpawned(
                            raid.getNumGroups(world.getDifficulty()));
                    for (Raider raider : new ArrayList<>(raid.getAllRaiders())) {
                        raider.hurtServer(world, world.damageSources().playerAttack(playerA), 1_000.0F);
                        if (raider.isAlive()) raider.kill(world);
                    }
                    stage = 3;
                    ticks = 0;
                } else if (ticks > TIMEOUT_TICKS) {
                    fail(world, geometry, "raider did not naturally fold across the seam");
                }
            }
            case 3 -> {
                boolean victory = raid.isVictory() && playerA.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
                if (victory) {
                    world.setBlock(marker(), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                    RingWorldMod.LOGGER.info("[raid-seam] PASS center={} fold={} hero={} canonicalRaider={}",
                            raid.getCenter(), sawRaiderFold, true, canonical(geometry, navigationRaider));
                    terminal = true;
                } else if (ticks > 300) {
                    fail(world, geometry, "real raid did not reach victory and grant Hero of the Village"
                            + " status=" + (raid.isVictory() ? "victory" : raid.isActive() ? "active" : "inactive")
                            + " raiders=" + raid.getTotalRaidersAlive()
                            + " groups=" + raid.getGroupsSpawned()
                            + " hero=" + playerA.hasEffect(MobEffects.HERO_OF_THE_VILLAGE));
                }
            }
            default -> throw new IllegalStateException("Unknown raid reload stage " + stage);
        }
    }

    private static void prepare(ServerLevel world, RingGeometry geometry,
                                ServerPlayer playerA, ServerPlayer playerB) {
        preparePlayers(world, geometry, playerA, playerB);
        prepareChunksAndLane(world, geometry);
        world.getGameRules().set(GameRules.RAIDS, true, world.getServer());

        ensureOccupiedPois(world, geometry);
        if (terminal) return;

        playerA.addEffect(new MobEffectInstance(MobEffects.RAID_OMEN, 1_200, 0));
        raid = world.getRaids().createOrExtendRaid(playerA, highSideProbe(geometry));
        BlockPos expectedCenter = new BlockPos(1, Y, Z);
        boolean centre = raid != null && raid.getCenter().equals(expectedCenter)
                && world.getRaidAt(highSideProbe(geometry)) == raid
                && world.getRaidAt(lowSideProbe()) == raid;
        if (!centre) {
            fail(world, geometry, "seam POIs did not create expected canonical center expected="
                    + expectedCenter + " actual=" + (raid == null ? null : raid.getCenter()));
            return;
        }
        ((RaidFixtureAccessor) raid).ringworld$setFixtureRaidCooldownTicks(20);
        RingWorldMod.LOGGER.info("[raid-seam] arm-created center={} highProbe={} lowProbe={}",
                raid.getCenter(), highSideProbe(geometry), lowSideProbe());
    }

    private static void ensureOccupiedPois(ServerLevel world, RingGeometry geometry) {
        PoiManager pois = world.getPoiManager();
        Holder<PoiType> home = world.registryAccess()
                .lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME);
        for (BlockPos poi : List.of(highSidePoi(geometry), lowSidePoi())) {
            world.setBlock(poi, RingWorldVanillaFixtureRegistries.block("red_bed").defaultBlockState(), 3);
            pois.remove(poi);
            pois.add(poi, home);
            boolean occupied = pois.take(holder -> holder.is(PoiTypeTags.VILLAGE),
                    (holder, candidate) -> candidate.equals(poi), poi, 0).isPresent();
            if (!occupied) {
                fail(world, geometry, "could not occupy seam POI " + poi);
                return;
            }
        }
    }

    private static boolean persistedOccupiedPoi(ServerLevel world, BlockPos expected) {
        return world.getPoiManager().getInRange(
                        holder -> holder.is(PoiTypeTags.VILLAGE), expected, 0,
                        PoiManager.Occupancy.IS_OCCUPIED)
                .anyMatch(record -> record.getPos().equals(expected));
    }

    private static void preparePlayers(ServerLevel world, RingGeometry geometry,
                                       ServerPlayer playerA, ServerPlayer playerB) {
        for (ServerPlayer player : List.of(playerA, playerB)) {
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.setHealth(player.getMaxHealth());
            player.setDeltaMovement(Vec3.ZERO);
        }
        playerA.teleportTo(world, geometry.circumferenceBlocks() - 5.0, Y, Z + 0.5,
                Set.<Relative>of(), 90.0f, 0.0f, false);
        playerB.teleportTo(world, 2.5, Y, Z + 0.5,
                Set.<Relative>of(), -90.0f, 0.0f, false);
    }

    private static void prepareChunksAndLane(ServerLevel world, RingGeometry geometry) {
        loadSeamChunks(world, geometry);
        for (int x = geometry.circumferenceBlocks() - 12; x <= geometry.circumferenceBlocks() + 12; x++) {
            int canonicalX = geometry.wrapBlockX(x);
            for (int z = Z - 3; z <= Z + 3; z++) {
                world.setBlock(new BlockPos(canonicalX, Y - 1, z), Blocks.STONE.defaultBlockState(), 3);
                world.setBlock(new BlockPos(canonicalX, Y, z), Blocks.AIR.defaultBlockState(), 3);
                world.setBlock(new BlockPos(canonicalX, Y + 1, z), Blocks.AIR.defaultBlockState(), 3);
            }
            for (int z : List.of(Z - 2, Z + 2)) {
                world.setBlock(new BlockPos(canonicalX, Y, z), Blocks.STONE.defaultBlockState(), 3);
                world.setBlock(new BlockPos(canonicalX, Y + 1, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
    }

    private static void loadSeamChunks(ServerLevel world, RingGeometry geometry) {
        int chunks = geometry.circumferenceChunks();
        int zChunk = Math.floorDiv(Z, 16);
        for (int offset = -4; offset <= 4; offset++) {
            int chunkX = Math.floorMod(offset, chunks);
            for (int chunkZ = zChunk - 2; chunkZ <= zChunk + 2; chunkZ++) world.getChunk(chunkX, chunkZ);
        }
    }

    private static void armNavigation(ServerLevel world, RingGeometry geometry) {
        navigationRaider.teleportTo(world, geometry.circumferenceBlocks() - 5.5, Y, Z + 0.5,
                Set.<Relative>of(), 90.0f, 0.0f, false);
        navigationRaider.setDeltaMovement(Vec3.ZERO);
        navigationRaider.getNavigation().stop();
        navigationGoal = new PathfindToRaidGoal<>(navigationRaider);
        navigationGoal.tick();
        previousRaiderX = navigationRaider.getX();
        sawRaiderFold = false;
        RingWorldMod.LOGGER.info("[raid-seam] restored raider navigation armed source={} target={} path={}",
                navigationRaider.blockPosition(), navigationRaider.getNavigation().getTargetPos(),
                navigationRaider.getNavigation().getPath() == null ? null
                        : navigationRaider.getNavigation().getPath().getTarget());
    }

    private static boolean bossbarContains(Raid raid, ServerPlayer player) {
        return ((RaidFixtureAccessor) raid).ringworld$fixtureBossEvent().getPlayers().contains(player);
    }

    private static Raider taggedRaider(ServerLevel world) {
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof Raider raider && raider.entityTags().contains(RAIDER_TAG)) return raider;
        }
        return null;
    }

    private static ServerPlayer playerNamed(ServerLevel world, String name) {
        for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
            if (player.level() == world && player.getName().getString().equals(name)) return player;
        }
        return null;
    }

    private static boolean canonical(RingGeometry geometry, Entity entity) {
        return entity.getX() >= 0.0 && entity.getX() < geometry.circumferenceBlocks();
    }

    private static boolean canonical(RingGeometry geometry, BlockPos position) {
        return position.getX() >= 0 && position.getX() < geometry.circumferenceBlocks();
    }

    private static BlockPos highSidePoi(RingGeometry geometry) {
        return new BlockPos(geometry.circumferenceBlocks() - 2, Y, Z);
    }

    private static BlockPos lowSidePoi() {
        return new BlockPos(4, Y, Z);
    }

    private static BlockPos highSideProbe(RingGeometry geometry) {
        return new BlockPos(geometry.circumferenceBlocks() - 4, Y, Z);
    }

    private static BlockPos lowSideProbe() {
        return new BlockPos(2, Y, Z);
    }

    private static BlockPos marker() {
        return new BlockPos(1, Y + 3, Z);
    }

    private static void fail(ServerLevel world, RingGeometry geometry, String reason) {
        if (terminal) return;
        world.setBlock(marker(), RingWorldVanillaFixtureRegistries.block("red_concrete").defaultBlockState(), 3);
        RingWorldMod.LOGGER.error("[raid-seam] FAIL stage={} ticks={} reason={}", stage, ticks, reason);
        terminal = true;
    }
}
