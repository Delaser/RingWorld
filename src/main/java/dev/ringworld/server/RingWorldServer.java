package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.net.RingWorldNetworking;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingWorldGeneratorAccess;
import dev.ringworld.world.RingWorldSettings;
import dev.ringworld.world.RingNoiseCoordinates;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Server-authoritative seam and boundary behaviour. */
public final class RingWorldServer {
    private static final Map<UUID, TestProgress> TEST_PROGRESS = new HashMap<>();
    private static final Map<UUID, Integer> PLAYER_SEAM_CROSSINGS = new HashMap<>();
    private static final Map<UUID, Long> LAST_PLAYER_SEAM_CROSSING_TICK = new HashMap<>();
    private static final Map<ServerLevel, RingGeometry> WORLD_GEOMETRY = new IdentityHashMap<>();
    private static final Map<UUID, Integer> TEST_MOVING_ENTITIES = new HashMap<>();
    private static final Map<UUID, Integer> TEST_SEAM_ENTITIES = new HashMap<>();
    private static final Map<UUID, Integer> TEST_PROJECTILE_TARGETS = new HashMap<>();
    private static final Map<UUID, Integer> TEST_PROJECTILES = new HashMap<>();
    private static final Map<UUID, Integer> TEST_VEHICLES = new HashMap<>();
    private static final Map<UUID, Integer> TEST_AI_MOBS = new HashMap<>();
    private static final Map<UUID, Integer> TEST_EXPLOSION_ENTITIES = new HashMap<>();
    private static final Map<ServerLevel, LinkedHashMap<Long, net.minecraft.world.level.chunk.LevelChunk>>
            PENDING_LEGACY_RIM_MIGRATIONS = new IdentityHashMap<>();
    private static final AtomicLong NON_CANONICAL_HOLDER_REQUESTS = new AtomicLong();

    private record TestProgress(int stage, int ticks) { }
    private RingWorldServer() { }

