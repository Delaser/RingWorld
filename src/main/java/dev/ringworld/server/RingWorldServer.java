package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.net.RingWorldNetworking;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingWorldGeneratorAccess;
import dev.ringworld.world.RingWorldSettings;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;

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
    private static final Map<ServerWorld, RingGeometry> WORLD_GEOMETRY = new IdentityHashMap<>();
    private static final Map<UUID, Integer> TEST_MOVING_ENTITIES = new HashMap<>();
    private static final Map<UUID, Integer> TEST_SEAM_ENTITIES = new HashMap<>();
    private static final Map<UUID, Integer> TEST_PROJECTILE_TARGETS = new HashMap<>();
    private static final Map<UUID, Integer> TEST_PROJECTILES = new HashMap<>();
    private static final Map<UUID, Integer> TEST_VEHICLES = new HashMap<>();
    private static final Map<UUID, Integer> TEST_AI_MOBS = new HashMap<>();
    private static final Map<UUID, Integer> TEST_EXPLOSION_ENTITIES = new HashMap<>();
    private static final Map<ServerWorld, LinkedHashMap<Long, net.minecraft.world.chunk.WorldChunk>>
            PENDING_LEGACY_RIM_MIGRATIONS = new IdentityHashMap<>();
    private static final AtomicLong NON_CANONICAL_HOLDER_REQUESTS = new AtomicLong();

    private record TestProgress(int stage, int ticks) { }
    private RingWorldServer() { }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(RingWorldServer::tickRingWorld);
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (!isOverworld(world)) return;
            RingTerrainAtlasServer.captureLoadedChunk(world, chunk);
            // A WorldChunk is not safe to mutate from inside its own load
            // callback. Doing so can re-enter ServerChunkManager and park the
            // server thread waiting for the future that is currently firing
            // this callback. Queue it for the end of a later world tick.
            if (RingGenerationBoundary.containsRim(chunk, geometryFor(world))) {
                PENDING_LEGACY_RIM_MIGRATIONS
                        .computeIfAbsent(world, unused -> new LinkedHashMap<>())
                        .putIfAbsent(chunk.getPos().toLong(), chunk);
            }
        });
        ServerWorldEvents.LOAD.register((server, world) -> attachPersistedGeometry(world));
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            RingTerrainAtlasServer.unload(world);
            WORLD_GEOMETRY.remove(world);
            PENDING_LEGACY_RIM_MIGRATIONS.remove(world);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            RingWorldMultiplayerTest.prepareWaitingPlayer(handler.player);
            server.execute(() -> rescueEmbeddedPlayer(handler.player));
        });
        RingWorldNetworking.registerServer();
    }

    private static boolean isOverworld(ServerWorld world) {
        return world.getRegistryKey() == World.OVERWORLD;
    }

    /** Called after ServerWorld owns its chunk manager and persistent state is available. */
    private static void attachPersistedGeometry(ServerWorld world) {
        if (!isOverworld(world)) return;
        RingWorldSettings settings = RingWorldSettings.get(world);
        RingGeometry geometry = settings.geometry();
        WORLD_GEOMETRY.put(world, geometry);
        // Ring dimensions remain immutable. Rim height and style are
        // decorative, so existing worlds intentionally follow current config.
        attachGeneratorSettings(world, geometry, RingWorldConfig.load().wallHeightBlocks());
        RingTerrainAtlasServer.load(world);
    }

    /** Safe during ServerChunkManager construction: only bootstrap config may be read then. */
    public static void attachBootstrapGeometry(ChunkGenerator generator) {
        RingWorldConfig config = RingWorldConfig.load();
        if (generator instanceof RingWorldGeneratorAccess access) {
            access.ringworld$setGeometry(new RingGeometry(config.widthBlocks(), config.circumferenceBlocks()));
            access.ringworld$setWallHeight(config.wallHeightBlocks());
        }
    }

    private static void attachGeneratorSettings(ServerWorld world, RingGeometry geometry, int wallHeightBlocks) {
        ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
        if (generator instanceof RingWorldGeneratorAccess access) {
            access.ringworld$setGeometry(geometry);
            access.ringworld$setWallHeight(wallHeightBlocks);
        }
    }

    /** Allocation-free geometry lookup for chunk and network hot paths. */
    public static RingGeometry geometryFor(ServerWorld world) {
        return WORLD_GEOMETRY.computeIfAbsent(world, unused -> RingWorldSettings.get(world).geometry());
    }

    public static void recordNonCanonicalHolderRequest() {
        NON_CANONICAL_HOLDER_REQUESTS.incrementAndGet();
    }

    /** Test instrumentation for packet-backed crossings; never persisted. */
    public static void recordPlayerCanonicalWrap(ServerPlayerEntity player) {
        if (!RingWorldConfig.load().testMode()) return;
        long tick = player.getEntityWorld().getTime();
        Long previousTick = LAST_PLAYER_SEAM_CROSSING_TICK.put(player.getUuid(), tick);
        if (previousTick != null && previousTick == tick) return;
        PLAYER_SEAM_CROSSINGS.merge(player.getUuid(), 1, Integer::sum);
    }

    private static void tickRingWorld(ServerWorld world) {
        if (!isOverworld(world)) return;
        RingGeometry geometry = geometryFor(world);
        // The server owns exactly one circumference plane. Clients may keep a
        // nearby presentation image for smooth seam rendering, but no entity
        // position outside [0, C) survives the authoritative tick boundary.
        for (Entity entity : world.iterateEntities()) {
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
        if (!(entity.getEntityWorld() instanceof ServerWorld world) || !isOverworld(world)) return 0.0;
        double sourceX = entity.getX();
        double canonicalX = geometry.wrapX(sourceX);
        if (canonicalX == sourceX) return 0.0;
        entity.setPosition(canonicalX, entity.getY(), entity.getZ());
        return canonicalX - sourceX;
    }

    /**
     * Migrates at most one loaded boundary chunk per tick. Besides avoiding
     * chunk-load reentrancy, the limit keeps an old world's first login from
     * turning hundreds of rim conversions into one watchdog-sized tick.
     */
    private static void migrateOneLegacyRimChunk(ServerWorld world, RingGeometry geometry) {
        LinkedHashMap<Long, net.minecraft.world.chunk.WorldChunk> pending =
                PENDING_LEGACY_RIM_MIGRATIONS.get(world);
        if (pending == null || pending.isEmpty()) return;

        var iterator = pending.entrySet().iterator();
        var entry = iterator.next();
        iterator.remove();

        long started = System.nanoTime();
        boolean migrated = RingGenerationBoundary.migrateLegacyRim(entry.getValue(), geometry,
                RingWorldConfig.load().wallHeightBlocks());
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
    private static void rescueEmbeddedPlayer(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        if (!isOverworld(world)) return;
        RingWorldSettings settings = RingWorldSettings.get(world);
        RingWorldMod.LOGGER.info("[diagnostic] joined ring world at x={}, y={}, z={}; width={}, circumference={}, seed={}, format={}",
                player.getX(), player.getY(), player.getZ(), settings.widthBlocks(),
                settings.circumferenceBlocks(), settings.generatorSeed(), settings.formatVersion());
        if (world.isSpaceEmpty(player)) return;

        int blockX = (int) Math.floor(player.getX());
        int blockZ = (int) Math.floor(player.getZ());
        int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        double targetY = Math.max(surfaceY, Math.floor(player.getY()) + 1.0);

        while (targetY < world.getTopYInclusive()
                && !world.isSpaceEmpty(player,
                player.getBoundingBox().offset(0.0, targetY - player.getY(), 0.0))) {
            targetY += 1.0;
        }
        if (targetY >= world.getTopYInclusive()) {
            RingWorldMod.LOGGER.warn("Could not find an unobstructed join pose for {} at {}, {}, {}",
                    player.getName().getString(), player.getX(), player.getY(), player.getZ());
            return;
        }

        double sourceY = player.getY();
        player.teleport(world, player.getX(), targetY, player.getZ(),
                Set.<PositionFlag>of(), player.getYaw(), player.getPitch(), false);
        player.setVelocity(Vec3d.ZERO);
        RingWorldMod.LOGGER.warn("Rescued embedded player {} from y={} to y={} at x={}, z={}",
                player.getName().getString(), sourceY, targetY, player.getX(), player.getZ());
    }

    /** Local-only smoke test, activated solely by testMode=true in ringworld.properties. */
    private static void runAutomatedTest(ServerWorld world, RingGeometry geometry) {
        if (!RingWorldConfig.load().testMode()) return;
        for (ServerPlayerEntity player : world.getPlayers()) {
            TestProgress progress = TEST_PROGRESS.getOrDefault(player.getUuid(), new TestProgress(0, 0));
            if (progress.stage == 0) {
                // A vanilla spawn may initially arrive in the negative image
                // of the ring and be canonicalised before this probe starts.
                // Count only the deliberate packet-backed traversal below.
                PLAYER_SEAM_CROSSINGS.remove(player.getUuid());
                LAST_PLAYER_SEAM_CROSSING_TICK.remove(player.getUuid());
                TEST_MOVING_ENTITIES.remove(player.getUuid());
                TEST_SEAM_ENTITIES.remove(player.getUuid());
                TEST_PROJECTILE_TARGETS.remove(player.getUuid());
                TEST_PROJECTILES.remove(player.getUuid());
                TEST_VEHICLES.remove(player.getUuid());
                TEST_AI_MOBS.remove(player.getUuid());
                TEST_EXPLOSION_ENTITIES.remove(player.getUuid());
                NON_CANONICAL_HOLDER_REQUESTS.set(0L);
                player.changeGameMode(GameMode.CREATIVE);
                player.getAbilities().flying = true;
                player.sendAbilitiesUpdate();
                RingWorldMod.LOGGER.info("[test] creative mode enabled for {}", player.getName().getString());
                TEST_PROGRESS.put(player.getUuid(), new TestProgress(1, 0));
            } else if (progress.stage == 1 && progress.ticks >= 140) {
                // Delay until after the vanilla join-position packet; otherwise
                // it overwrites this showcase teleport on the client.
                // Use a terrain-relative altitude. The old fixed Y=120 camera
                // could land inside an amplified mountain on an otherwise
                // valid random seed and turn every visual capture black.
                int terrainTop = world.getTopY(Heightmap.Type.WORLD_SURFACE, 0, 0);
                int showcaseY = Math.min(world.getTopYInclusive() - 16,
                        Math.max(120, terrainTop + 32));
                clearTestCameraSpace(world, showcaseY);
                // Minecraft yaw 90 faces along canonical X, the periodic ring
                // circumference; the small downward pitch includes the band.
                player.teleport(world, 0.5, showcaseY, 0.5, Set.<PositionFlag>of(), 90.0f, 22.0f, false);
                RingWorldMod.LOGGER.info("[test] showcase camera at y={}", showcaseY);
                TEST_PROGRESS.put(player.getUuid(), new TestProgress(2, 0));
            } else if (progress.stage == 2 && progress.ticks >= 40) {
                int terrainTopAtSpawn = world.getTopY(Heightmap.Type.WORLD_SURFACE, 0, 0);
                boolean terrainPresent = terrainTopAtSpawn > world.getBottomY();
                RingWorldMod.LOGGER.info("[test] terrain={}, terrainTopSpawn={}", terrainPresent, terrainTopAtSpawn);
                boolean wrapped = geometry.wrapX(geometry.circumferenceBlocks() + 0.5) == 0.5;
                RingWorldMod.LOGGER.info("[test] circumference coordinate wrap={}", wrapped);
                TEST_PROGRESS.put(player.getUuid(), new TestProgress(4, 0));
            } else if (progress.stage == 4 && progress.ticks >= 700) {
                // Let the normal render smoke test settle first, then put the
                // client eight blocks before the seam. The client waits for
                // the periodic chunks ahead to be meshed, then walks across
                // over several ticks. This exercises real movement prediction
                // and one canonical server wrap instead of disguising a hitch inside
                // a direct four-block test jump.
                clearTestSeamFlightPath(world, geometry, 120);
                player.teleport(world, geometry.circumferenceBlocks() - 8.0, 120.0, 0.5,
                        Set.<PositionFlag>of(), 90.0f, 22.0f, false);
                // Send the logical target after the teleport packet so the
                // client maps it into its nearby seam presentation (x=C+2).
                world.setBlockState(new BlockPos(2, 119, 0), Blocks.GOLD_BLOCK.getDefaultState(), 3);
                world.setBlockState(new BlockPos(3, 119, 3), Blocks.STONE.getDefaultState(), 3);
                ItemEntity seamItem = new ItemEntity(world, 2.5, 120.0, 2.5, new ItemStack(Items.DIAMOND));
                seamItem.setNoGravity(true);
                seamItem.setPickupDelayInfinite();
                seamItem.setVelocity(Vec3d.ZERO);
                world.spawnEntity(seamItem);
                TEST_SEAM_ENTITIES.put(player.getUuid(), seamItem.getId());
                ItemEntity movingSeamItem = new ItemEntity(world,
                        geometry.circumferenceBlocks() - 1.5, 120.0, 2.5,
                        new ItemStack(Items.EMERALD));
                movingSeamItem.setNoGravity(true);
                movingSeamItem.setPickupDelayInfinite();
                movingSeamItem.setVelocity(0.15, 0.0, 0.0);
                world.spawnEntity(movingSeamItem);
                TEST_MOVING_ENTITIES.put(player.getUuid(), movingSeamItem.getId());
                RingWorldMod.LOGGER.info("[test] seam traversal armed at x={}", player.getX());
                TEST_PROGRESS.put(player.getUuid(), new TestProgress(5, 0));
            } else if (progress.stage == 5
                    && (world.getBlockState(new BlockPos(2, 119, 0)).isAir()
                    || progress.ticks >= 3_600)) {
                // The acknowledgement also proves the seam-side chunks are
                // active. Start a fresh interval so projectile/AI assertions
                // measure active simulation ticks rather than prefetch time.
                armGameplaySeamProbes(world, player, geometry);
                TEST_PROGRESS.put(player.getUuid(), new TestProgress(10, 0));
            } else if (progress.stage == 5 && progress.ticks % 20 == 0) {
                // The fixture is placed before the client finishes changing
                // from its showcase chart to the seam chart. Repeat the block
                // update while it settles so the interaction probe observes
                // the canonical block in the currently active presentation.
                BlockPos seamBlock = new BlockPos(2, 119, 0);
                player.networkHandler.sendPacket(new BlockUpdateS2CPacket(world, seamBlock));
                TEST_PROGRESS.put(player.getUuid(), new TestProgress(5, progress.ticks + 1));
            } else if (progress.stage == 10 && progress.ticks >= 40) {
                int crossings = PLAYER_SEAM_CROSSINGS.getOrDefault(player.getUuid(), 0);
                RingWorldMod.LOGGER.info("[test] canonical server seam wraps={}", crossings);
                RingWorldMod.LOGGER.info("[test] canonical server player x={}, inPlane={}", player.getX(),
                        player.getX() >= 0.0 && player.getX() < geometry.circumferenceBlocks());
                boolean seamInteraction = world.getBlockState(new BlockPos(2, 119, 0)).isAir();
                RingWorldMod.LOGGER.info("[test] seam block interaction={}", seamInteraction);
                var periodicEntities = world.getOtherEntities(player,
                                player.getBoundingBox().expand(4.0),
                                entity -> entity instanceof ItemEntity item && item.getStack().isOf(Items.DIAMOND));
                var seamEntity = world.getEntityById(TEST_SEAM_ENTITIES.getOrDefault(player.getUuid(), -1));
                RingWorldMod.LOGGER.info("[test] periodic seam entity query={}, count={}, storedEntityX={}",
                        periodicEntities.size() == 1, periodicEntities.size(),
                        seamEntity == null ? Double.NaN : seamEntity.getX());
                var movingEntity = world.getEntityById(TEST_MOVING_ENTITIES.getOrDefault(player.getUuid(), -1));
                RingWorldMod.LOGGER.info("[test] moving seam entity canonical={}, x={}",
                        movingEntity != null && movingEntity.getX() >= 0.0
                                && movingEntity.getX() < geometry.circumferenceBlocks(),
                        movingEntity == null ? Double.NaN : movingEntity.getX());
                RingWorldMod.LOGGER.info("[test] non-canonical chunk-holder requests={}",
                        NON_CANONICAL_HOLDER_REQUESTS.get());
                logGameplaySeamProbes(world, player, geometry);
                TEST_PROGRESS.put(player.getUuid(), new TestProgress(6, 0));
            } else if (progress.stage == 6
                    && (PLAYER_SEAM_CROSSINGS.getOrDefault(player.getUuid(), 0) >= 2
                    || progress.ticks >= 12_000)) {
                var reloadedMovingEntities = world.getOtherEntities(player,
                        player.getBoundingBox().expand(8.0),
                        entity -> entity instanceof ItemEntity item && item.getStack().isOf(Items.EMERALD));
                var movingEntity = reloadedMovingEntities.isEmpty() ? null : reloadedMovingEntities.getFirst();
                boolean lateMovingEntity = reloadedMovingEntities.size() == 1
                        && Math.abs(geometry.shortestCircumferenceDelta(player.getX(), movingEntity.getX())) < 8.0;
                Box wrappedBlockBox = new Box(
                        3.0, 119.0, 3.0,
                        4.0, 120.0, 4.0);
                boolean periodicBlockCollision = world.getBlockCollisions(player, wrappedBlockBox)
                        .iterator().hasNext();
                RingWorldMod.LOGGER.info("[test] second canonical seam wrap={}, count={}, x={}, inPlane={}",
                        PLAYER_SEAM_CROSSINGS.getOrDefault(player.getUuid(), 0) >= 2,
                        PLAYER_SEAM_CROSSINGS.getOrDefault(player.getUuid(), 0), player.getX(),
                        player.getX() >= 0.0 && player.getX() < geometry.circumferenceBlocks());
                RingWorldMod.LOGGER.info("[test] late moving entity tracking={}, count={}, reloadedX={}",
                        lateMovingEntity, reloadedMovingEntities.size(),
                        movingEntity == null ? Double.NaN : movingEntity.getX());
                RingWorldMod.LOGGER.info("[test] periodic block collision={}", periodicBlockCollision);
                world.setBlockState(new BlockPos(3, 119, 3), Blocks.AIR.getDefaultState(), 3);
                TEST_PROGRESS.put(player.getUuid(), new TestProgress(9, 0));
            } else if (progress.stage == 9 && progress.ticks >= 100) {
                double boundaryZ = geometry.minWidthZ() + 7.5;
                player.teleport(world, 100.5, 106.0, boundaryZ,
                        Set.<PositionFlag>of(), 180.0f, 58.0f, false);
                RingWorldMod.LOGGER.info("[test] rim stress view armed at z={}", boundaryZ);
                TEST_PROGRESS.put(player.getUuid(), new TestProgress(7, 0));
            } else if (progress.stage == 7 && progress.ticks >= 400) {
                boolean exteriorVoid = world.getBlockState(
                        new BlockPos(100, 64, geometry.minWidthZ() - 32)).isAir();
                boolean rimPresent = RingGenerationBoundary.isRimMaterial(world.getBlockState(
                        new BlockPos(100, 64, geometry.minWidthZ())))
                        && RingGenerationBoundary.isRimMaterial(world.getBlockState(
                        new BlockPos(100, 64,
                                geometry.minWidthZ() + RingGenerationBoundary.RIM_THICKNESS - 1)));
                int rimTop = world.getBottomY() + RingWorldConfig.load().wallHeightBlocks();
                boolean shortenedRimTopClear = !RingGenerationBoundary.isRimMaterial(
                        world.getBlockState(new BlockPos(100, rimTop, geometry.minWidthZ())));
                RingWorldMod.LOGGER.info("[test] async boundary exteriorVoid={}, texturedRimPresent={}, shortenedTopClear={}",
                        exteriorVoid, rimPresent, shortenedRimTopClear);
                // Leave a completed test world in a useful playable pose:
                // one short walk before the circumference seam, not staring
                // into the deliberately tall rim wall.
                player.teleport(world, geometry.circumferenceBlocks() - 8.0, 120.0, 0.5,
                        Set.<PositionFlag>of(), -90.0f, 22.0f, false);
                world.getServer().saveAll(false, true, false);
                RingWorldMod.LOGGER.info("[test] playable pre-seam pose saved at canonical x={}", player.getX());
                TEST_PROGRESS.put(player.getUuid(), new TestProgress(8, 0));
            } else if (progress.stage != 8) {
                TEST_PROGRESS.put(player.getUuid(), new TestProgress(progress.stage, progress.ticks + 1));
            }
        }
    }

    /** Keeps the automated screenshot out of a tree or a tall terrain spire. */
    private static void clearTestCameraSpace(ServerWorld world, int y) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int clearY = y - 3; clearY <= y + 5; clearY++) {
                    world.setBlockState(new BlockPos(x, clearY, z), Blocks.AIR.getDefaultState(), 2);
                }
            }
        }
    }

    /** Keeps high random terrain from blocking the test-only seam flight lane. */
    private static void clearTestSeamFlightPath(ServerWorld world, RingGeometry geometry, int y) {
        for (int offset = -16; offset <= 16; offset++) {
            int x = geometry.wrapBlockX(geometry.circumferenceBlocks() + offset);
            for (int z = -4; z <= 4; z++) {
                for (int clearY = y - 3; clearY <= y + 5; clearY++) {
                    world.setBlockState(new BlockPos(x, clearY, z), Blocks.AIR.getDefaultState(), 2);
                }
            }
        }
    }

    /** Arms representative engine paths that must treat the seam as ordinary adjacency. */
    private static void armGameplaySeamProbes(ServerWorld world, ServerPlayerEntity player,
                                               RingGeometry geometry) {
        int circumference = geometry.circumferenceBlocks();

        // Projectile -> entity collision. The target stays in canonical chunk
        // zero while the arrow crosses into the same canonical plane.
        ZombieEntity projectileTarget = new ZombieEntity(world);
        projectileTarget.setPosition(2.5, 200.0, 8.5);
        projectileTarget.setNoGravity(true);
        projectileTarget.setAiDisabled(true);
        projectileTarget.setPersistent();
        world.spawnEntity(projectileTarget);
        TEST_PROJECTILE_TARGETS.put(player.getUuid(), projectileTarget.getId());
        for (int x = circumference - 4; x <= circumference + 5; x++) {
            for (int y = 198; y <= 203; y++) {
                for (int z = 7; z <= 9; z++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 2);
                }
            }
        }
        ArrowEntity arrow = new ArrowEntity(world, circumference - 2.5, 200.9, 8.5,
                new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        arrow.setNoGravity(true);
        arrow.setVelocity(0.55, 0.0, 0.0);
        boolean arrowSpawned = world.spawnEntity(arrow);
        TEST_PROJECTILES.put(player.getUuid(), arrow.getId());
        RingWorldMod.LOGGER.info("[test] periodic projectile spawned={}, id={}", arrowSpawned, arrow.getId());

        // An unoccupied vehicle exercises the same periodic entity motion
        // and client tracking path with vehicle-specific physics enabled.
        BoatEntity boat = EntityType.OAK_BOAT.create(world, SpawnReason.COMMAND);
        if (boat != null) {
            boat.setPosition(circumference - 1.5, 123.0, 16.5);
            boat.setNoGravity(true);
            boat.setVelocity(0.25, 0.0, 0.0);
            world.spawnEntity(boat);
            TEST_VEHICLES.put(player.getUuid(), boat.getId());
        }

        // Give a normal ground navigator a short path whose requested target
        // is stored in chunk zero but whose nearest image is just beyond C.
        for (int x = circumference - 8; x <= circumference + 4; x++) {
            for (int z = 11; z <= 13; z++) {
                world.setBlockState(new BlockPos(x, 119, z), Blocks.STONE.getDefaultState(), 2);
                world.setBlockState(new BlockPos(x, 120, z), Blocks.AIR.getDefaultState(), 2);
                world.setBlockState(new BlockPos(x, 121, z), Blocks.AIR.getDefaultState(), 2);
            }
        }
        ZombieEntity navigator = new ZombieEntity(world);
        navigator.setPosition(circumference - 5.5, 120.0, 12.5);
        navigator.setPersistent();
        world.spawnEntity(navigator);
        // A freshly spawned ground mob has not completed its first physics
        // tick yet, so mark the already-supported pose as grounded before
        // requesting the path.
        navigator.setOnGround(true);
        boolean navigationStarted = navigator.getNavigation().startMovingTo(2.5, 120.0, 12.5, 1.0);
        RingWorldMod.LOGGER.info("[test] AI periodic path created={}", navigationStarted);
        TEST_AI_MOBS.put(player.getUuid(), navigator.getId());

        // A water source has one open outlet through the canonical seam. Its
        // resulting tick is stored under chunk zero by the scheduler mixins.
        BlockPos waterSource = new BlockPos(circumference - 1, 120, 20);
        for (int x = circumference - 2; x <= circumference; x++) {
            world.setBlockState(new BlockPos(x, 119, 20), Blocks.STONE.getDefaultState(), 2);
        }
        world.setBlockState(new BlockPos(circumference - 2, 120, 20), Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(new BlockPos(circumference - 1, 120, 19), Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(new BlockPos(circumference - 1, 120, 21), Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(new BlockPos(circumference, 120, 20), Blocks.AIR.getDefaultState(), 2);
        world.setBlockState(waterSource, Blocks.WATER.getDefaultState(), 3);
        world.scheduleFluidTick(waterSource, Fluids.WATER, 1);

        // Explosion reach and exposure must use the target's nearby image,
        // not a ray almost one circumference long through unrelated terrain.
        ItemEntity blastItem = new ItemEntity(world, 2.5, 121.0, 26.5,
                new ItemStack(Items.NETHER_STAR));
        blastItem.setNoGravity(true);
        blastItem.setPickupDelayInfinite();
        world.spawnEntity(blastItem);
        TEST_EXPLOSION_ENTITIES.put(player.getUuid(), blastItem.getId());
        world.createExplosion(null, circumference - 1.0, 121.0, 26.5,
                2.0F, World.ExplosionSourceType.NONE);

        // Also exercise proximity delivery and client presentation remapping for a
        // canonical particle position adjacent to the client's nearby presentation.
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, 2.5, 123.0, 0.5,
                12, 0.2, 0.2, 0.2, 0.0);
    }

    private static void logGameplaySeamProbes(ServerWorld world, ServerPlayerEntity player,
                                               RingGeometry geometry) {
        Entity target = world.getEntityById(TEST_PROJECTILE_TARGETS.getOrDefault(player.getUuid(), -1));
        Entity projectile = world.getEntityById(TEST_PROJECTILES.getOrDefault(player.getUuid(), -1));
        boolean projectileHit = target instanceof ZombieEntity zombie
                && zombie.getHealth() < zombie.getMaxHealth();
        Entity vehicle = world.getEntityById(TEST_VEHICLES.getOrDefault(player.getUuid(), -1));
        boolean vehicleCrossed = vehicle != null && vehicle.getX() >= 0.0
                && vehicle.getX() < geometry.circumferenceBlocks() / 2.0;
        Entity navigator = world.getEntityById(TEST_AI_MOBS.getOrDefault(player.getUuid(), -1));
        boolean aiCrossed = navigator != null && navigator.getX() >= 0.0
                && navigator.getX() < geometry.circumferenceBlocks() / 2.0;
        boolean fluidCrossed = !world.getFluidState(new BlockPos(0, 120, 20)).isEmpty();
        Entity blastItem = world.getEntityById(TEST_EXPLOSION_ENTITIES.getOrDefault(player.getUuid(), -1));
        boolean explosionAffected = blastItem != null
                && (blastItem.getVelocity().lengthSquared() > 0.0
                || blastItem.squaredDistanceTo(2.5, 121.0, 26.5) > 0.0001);
        RingWorldMod.LOGGER.info("[test] projectile entity collision across seam={}, targetHealth={}",
                projectileHit, target instanceof ZombieEntity zombie ? zombie.getHealth() : Float.NaN);
        RingWorldMod.LOGGER.info("[test] projectile state present={}, x={}, y={}, velocity={}",
                projectile != null, projectile == null ? Double.NaN : projectile.getX(),
                projectile == null ? Double.NaN : projectile.getY(),
                projectile == null ? Vec3d.ZERO : projectile.getVelocity());
        RingWorldMod.LOGGER.info("[test] vehicle canonical across seam={}, x={}",
                vehicleCrossed, vehicle == null ? Double.NaN : vehicle.getX());
        RingWorldMod.LOGGER.info("[test] AI nearest-image navigation across seam={}, x={}",
                aiCrossed, navigator == null ? Double.NaN : navigator.getX());
        RingWorldMod.LOGGER.info("[test] fluid flow across seam={}", fluidCrossed);
        RingWorldMod.LOGGER.info("[test] explosion reaches entity across seam={}, x={}, vx={}", explosionAffected,
                blastItem == null ? Double.NaN : blastItem.getX(),
                blastItem == null ? Double.NaN : blastItem.getVelocity().x);
    }
}
