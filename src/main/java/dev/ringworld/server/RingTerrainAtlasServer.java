package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.net.RingTerrainAtlasMetadataPayload;
import dev.ringworld.net.RingTerrainAtlasTilePayload;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSurfaceLod;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingWorldSettings;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Generates, persists, and incrementally distributes each world's terrain atlas. */
public final class RingTerrainAtlasServer {
    private static final double WATER_TEXTURE_LUMINANCE = 0.58;
    private static final double GRASS_TEXTURE_LUMINANCE = 0.68;
    private static final double FOLIAGE_TEXTURE_LUMINANCE = 0.52;
    private static final String CACHE_FILE = "ringworld-terrain-atlas.rwat.gz";
    private static final int SAVE_INTERVAL_TICKS = 200;
    private static final int TILE_BROADCAST_INTERVAL_TICKS = 20;
    private static final int STREAM_TILES_PER_TICK = 8;
    private static final int MAX_PENDING_CHUNK_TASKS_FOR_PREGEN = 64;
    private static final Map<ServerWorld, State> STATES = new IdentityHashMap<>();
    private static final Map<UUID, ClientStream> STREAMS = new HashMap<>();

    private RingTerrainAtlasServer() { }

    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("ringworld")
                        .requires(source -> source.getPermissions().hasPermission(
                                new Permission.Level(PermissionLevel.GAMEMASTERS)))
                        .then(CommandManager.literal("atlas")
                                .then(CommandManager.literal("status")
                                        .executes(context -> {
                                            ServerWorld world = context.getSource().getServer()
                                                    .getWorld(World.OVERWORLD);
                                            context.getSource().sendFeedback(
                                                    () -> Text.literal(world == null
                                                            ? "RingWorld Overworld is unavailable"
                                                            : "RingWorld atlas: " + status(world)),
                                                    false);
                                            return world == null ? 0 : 1;
                                        }))
                                .then(CommandManager.literal("pause")
                                        .executes(context -> setPaused(context.getSource()
                                                .getServer().getWorld(World.OVERWORLD), true,
                                                context.getSource())))
                                .then(CommandManager.literal("resume")
                                        .executes(context -> setPaused(context.getSource()
                                                .getServer().getWorld(World.OVERWORLD), false,
                                                context.getSource()))))));
    }

    public static void load(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD) return;
        RingWorldSettings settings = RingWorldSettings.get(world);
        RingGeometry geometry = settings.geometry();
        long hash = RingTerrainAtlas.worldHash(settings);
        Path path = cachePath(world);
        RingTerrainAtlas atlas = null;
        if (Files.exists(path)) {
            try {
                atlas = RingTerrainAtlas.load(path, geometry, hash);
                RingWorldMod.LOGGER.info("Loaded RingWorld terrain atlas {} ({}/{} cells, {}%)",
                        path, atlas.presentCount(), atlas.cellCount(), percent(atlas.completion()));
            } catch (IOException exception) {
                RingWorldMod.LOGGER.warn("Ignoring invalid RingWorld terrain atlas {}", path, exception);
            }
        }
        if (atlas == null) atlas = new RingTerrainAtlas(geometry, hash);
        State state = new State(atlas, path);
        state.nextChunkIndex = atlas.firstMissingChunkIndex();
        STATES.put(world, state);
    }

    public static void unload(ServerWorld world) {
        State state = STATES.remove(world);
        if (state != null) save(state);
        STREAMS.entrySet().removeIf(entry -> entry.getValue().world == world);
    }

    public static void captureLoadedChunk(ServerWorld world, WorldChunk chunk) {
        State state = STATES.get(world);
        if (state == null) return;
        captureChunk(world, chunk, state);
    }

    public static void tick(ServerWorld world) {
        State state = STATES.get(world);
        if (state == null) return;
        state.ticks++;
        completeGenerationFuture(world, state);
        // Player-driven chunk streaming wins. Pregeneration resumes as soon as
        // the normal chunk queue settles instead of adding hitching at a large
        // render distance.
        if (!state.paused && RingWorldConfig.load().pregenerateTerrainAtlas()
                && world.getChunkManager().getPendingTasks() < MAX_PENDING_CHUNK_TASKS_FOR_PREGEN) {
            startNextGeneration(world, state);
        }
        if (state.ticks % TILE_BROADCAST_INTERVAL_TICKS == 0) queueDirtyTiles(world, state);
        streamTiles(world, state);
        if (state.dirty && state.ticks % SAVE_INTERVAL_TICKS == 0) save(state);
    }

    /** Sent after geometry acknowledgement, before the client asks for missing tiles. */
    public static void sendMetadata(ServerPlayerEntity player) {
        ServerWorld overworld = player.getEntityWorld().getServer().getWorld(World.OVERWORLD);
        if (overworld == null) return;
        State state = STATES.get(overworld);
        if (state == null || !ServerPlayNetworking.canSend(player, RingTerrainAtlasMetadataPayload.ID)) return;
        RingTerrainAtlas atlas = state.atlas;
        ServerPlayNetworking.send(player, new RingTerrainAtlasMetadataPayload(
                atlas.worldHash(), atlas.sampleStep(), atlas.columns(), atlas.rows(),
                RingTerrainAtlas.TILE_SIZE, atlas.presentCount(), atlas.isComplete()));
    }

    public static void requestTiles(ServerPlayerEntity player, long worldHash, boolean cacheComplete) {
        ServerWorld overworld = player.getEntityWorld().getServer().getWorld(World.OVERWORLD);
        if (overworld == null) return;
        State state = STATES.get(overworld);
        if (state == null || state.atlas.worldHash() != worldHash) return;
        if (cacheComplete) {
            RingWorldMod.LOGGER.info("{} reused complete RingWorld terrain atlas cache {}",
                    player.getName().getString(), Long.toUnsignedString(worldHash, 16));
            STREAMS.remove(player.getUuid());
            return;
        }
        Queue<TileCoordinate> tiles = new ArrayDeque<>();
        for (int tileZ = 0; tileZ < state.atlas.tileRows(); tileZ++) {
            for (int tileX = 0; tileX < state.atlas.tileColumns(); tileX++) {
                tiles.add(new TileCoordinate(tileX, tileZ));
            }
        }
        STREAMS.put(player.getUuid(), new ClientStream(overworld, tiles));
        RingWorldMod.LOGGER.info("Streaming {} RingWorld terrain atlas tiles (~{} KiB) to {}",
                tiles.size(), Math.max(1L, state.atlas.estimatedWireBytes() / 1_024L),
                player.getName().getString());
    }

    public static String status(ServerWorld world) {
        State state = STATES.get(world);
        if (state == null) return "terrain atlas unavailable";
        String generation = state.atlas.isComplete()
                ? "complete"
                : state.paused ? "paused"
                : state.generationFuture == null ? "queued" : "running";
        long elapsedNanos = Math.max(1L, System.nanoTime() - state.startedNanos);
        long captured = Math.max(0L, state.atlas.presentCount() - state.startPresentCells);
        double cellsPerSecond = captured * 1_000_000_000.0 / elapsedNanos;
        String estimate = "";
        if (!state.atlas.isComplete() && cellsPerSecond > 0.01) {
            long remaining = state.atlas.cellCount() - state.atlas.presentCount();
            long etaSeconds = Math.max(1L, Math.round(remaining / cellsPerSecond));
            estimate = ", " + String.format(java.util.Locale.ROOT, "%.0f", cellsPerSecond)
                    + " cells/s, ETA " + formatDuration(etaSeconds);
        }
        return state.atlas.presentCount() + "/" + state.atlas.cellCount() + " cells ("
                + percent(state.atlas.completion()) + "%), generation " + generation + estimate;
    }

    private static int setPaused(ServerWorld world, boolean paused,
                                 net.minecraft.server.command.ServerCommandSource source) {
        if (world == null) {
            source.sendError(Text.literal("RingWorld Overworld is unavailable"));
            return 0;
        }
        State state = STATES.get(world);
        if (state == null) {
            source.sendError(Text.literal("RingWorld terrain atlas is unavailable"));
            return 0;
        }
        state.paused = paused;
        source.sendFeedback(() -> Text.literal(
                "RingWorld atlas pregeneration " + (paused ? "paused" : "resumed")
                        + ": " + status(world)), true);
        return 1;
    }

    private static void startNextGeneration(ServerWorld world, State state) {
        if (state.atlas.isComplete() || state.generationFuture != null) return;
        int chunksAcross = state.atlas.geometry().widthChunks();
        long totalChunks = Math.multiplyExact(
                (long)state.atlas.geometry().circumferenceChunks(), chunksAcross);
        while (state.nextChunkIndex < totalChunks) {
            int chunkX = Math.toIntExact(state.nextChunkIndex / chunksAcross);
            int chunkRow = Math.toIntExact(state.nextChunkIndex % chunksAcross);
            if (!state.atlas.isChunkPresent(chunkX, chunkRow)) {
                int chunkZ = (state.atlas.geometry().minWidthZ() >> 4) + chunkRow;
                state.generationChunkIndex = state.nextChunkIndex;
                state.generationFuture = world.getChunkManager()
                        .getChunkFutureSyncOnMainThread(chunkX, chunkZ, ChunkStatus.FULL, true);
                return;
            }
            state.nextChunkIndex++;
        }
    }

    private static void completeGenerationFuture(ServerWorld world, State state) {
        CompletableFuture<?> future = state.generationFuture;
        if (future == null || !future.isDone()) return;
        try {
            var result = state.generationFuture.join();
            Chunk chunk = result.orElse(null);
            if (chunk instanceof WorldChunk worldChunk) captureChunk(world, worldChunk, state);
            state.nextChunkIndex = Math.max(state.nextChunkIndex, state.generationChunkIndex + 1);
            if (state.nextChunkIndex % 100 == 0 || state.atlas.isComplete()) {
                RingWorldMod.LOGGER.info("RingWorld terrain atlas progress: {}", status(world));
            }
        } catch (RuntimeException exception) {
            RingWorldMod.LOGGER.error("Could not pregenerate RingWorld atlas chunk {}",
                    state.generationChunkIndex, exception);
        } finally {
            state.generationFuture = null;
        }
    }

    private static void captureChunk(ServerWorld world, WorldChunk chunk, State state) {
        RingTerrainAtlas atlas = state.atlas;
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        int minChunkZ = atlas.geometry().minWidthZ() >> 4;
        int chunksAlong = atlas.geometry().circumferenceChunks();
        int chunksAcross = atlas.geometry().widthChunks();
        if (chunkX < 0 || chunkX >= chunksAlong || chunkZ < minChunkZ
                || chunkZ >= minChunkZ + chunksAcross) return;

        boolean changed = false;
        int step = atlas.sampleStep();
        for (int localZ = step / 2; localZ < 16; localZ += step) {
            for (int localX = step / 2; localX < 16; localX += step) {
                // Chunk.sampleHeightmap already returns the Y coordinate of
                // the highest matching block (Heightmap#get minus one). Do
                // not subtract another block here or grass becomes the dirt
                // underneath it in the distant surface atlas.
                int surfaceY = chunk.sampleHeightmap(
                        Heightmap.Type.WORLD_SURFACE, localX, localZ);
                int blockX = chunk.getPos().getStartX() + localX;
                int blockZ = chunk.getPos().getStartZ() + localZ;
                BlockPos surface = new BlockPos(blockX, surfaceY, blockZ);
                BlockState surfaceState = chunk.getBlockState(surface);
                int color = surfaceColor(world, surface, surfaceState);
                // The mesh represents the exposed top face, one coordinate
                // above the surface block, matching live terrain vertices.
                if (atlas.putBlockSample(blockX, blockZ, surfaceY + 1, color)) {
                    changed = true;
                    int atlasX = atlas.geometry().wrapBlockX(blockX) / step;
                    int atlasZ = Math.floorDiv(blockZ - atlas.geometry().minWidthZ(), step);
                    state.dirtyTiles.add(new TileCoordinate(
                            atlasX / RingTerrainAtlas.TILE_SIZE,
                            atlasZ / RingTerrainAtlas.TILE_SIZE));
                }
            }
        }
        if (changed) state.dirty = true;
    }

    /**
     * Captures the colour a distant top surface actually presents rather than
     * only the untinted map palette entry. Biome tint is the largest colour
     * difference between live grass/water/foliage and the atlas stand-in.
     */
    private static int surfaceColor(ServerWorld world, BlockPos surface, BlockState state) {
        var biome = world.getBiome(surface).value();
        if (state.getFluidState().isIn(FluidTags.WATER)) {
            return RingSurfaceLod.applyTextureLuminance(
                    biome.getWaterColor(), WATER_TEXTURE_LUMINANCE);
        }
        if (state.isOf(Blocks.GRASS_BLOCK)
                || state.isOf(Blocks.SHORT_GRASS)
                || state.isOf(Blocks.TALL_GRASS)
                || state.isOf(Blocks.FERN)
                || state.isOf(Blocks.LARGE_FERN)) {
            return RingSurfaceLod.applyTextureLuminanceWithMapFallback(
                    biome.getGrassColorAt(surface.getX(), surface.getZ()),
                    state.getMapColor(world, surface).color,
                    GRASS_TEXTURE_LUMINANCE);
        }
        if (state.isIn(BlockTags.LEAVES) || state.isOf(Blocks.VINE)) {
            return RingSurfaceLod.applyTextureLuminanceWithMapFallback(
                    biome.getFoliageColor(), state.getMapColor(world, surface).color,
                    FOLIAGE_TEXTURE_LUMINANCE);
        }
        return state.getMapColor(world, surface).color;
    }

    private static void queueDirtyTiles(ServerWorld world, State state) {
        if (state.dirtyTiles.isEmpty()) return;
        for (ClientStream stream : STREAMS.values()) {
            if (stream.world != world) continue;
            for (TileCoordinate tile : state.dirtyTiles) {
                if (!stream.tiles.contains(tile)) stream.tiles.add(tile);
            }
        }
        state.dirtyTiles.clear();
    }

    private static void streamTiles(ServerWorld world, State state) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            ClientStream stream = STREAMS.get(player.getUuid());
            if (stream == null || stream.world != world) continue;
            if (!ServerPlayNetworking.canSend(player, RingTerrainAtlasTilePayload.ID)) continue;
            for (int count = 0; count < STREAM_TILES_PER_TICK && !stream.tiles.isEmpty(); count++) {
                TileCoordinate tile = stream.tiles.remove();
                ServerPlayNetworking.send(player, new RingTerrainAtlasTilePayload(
                        state.atlas.worldHash(), tile.x(), tile.z(),
                        state.atlas.encodeTile(tile.x(), tile.z())));
            }
            // The generation future can complete between broadcast epochs.
            // Keep the stream alive until its final dirty tile has actually
            // been queued; otherwise the client can remain one chunk short.
            if (stream.tiles.isEmpty() && state.atlas.isComplete() && state.dirtyTiles.isEmpty()) {
                STREAMS.remove(player.getUuid());
            }
        }
    }

    private static void save(State state) {
        if (!state.dirty) return;
        try {
            state.atlas.save(state.path);
            state.dirty = false;
            RingWorldMod.LOGGER.info("Saved RingWorld terrain atlas {} ({})",
                    state.path, percent(state.atlas.completion()) + "%");
        } catch (IOException exception) {
            RingWorldMod.LOGGER.error("Could not save RingWorld terrain atlas " + state.path, exception);
        }
    }

    private static Path cachePath(ServerWorld world) {
        return world.getServer().getSavePath(WorldSavePath.ROOT).resolve("data").resolve(CACHE_FILE);
    }

    private static String percent(double completion) { return String.format(java.util.Locale.ROOT, "%.1f", completion * 100.0); }
    private static String formatDuration(long seconds) {
        if (seconds < 60L) return seconds + "s";
        long minutes = seconds / 60L;
        if (minutes < 60L) return minutes + "m" + seconds % 60L + "s";
        return minutes / 60L + "h" + minutes % 60L + "m";
    }

    private record TileCoordinate(int x, int z) {
        private TileCoordinate {
            if (x < 0 || z < 0) throw new IllegalArgumentException("negative atlas tile");
        }
    }

    private static final class State {
        private final RingTerrainAtlas atlas;
        private final Path path;
        private final Set<TileCoordinate> dirtyTiles = new LinkedHashSet<>();
        private CompletableFuture<net.minecraft.server.world.OptionalChunk<Chunk>> generationFuture;
        private long generationChunkIndex;
        private long nextChunkIndex;
        private int ticks;
        private boolean dirty;
        private boolean paused;
        private final long startedNanos;
        private final int startPresentCells;

        private State(RingTerrainAtlas atlas, Path path) {
            this.atlas = atlas;
            this.path = path;
            this.startedNanos = System.nanoTime();
            this.startPresentCells = atlas.presentCount();
        }
    }

    private record ClientStream(ServerWorld world, Queue<TileCoordinate> tiles) { }
}