    public static void register() {
        RingTerrainAtlasServer.registerCommands();
        ServerTickEvents.END_LEVEL_TICK.register(RingWorldServer::tickRingWorld);
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk, generated) -> {
            if (!isOverworld(world)) return;
            RingTerrainAtlasServer.captureLoadedChunk(world, chunk);
            // A WorldChunk is not safe to mutate from inside its own load
            // callback. Doing so can re-enter ServerChunkCache and park the
            // server thread waiting for the future that is currently firing
            // this callback. Queue it for the end of a later world tick.
            if (RingGenerationBoundary.containsRim(chunk, geometryFor(world))) {
                PENDING_LEGACY_RIM_MIGRATIONS
                        .computeIfAbsent(world, unused -> new LinkedHashMap<>())
                        .putIfAbsent(chunk.getPos().pack(), chunk);
            }
        });
        ServerLevelEvents.LOAD.register((server, world) -> {
            attachWorldGeometry(world);
            if (isOverworld(world)) RingTerrainAtlasServer.load(world);
        });
        ServerLevelEvents.UNLOAD.register((server, world) -> {
            RingTerrainAtlasServer.unload(world);
            WORLD_GEOMETRY.remove(world);
            PENDING_LEGACY_RIM_MIGRATIONS.remove(world);
            if (isOverworld(world)) RingNoiseCoordinates.clearCache();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            RingWorldMultiplayerTest.prepareWaitingPlayer(handler.player);
            server.execute(() -> rescueEmbeddedPlayer(handler.player));
        });
        RingWorldNetworking.registerServer();
    }

    private static boolean isOverworld(ServerLevel world) {
        return world.dimension() == Level.OVERWORLD;
    }

    /**
     * Installs saved geometry as soon as the constructed ServerLevel owns its
     * persistent-state manager and chunk generator. Bootstrap config is used
     * only when {@link RingWorldSettings#get} creates this world's first state.
     */
    public static RingGeometry attachWorldGeometry(ServerLevel world) {
        if (!isOverworld(world)) return null;
        RingWorldSettings settings = RingWorldSettings.get(world);
        RingGeometry geometry = settings.geometry();
        WORLD_GEOMETRY.put(world, geometry);
        attachGeneratorSettings(world, geometry, settings.wallHeightBlocks());
        return geometry;
    }

    private static void attachGeneratorSettings(ServerLevel world, RingGeometry geometry, int wallHeightBlocks) {
        ChunkGenerator generator = world.getChunkSource().getGenerator();
        if (generator instanceof RingWorldGeneratorAccess access) {
            access.ringworld$setGeometry(geometry);
            access.ringworld$setWallHeight(wallHeightBlocks);
        }
    }

    /** Allocation-free geometry lookup for chunk and network hot paths. */
    public static RingGeometry geometryFor(ServerLevel world) {
        RingGeometry geometry = WORLD_GEOMETRY.get(world);
        return geometry != null ? geometry : attachWorldGeometry(world);
    }

    public static void recordNonCanonicalHolderRequest() {
        NON_CANONICAL_HOLDER_REQUESTS.incrementAndGet();
    }

    /** Test instrumentation for packet-backed crossings; never persisted. */
    public static void recordPlayerCanonicalWrap(ServerPlayer player) {
        if (!RingWorldConfig.load().testMode()) return;
        long tick = player.level().getGameTime();
        Long previousTick = LAST_PLAYER_SEAM_CROSSING_TICK.put(player.getUUID(), tick);
        if (previousTick != null && previousTick == tick) return;
        PLAYER_SEAM_CROSSINGS.merge(player.getUUID(), 1, Integer::sum);
    }

    private static void tickRingWorld(ServerLevel world) {
        if (!isOverworld(world)) return;
        RingGeometry geometry = geometryFor(world);
        // The server owns exactly one circumference plane. Clients may keep a
        // nearby presentation image for smooth seam rendering, but no entity
        // position outside [0, C) survives the authoritative tick boundary.
        for (Entity entity : world.getAllEntities()) {
            canonicalizeEntityPosition(entity, geometry);
        }
        migrateOneLegacyRimChunk(world, geometry);
        RingTerrainAtlasServer.tick(world);
        RingWorldMultiplayerTest.tick(world, geometry);
        runAutomatedTest(world, geometry);
    }

    /**
     * Folds one live server entity onto the single authoritative ring plane
     * without teleport packets, velocity changes, or camera rotation.
     *
     * @return the X shift applied to the entity
     */
    public static double canonicalizeEntityPosition(Entity entity, RingGeometry geometry) {
        if (!(entity.level() instanceof ServerLevel world) || !isOverworld(world)) return 0.0;
        double sourceX = entity.getX();
        double canonicalX = geometry.wrapX(sourceX);
        if (canonicalX == sourceX) return 0.0;
        entity.setPos(canonicalX, entity.getY(), entity.getZ());
        return canonicalX - sourceX;
    }

    /**
     * Migrates at most one loaded boundary chunk per tick. Besides avoiding
     * chunk-load reentrancy, the limit keeps an old world's first login from
     * turning hundreds of rim conversions into one watchdog-sized tick.
     */
    private static void migrateOneLegacyRimChunk(ServerLevel world, RingGeometry geometry) {
        LinkedHashMap<Long, net.minecraft.world.level.chunk.LevelChunk> pending =
                PENDING_LEGACY_RIM_MIGRATIONS.get(world);
        if (pending == null || pending.isEmpty()) return;

        var iterator = pending.entrySet().iterator();
        var entry = iterator.next();
        iterator.remove();

        long started = System.nanoTime();
        boolean migrated = RingGenerationBoundary.migrateLegacyRim(entry.getValue(), geometry,
                RingWorldSettings.get(world).wallHeightBlocks());
        if (migrated) {
            double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
            RingWorldMod.LOGGER.info("Migrated legacy rim chunk {} in {} ms ({} queued)",
                    entry.getValue().getPos(), elapsedMs, pending.size());
        }
        if (pending.isEmpty()) PENDING_LEGACY_RIM_MIGRATIONS.remove(world);
    }

    /**
     * A player saved while a seam-adjacent chunk was still settling can load
     * with their bounding box inside newly available terrain. Vanilla then
     * renders an apparently black world because the camera is enclosed by a
     * solid block. Validate the join pose once, and only move players whose
     * actual collision box is obstructed.
     */
    private static void rescueEmbeddedPlayer(ServerPlayer player) {
        ServerLevel world = player.level();
        if (!isOverworld(world)) return;
        RingWorldSettings settings = RingWorldSettings.get(world);
        RingWorldMod.LOGGER.info("[diagnostic] joined ring world at x={}, y={}, z={}; width={}, circumference={}, seed={}, format={}",
                player.getX(), player.getY(), player.getZ(), settings.widthBlocks(),
                settings.circumferenceBlocks(), settings.generatorSeed(), settings.formatVersion());
        if (world.noCollision(player)) return;

        int blockX = (int) Math.floor(player.getX());
        int blockZ = (int) Math.floor(player.getZ());
        int surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        double targetY = Math.max(surfaceY, Math.floor(player.getY()) + 1.0);

        while (targetY < world.getMaxY()
                && !world.noCollision(player,
                player.getBoundingBox().move(0.0, targetY - player.getY(), 0.0))) {
            targetY += 1.0;
        }
        if (targetY >= world.getMaxY()) {
            RingWorldMod.LOGGER.warn("Could not find an unobstructed join pose for {} at {}, {}, {}",
                    player.getName().getString(), player.getX(), player.getY(), player.getZ());
            return;
        }

        double sourceY = player.getY();
        player.teleportTo(world, player.getX(), targetY, player.getZ(),
                Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
        player.setDeltaMovement(Vec3.ZERO);
        RingWorldMod.LOGGER.warn("Rescued embedded player {} from y={} to y={} at x={}, z={}",
                player.getName().getString(), sourceY, targetY, player.getX(), player.getZ());
    }

    /** Local-only smoke test, activated solely by testMode=true in ringworld.properties. */
    private static void runAutomatedTest(ServerLevel world, RingGeometry geometry) {
        if (!RingWorldConfig.load().testMode()) return;
        for (ServerPlayer player : world.players()) {
            TestProgress progress = TEST_PROGRESS.getOrDefault(player.getUUID(), new TestProgress(0, 0));
            if (progress.stage == 0) {
                // A vanilla spawn may initially arrive in the negative image
                // of the ring and be canonicalised before this probe starts.
                // Count only the deliberate packet-backed traversal below.
                PLAYER_SEAM_CROSSINGS.remove(player.getUUID());
                LAST_PLAYER_SEAM_CROSSING_TICK.remove(player.getUUID());
                TEST_MOVING_ENTITIES.remove(player.getUUID());
                TEST_SEAM_ENTITIES.remove(player.getUUID());
                TEST_PROJECTILE_TARGETS.remove(player.getUUID());
                TEST_PROJECTILES.remove(player.getUUID());
                TEST_VEHICLES.remove(player.getUUID());
                TEST_AI_MOBS.remove(player.getUUID());
                TEST_EXPLOSION_ENTITIES.remove(player.getUUID());
                NON_CANONICAL_HOLDER_REQUESTS.set(0L);
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                RingWorldMod.LOGGER.info("[test] creative mode enabled for {}", player.getName().getString());
                TEST_PROGRESS.put(player.getUUID(), new TestProgress(1, 0));
            } else if (progress.stage == 1 && progress.ticks >= 140) {
                // Delay until after the vanilla join-position packet; otherwise
                // it overwrites this showcase teleport on the client.
                // Use a terrain-relative altitude. The old fixed Y=120 camera
                // could land inside an amplified mountain on an otherwise
                // valid random seed and turn every visual capture black.
                int terrainTop = world.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0);
                int showcaseY = Math.min(world.getMaxY() - 16,
                        Math.max(120, terrainTop + 32));
                clearTestCameraSpace(world, showcaseY);
                // Minecraft yaw 90 faces along canonical X, the periodic ring
                // circumference; the small downward pitch includes the band.
                player.teleportTo(world, 0.5, showcaseY, 0.5, Set.<Relative>of(), 90.0f, 22.0f, false);
                RingWorldMod.LOGGER.info("[test] showcase camera at y={}", showcaseY);
                TEST_PROGRESS.put(player.getUUID(), new TestProgress(2, 0));
            } else if (progress.stage == 2 && progress.ticks >= 40) {
                int terrainTopAtSpawn = world.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0);
                boolean terrainPresent = terrainTopAtSpawn > world.getMinY();
                RingWorldMod.LOGGER.info("[test] terrain={}, terrainTopSpawn={}", terrainPresent, terrainTopAtSpawn);
                boolean wrapped = geometry.wrapX(geometry.circumferenceBlocks() + 0.5) == 0.5;
                RingWorldMod.LOGGER.info("[test] circumference coordinate wrap={}", wrapped);
                TEST_PROGRESS.put(player.getUUID(), new TestProgress(4, 0));
            } else if (progress.stage == 4 && progress.ticks >= 700) {
                // Let the normal render smoke test settle first, then put the
                // client eight blocks before the seam. The client waits for
                // the periodic chunks ahead to be meshed, then walks across
                // over several ticks. This exercises real movement prediction
                // and one canonical server wrap instead of disguising a hitch inside
                // a direct four-block test jump.
                clearTestSeamFlightPath(world, geometry, 120);
                player.teleportTo(world, geometry.circumferenceBlocks() - 8.0, 120.0, 0.5,
                        Set.<Relative>of(), 90.0f, 22.0f, false);
                // Send the logical target after the teleport packet so the
                // client maps it into its nearby seam presentation (x=C+2).
                world.setBlock(new BlockPos(2, 119, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                world.setBlock(new BlockPos(3, 119, 3), Blocks.STONE.defaultBlockState(), 3);
                ItemEntity seamItem = new ItemEntity(world, 2.5, 120.0, 2.5, new ItemStack(Items.DIAMOND));
                seamItem.setNoGravity(true);
                seamItem.setNeverPickUp();
                seamItem.setDeltaMovement(Vec3.ZERO);
                world.addFreshEntity(seamItem);
                TEST_SEAM_ENTITIES.put(player.getUUID(), seamItem.getId());
                ItemEntity movingSeamItem = new ItemEntity(world,
                        geometry.circumferenceBlocks() - 1.5, 120.0, 2.5,
                        new ItemStack(Items.EMERALD));
                movingSeamItem.setNoGravity(true);
                movingSeamItem.setNeverPickUp();
                movingSeamItem.setDeltaMovement(0.15, 0.0, 0.0);
                world.addFreshEntity(movingSeamItem);
                TEST_MOVING_ENTITIES.put(player.getUUID(), movingSeamItem.getId());
                RingWorldMod.LOGGER.info("[test] seam traversal armed at x={}", player.getX());
                TEST_PROGRESS.put(player.getUUID(), new TestProgress(5, 0));
            } else if (progress.stage == 5
                    && (world.getBlockState(new BlockPos(2, 119, 0)).isAir()
                    || progress.ticks >= 3_600)) {
                // The acknowledgement also proves the seam-side chunks are
                // active. Start a fresh interval so projectile/AI assertions
                // measure active simulation ticks rather than prefetch time.
                armGameplaySeamProbes(world, player, geometry);
                TEST_PROGRESS.put(player.getUUID(), new TestProgress(10, 0));
            } else if (progress.stage == 5 && progress.ticks % 20 == 0) {
                // The fixture is placed before the client finishes changing
                // from its showcase chart to the seam chart. Repeat the block
                // update while it settles so the interaction probe observes
                // the canonical block in the currently active presentation.
                BlockPos seamBlock = new BlockPos(2, 119, 0);
                player.connection.send(new ClientboundBlockUpdatePacket(world, seamBlock));
                TEST_PROGRESS.put(player.getUUID(), new TestProgress(5, progress.ticks + 1));
            } else if (progress.stage == 10 && progress.ticks >= 240) {
                int packetFolds = PLAYER_SEAM_CROSSINGS.getOrDefault(player.getUUID(), 0);
                RingWorldMod.LOGGER.info("[test] presentation packets folded into canonical plane={}",
                        packetFolds);
                RingWorldMod.LOGGER.info("[test] canonical server player x={}, inPlane={}", player.getX(),
                        player.getX() >= 0.0 && player.getX() < geometry.circumferenceBlocks());
                boolean seamInteraction = world.getBlockState(new BlockPos(2, 119, 0)).isAir();
                RingWorldMod.LOGGER.info("[test] seam block interaction={}", seamInteraction);
                var periodicEntities = world.getEntities(player,
                                player.getBoundingBox().inflate(4.0),
                                entity -> entity instanceof ItemEntity item && item.getItem().is(Items.DIAMOND));
                var seamEntity = world.getEntity(TEST_SEAM_ENTITIES.getOrDefault(player.getUUID(), -1));
                RingWorldMod.LOGGER.info("[test] periodic seam entity query={}, count={}, storedEntityX={}",
                        periodicEntities.size() == 1, periodicEntities.size(),
                        seamEntity == null ? Double.NaN : seamEntity.getX());
                var movingEntity = world.getEntity(TEST_MOVING_ENTITIES.getOrDefault(player.getUUID(), -1));
                RingWorldMod.LOGGER.info("[test] moving seam entity canonical={}, x={}",
                        movingEntity != null && movingEntity.getX() >= 0.0
                                && movingEntity.getX() < geometry.circumferenceBlocks(),
                        movingEntity == null ? Double.NaN : movingEntity.getX());
                RingWorldMod.LOGGER.info("[test] non-canonical chunk-holder requests={}",
                        NON_CANONICAL_HOLDER_REQUESTS.get());
                logGameplaySeamProbes(world, player, geometry);
                TEST_PROGRESS.put(player.getUUID(), new TestProgress(6, 0));
            } else if (progress.stage == 6
                    && player.getX() >= geometry.circumferenceBlocks() - 16.0) {
                // The client has traversed the actual canonical plane rather
                // than merely sending several packets from one presentation
                // chart. Wait for the following low-X pose before declaring a
                // complete second circuit.
                TEST_PROGRESS.put(player.getUUID(), new TestProgress(11, 0));
            } else if (progress.stage == 6 && progress.ticks >= 12_000) {
                RingWorldMod.LOGGER.warn(
                        "[test] timed out before reaching the far side of the second circuit at x={}",
                        player.getX());
                TEST_PROGRESS.put(player.getUUID(), new TestProgress(11, 12_000));
            } else if (progress.stage == 11
                    && (player.getX() < 16.0 || progress.ticks >= 12_000)) {
                var reloadedMovingEntities = world.getEntities(player,
                        player.getBoundingBox().inflate(8.0),
                        entity -> entity instanceof ItemEntity item && item.getItem().is(Items.EMERALD));
                var movingEntity = reloadedMovingEntities.isEmpty() ? null : reloadedMovingEntities.getFirst();
                boolean lateMovingEntity = reloadedMovingEntities.size() == 1
                        && Math.abs(geometry.shortestCircumferenceDelta(player.getX(), movingEntity.getX())) < 8.0;
                AABB wrappedBlockBox = new AABB(
                        3.0, 119.0, 3.0,
                        4.0, 120.0, 4.0);
                boolean periodicBlockCollision = world.getBlockCollisions(player, wrappedBlockBox)
                        .iterator().hasNext();
                RingWorldMod.LOGGER.info("[test] second canonical seam circuit={}, x={}, inPlane={}",
                        player.getX() < 16.0, player.getX(),
                        player.getX() >= 0.0 && player.getX() < geometry.circumferenceBlocks());
                RingWorldMod.LOGGER.info("[test] late moving entity tracking={}, count={}, reloadedX={}",
                        lateMovingEntity, reloadedMovingEntities.size(),
                        movingEntity == null ? Double.NaN : movingEntity.getX());
                RingWorldMod.LOGGER.info("[test] periodic block collision={}", periodicBlockCollision);
                world.setBlock(new BlockPos(3, 119, 3), Blocks.AIR.defaultBlockState(), 3);
                TEST_PROGRESS.put(player.getUUID(), new TestProgress(9, 0));
            } else if (progress.stage == 9 && progress.ticks >= 100) {
                double boundaryZ = geometry.minWidthZ() + 7.5;
                player.teleportTo(world, 100.5, 106.0, boundaryZ,
                        Set.<Relative>of(), 180.0f, 58.0f, false);
                RingWorldMod.LOGGER.info("[test] rim stress view armed at z={}", boundaryZ);
                TEST_PROGRESS.put(player.getUUID(), new TestProgress(7, 0));
            } else if (progress.stage == 7 && progress.ticks >= 400) {
                boolean exteriorVoid = world.getBlockState(
                        new BlockPos(100, 64, geometry.minWidthZ() - 32)).isAir();
                boolean rimPresent = RingGenerationBoundary.isRimMaterial(world.getBlockState(
                        new BlockPos(100, 64, geometry.minWidthZ())))
                        && RingGenerationBoundary.isRimMaterial(world.getBlockState(
                        new BlockPos(100, 64,
                                geometry.minWidthZ() + RingGenerationBoundary.RIM_THICKNESS - 1)));
                int rimTop = world.getMinY() + RingWorldSettings.get(world).wallHeightBlocks();
                boolean shortenedRimTopClear = !RingGenerationBoundary.isRimMaterial(
                        world.getBlockState(new BlockPos(100, rimTop, geometry.minWidthZ())));
                RingWorldMod.LOGGER.info("[test] async boundary exteriorVoid={}, texturedRimPresent={}, shortenedTopClear={}",
                        exteriorVoid, rimPresent, shortenedRimTopClear);
                // Leave a completed test world in a useful playable pose:
                // one short walk before the circumference seam, not staring
                // into the deliberately tall rim wall.
                player.teleportTo(world, geometry.circumferenceBlocks() - 8.0, 120.0, 0.5,
                        Set.<Relative>of(), -90.0f, 22.0f, false);
                world.getServer().saveEverything(false, true, false);
                RingWorldMod.LOGGER.info("[test] playable pre-seam pose saved at canonical x={}", player.getX());
                TEST_PROGRESS.put(player.getUUID(), new TestProgress(8, 0));
            } else if (progress.stage != 8) {
                TEST_PROGRESS.put(player.getUUID(), new TestProgress(progress.stage, progress.ticks + 1));
            }
        }
    }

    /** Keeps the automated screenshot out of a tree or a tall terrain spire. */
    private static void clearTestCameraSpace(ServerLevel world, int y) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int clearY = y - 3; clearY <= y + 5; clearY++) {
                    world.setBlock(new BlockPos(x, clearY, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    /** Keeps high random terrain from blocking the test-only seam flight lane. */
    private static void clearTestSeamFlightPath(ServerLevel world, RingGeometry geometry, int y) {
        for (int offset = -16; offset <= 16; offset++) {
            int x = geometry.wrapBlockX(geometry.circumferenceBlocks() + offset);
            for (int z = -4; z <= 4; z++) {
                for (int clearY = y - 3; clearY <= y + 5; clearY++) {
                    world.setBlock(new BlockPos(x, clearY, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    /** Arms representative engine paths that must treat the seam as ordinary adjacency. */
    private static void armGameplaySeamProbes(ServerLevel world, ServerPlayer player,
                                               RingGeometry geometry) {
        int circumference = geometry.circumferenceBlocks();

        // Projectile -> entity collision. The target stays in canonical chunk
        // zero while the arrow crosses into the same canonical plane.
        Zombie projectileTarget = new Zombie(world);
        projectileTarget.setPos(2.5, 200.0, 8.5);
        projectileTarget.setNoGravity(true);
        projectileTarget.setNoAi(true);
        projectileTarget.setPersistenceRequired();
        world.addFreshEntity(projectileTarget);
        TEST_PROJECTILE_TARGETS.put(player.getUUID(), projectileTarget.getId());
        for (int x = circumference - 4; x <= circumference + 5; x++) {
            for (int y = 198; y <= 203; y++) {
                for (int z = 7; z <= 9; z++) {
                    world.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        Arrow arrow = new Arrow(world, circumference - 2.5, 200.9, 8.5,
                new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0.55, 0.0, 0.0);
        boolean arrowSpawned = world.addFreshEntity(arrow);
        TEST_PROJECTILES.put(player.getUUID(), arrow.getId());
        RingWorldMod.LOGGER.info("[test] periodic projectile spawned={}, id={}", arrowSpawned, arrow.getId());

        // An unoccupied vehicle exercises the same periodic entity motion
        // and client tracking path with vehicle-specific physics enabled.
        Boat boat = EntityType.OAK_BOAT.create(world, EntitySpawnReason.COMMAND);
        if (boat != null) {
            boat.setPos(circumference - 1.5, 123.0, 16.5);
            boat.setNoGravity(true);
            boat.setDeltaMovement(0.25, 0.0, 0.0);
            world.addFreshEntity(boat);
            TEST_VEHICLES.put(player.getUUID(), boat.getId());
        }

        // Give a normal ground navigator a short path whose requested target
        // is stored in chunk zero but whose nearest image is just beyond C.
        for (int x = circumference - 8; x <= circumference + 4; x++) {
            for (int z = 11; z <= 13; z++) {
                world.setBlock(new BlockPos(x, 119, z), Blocks.STONE.defaultBlockState(), 2);
                world.setBlock(new BlockPos(x, 120, z), Blocks.AIR.defaultBlockState(), 2);
                world.setBlock(new BlockPos(x, 121, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        Zombie navigator = new Zombie(world);
        navigator.setPos(circumference - 5.5, 120.0, 12.5);
        navigator.setPersistenceRequired();
        world.addFreshEntity(navigator);
        // A freshly spawned ground mob has not completed its first physics
        // tick yet, so mark the already-supported pose as grounded before
        // requesting the path.
        navigator.setOnGround(true);
        boolean navigationStarted = navigator.getNavigation().moveTo(2.5, 120.0, 12.5, 1.0);
        RingWorldMod.LOGGER.info("[test] AI periodic path created={}", navigationStarted);
        TEST_AI_MOBS.put(player.getUUID(), navigator.getId());

        // A water source has one open outlet through the canonical seam. Its
        // resulting tick is stored under chunk zero by the scheduler mixins.
        BlockPos waterSource = new BlockPos(circumference - 1, 120, 20);
        for (int x = circumference - 2; x <= circumference; x++) {
            world.setBlock(new BlockPos(x, 119, 20), Blocks.STONE.defaultBlockState(), 2);
        }
        world.setBlock(new BlockPos(circumference - 2, 120, 20), Blocks.STONE.defaultBlockState(), 2);
        world.setBlock(new BlockPos(circumference - 1, 120, 19), Blocks.STONE.defaultBlockState(), 2);
        world.setBlock(new BlockPos(circumference - 1, 120, 21), Blocks.STONE.defaultBlockState(), 2);
        world.setBlock(new BlockPos(circumference, 120, 20), Blocks.AIR.defaultBlockState(), 2);
        world.setBlock(waterSource, Blocks.WATER.defaultBlockState(), 3);
        world.scheduleTick(waterSource, Fluids.WATER, 1);

        // Explosion reach and exposure must use the target's nearby image,
        // not a ray almost one circumference long through unrelated terrain.
        ItemEntity blastItem = new ItemEntity(world, 2.5, 121.0, 26.5,
                new ItemStack(Items.NETHER_STAR));
        blastItem.setNoGravity(true);
        blastItem.setNeverPickUp();
        world.addFreshEntity(blastItem);
        TEST_EXPLOSION_ENTITIES.put(player.getUUID(), blastItem.getId());
        world.explode(null, circumference - 1.0, 121.0, 26.5,
                2.0F, Level.ExplosionInteraction.NONE);

        // Also exercise proximity delivery and client presentation remapping for a
        // canonical particle position adjacent to the client's nearby presentation.
        world.sendParticles(ParticleTypes.HAPPY_VILLAGER, 2.5, 123.0, 0.5,
                12, 0.2, 0.2, 0.2, 0.0);
    }

    private static void logGameplaySeamProbes(ServerLevel world, ServerPlayer player,
                                               RingGeometry geometry) {
        Entity target = world.getEntity(TEST_PROJECTILE_TARGETS.getOrDefault(player.getUUID(), -1));
        Entity projectile = world.getEntity(TEST_PROJECTILES.getOrDefault(player.getUUID(), -1));
        boolean projectileHit = target instanceof Zombie zombie
                && zombie.getHealth() < zombie.getMaxHealth();
        Entity vehicle = world.getEntity(TEST_VEHICLES.getOrDefault(player.getUUID(), -1));
        boolean vehicleCrossed = vehicle != null && vehicle.getX() >= 0.0
                && vehicle.getX() < geometry.circumferenceBlocks() / 2.0;
        Entity navigator = world.getEntity(TEST_AI_MOBS.getOrDefault(player.getUUID(), -1));
        boolean aiCrossed = navigator != null && navigator.getX() >= 0.0
                && navigator.getX() < geometry.circumferenceBlocks() / 2.0;
        boolean fluidCrossed = !world.getFluidState(new BlockPos(0, 120, 20)).isEmpty();
        Entity blastItem = world.getEntity(TEST_EXPLOSION_ENTITIES.getOrDefault(player.getUUID(), -1));
        boolean explosionAffected = blastItem != null
                && (blastItem.getDeltaMovement().lengthSqr() > 0.0
                || blastItem.distanceToSqr(2.5, 121.0, 26.5) > 0.0001);
        RingWorldMod.LOGGER.info("[test] projectile entity collision across seam={}, targetHealth={}",
                projectileHit, target instanceof Zombie zombie ? zombie.getHealth() : Float.NaN);
        RingWorldMod.LOGGER.info(
                "[test] projectile state present={}, x={}, y={}, velocity={}, age={}, chunk={}, tickEligible={}",
                projectile != null, projectile == null ? Double.NaN : projectile.getX(),
                projectile == null ? Double.NaN : projectile.getY(),
                projectile == null ? Vec3.ZERO : projectile.getDeltaMovement(),
                projectile == null ? -1 : projectile.tickCount,
                projectile == null ? "missing" : projectile.chunkPosition(),
                projectile != null && world.isPositionEntityTicking(projectile.blockPosition()));
        RingWorldMod.LOGGER.info("[test] vehicle canonical across seam={}, x={}",
                vehicleCrossed, vehicle == null ? Double.NaN : vehicle.getX());
        RingWorldMod.LOGGER.info("[test] AI nearest-image navigation across seam={}, x={}",
                aiCrossed, navigator == null ? Double.NaN : navigator.getX());
        RingWorldMod.LOGGER.info("[test] fluid flow across seam={}", fluidCrossed);
        RingWorldMod.LOGGER.info("[test] explosion reaches entity across seam={}, x={}, vx={}", explosionAffected,
                blastItem == null ? Double.NaN : blastItem.getX(),
                blastItem == null ? Double.NaN : blastItem.getDeltaMovement().x);
    }
}
