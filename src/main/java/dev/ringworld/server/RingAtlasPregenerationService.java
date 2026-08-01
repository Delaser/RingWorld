package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.AtlasPregenerationHandle;
import dev.ringworld.world.AtlasPregenerationHeadlessPolicy;
import dev.ringworld.world.AtlasPregenerationListener;
import dev.ringworld.world.AtlasPregenerationOptions;
import dev.ringworld.world.AtlasPregenerationProgress;
import dev.ringworld.world.AtlasPregenerationResult;
import dev.ringworld.world.AtlasPregenerationState;
import dev.ringworld.world.RingAtlasPregenerationCursor;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSurfaceLod;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingWorldSettings;
import dev.ringworld.world.RingWorldStorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The single server-thread owner of a RingWorld Overworld terrain atlas.
 *
 * <p>This service deliberately contains no Fabric registrations. The Fabric
 * adapter calls its lifecycle, chunk-capture, and tile-publication methods;
 * all atlas writes, saves, cursor advancement, and job state live here so
 * there is never a competing scheduler or atlas writer.</p>
 */
public final class RingAtlasPregenerationService {
    public static final int SAVE_INTERVAL_TICKS = 200;
    public static final int TILE_PUBLICATION_INTERVAL_TICKS = 20;
    private static final double WATER_TEXTURE_LUMINANCE = 0.58;
    private static final double GRASS_TEXTURE_LUMINANCE = 0.68;
    private static final double FOLIAGE_TEXTURE_LUMINANCE = 0.52;
    private static final String CACHE_FILE = "terrain-atlas.rwat.gz";
    private static final String LEGACY_CACHE_FILE = "ringworld-terrain-atlas.rwat.gz";
    private static final String CACHE_DIRECTORY = RingWorldMod.MOD_ID;
    private static final Map<ServerLevel, WorldState> WORLDS = new IdentityHashMap<>();
    private static final AtlasPregenerationListener NOOP_LISTENER = progress -> { };

    private RingAtlasPregenerationService() { }

    public static void load(ServerLevel world) { load(world, true); }

    /**
     * Loads the one authoritative atlas state. The Fabric headless adapter
     * passes {@code false} so it can attach the stop-on-complete intent before
     * a normal background writer is allowed to start.
     */
    public static void load(ServerLevel world, boolean allowBackgroundAutostart) {
        if (world.dimension() != Level.OVERWORLD) return;
        requireServerThread(world);
        RingWorldSettings settings = RingWorldSettings.get(world);
        RingGeometry geometry = settings.geometry();
        long hash = RingTerrainAtlas.worldHash(settings);
        Path path = cachePath(world);
        Path legacyPath = legacyCachePath(world);
        RingTerrainAtlas.StorageLoad storage = RingTerrainAtlas.loadStorage(path, legacyPath, geometry, hash);
        RingTerrainAtlas atlas = storage.atlas();
        logLoad(storage.status(), path, legacyPath, atlas);
        WorldState state = new WorldState(atlas, path,
                storage.status() == RingTerrainAtlas.StorageStatus.INVALID_CURRENT
                        || storage.status() == RingTerrainAtlas.StorageStatus.INVALID_LEGACY);
        WORLDS.put(world, state);
        if (allowBackgroundAutostart && RingWorldConfig.load().pregenerateTerrainAtlas()) {
            pregenerate(world, AtlasPregenerationOptions.backgroundDefaults(), NOOP_LISTENER);
        } else {
            // Config false deliberately leaves the same observable handle in
            // IDLE. Player chunk capture still mutates this atlas, while a
            // later explicit matching start can transition the handle once.
            state.job = new Job(world, state, AtlasPregenerationOptions.backgroundDefaults(), NOOP_LISTENER);
            if (state.atlas.isComplete() && !state.dirty) state.job.completeAlreadyVerified();
        }
    }

    public static void unload(ServerLevel world) {
        requireServerThread(world);
        WorldState state = WORLDS.remove(world);
        if (state == null) return;
        // Unload cannot wait for a chunk future. A completed result is consumed
        // first, then the active handle is resolved deterministically.
        consumeFuture(world, state);
        if (state.job != null && !state.job.completion.isDone()) {
            state.job.cancelForUnload();
        }
        save(state, true);
    }

    public static AtlasPregenerationHandle pregenerate(ServerLevel world,
                                                         AtlasPregenerationOptions options,
                                                         AtlasPregenerationListener listener) {
        requireOverworld(world);
        requireServerThread(world);
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(listener, "listener");
        requireSupportedPolicy(options);
        WorldState state = requireState(world);
        Job active = state.job;
        if (active != null && AtlasPregenerationHeadlessPolicy.mayReplaceIdleHandle(active.state, options)) {
            // Config-disabled startup creates an observable IDLE background
            // handle. It has no selected future or writes, so replacing it is
            // safe and preserves the single world-owned writer invariant.
            active = null;
        }
        if (active != null && !active.state.isTerminal()) {
            if (!active.options.sharesExecutionPolicyWith(options)) {
                throw new IllegalStateException("a RingWorld atlas pregeneration job is already active with different options");
            }
            active.addListener(listener);
            if (active.state == AtlasPregenerationState.IDLE) active.transition(AtlasPregenerationState.RUNNING);
            return active;
        }
        Job job = new Job(world, state, options, listener);
        state.job = job;
        if (state.atlas.isComplete() && !state.dirty) {
            // A loaded, complete cache was already validated by loadStorage.
            job.completeAlreadyVerified();
        } else {
            job.transition(AtlasPregenerationState.RUNNING);
        }
        return job;
    }

    public static Optional<AtlasPregenerationHandle> active(ServerLevel world) {
        WorldState state = WORLDS.get(world);
        return state == null || state.job == null ? Optional.empty() : Optional.of(state.job);
    }

    public static RingTerrainAtlas atlas(ServerLevel world) {
        return requireState(world).atlas;
    }

    /** Called from the normal end-level tick on the owning server thread. */
    public static void tick(ServerLevel world) {
        requireServerThread(world);
        WorldState state = WORLDS.get(world);
        if (state == null) return;
        state.ticks++;
        if (AtlasPregenerationHeadlessPolicy.mustConsumeCompletedFutureBeforeCheckpoint(
                state.job.future != null && state.job.future.isDone())) {
            consumeFuture(world, state);
        }
        Job job = state.job;
        if (job != null) {
            job.tick();
            if (state.ticks % job.options.progressIntervalTicks() == 0) job.refreshProgress();
        }
        if (state.dirty && state.ticks % SAVE_INTERVAL_TICKS == 0) save(state, false);
    }

    /** Player-loaded chunks have priority and always populate the same atlas. */
    public static void captureLoadedChunk(ServerLevel world, LevelChunk chunk) {
        requireServerThread(world);
        WorldState state = WORLDS.get(world);
        if (state != null) captureChunk(world, chunk, state);
    }

    /** Atomically transfers changed tile coordinates to the Fabric streamer. */
    public static Set<TileCoordinate> drainDirtyTiles(ServerLevel world) {
        requireServerThread(world);
        WorldState state = requireState(world);
        return state.dirtyTiles.drain();
    }

    /** Lets the adapter retain a finished client stream until final tiles reach its queue. */
    public static boolean hasPendingDirtyTiles(ServerLevel world) {
        return requireState(world).dirtyTiles.hasPending();
    }

    public static String status(ServerLevel world) {
        WorldState state = WORLDS.get(world);
        if (state == null) return "terrain atlas unavailable";
        AtlasPregenerationProgress progress = state.job == null
                ? idleProgress(state) : state.job.progress();
        String rate = progress.cellsPerSecond() > 0.01
                ? ", " + String.format(java.util.Locale.ROOT, "%.0f", progress.cellsPerSecond())
                + " cells/s" + progress.eta().map(value -> ", ETA " + formatDuration(value.toSeconds())).orElse("")
                : "";
        return progress.presentCells() + "/" + progress.totalCells() + " cells ("
                + percent(state.atlas.completion()) + "%), generation "
                + progress.state().name().toLowerCase(java.util.Locale.ROOT) + rate;
    }

    private static AtlasPregenerationProgress idleProgress(WorldState state) {
        long chunks = RingAtlasPregenerationCursor.checkedTotalChunks(
                state.atlas.geometry().circumferenceChunks(), state.atlas.geometry().widthChunks());
        return AtlasPregenerationProgress.snapshot(AtlasPregenerationState.IDLE, 0, chunks,
                state.atlas.presentCount(), state.atlas.presentCount(), state.atlas.cellCount(),
                Duration.ZERO, Optional.empty());
    }

    private static void consumeFuture(ServerLevel world, WorldState state) {
        Job job = state.job;
        if (job == null || job.future == null || !job.future.isDone()) return;
        CompletableFuture<ChunkResult<ChunkAccess>> future = job.future;
        job.future = null;
        try {
            ChunkAccess chunk = future.join().orElse(null);
            if (!(chunk instanceof LevelChunk levelChunk)) {
                throw new IllegalStateException("pregeneration returned no full chunk for " + job.selection.selected());
            }
            if (!job.selection.accepts(levelChunk.getPos().x(), levelChunk.getPos().z())) {
                throw new IllegalStateException("pregeneration returned unexpected chunk " + levelChunk.getPos()
                        + " for " + job.selection.selected());
            }
            if (!captureChunk(world, levelChunk, state)
                    || !state.atlas.isChunkPresent(job.selection.selected().chunkX(),
                    job.selection.selected().chunkRow())) {
                throw new IllegalStateException("pregeneration did not capture selected chunk " + job.selection.selected());
            }
            job.completedChunks++;
            job.selection.captured(); // Advance only after the selected chunk was safely captured.
            job.lastError = Optional.empty();
            if (job.completedChunks % 100 == 0 || state.atlas.isComplete()) {
                RingWorldMod.LOGGER.info("RingWorld terrain atlas progress: {}", status(world));
            }
        } catch (RuntimeException exception) {
            job.recordFailure(exception);
        }
    }

    private static boolean captureChunk(ServerLevel world, LevelChunk chunk, WorldState state) {
        RingTerrainAtlas atlas = state.atlas;
        int chunkX = chunk.getPos().x();
        int chunkZ = chunk.getPos().z();
        int minChunkZ = atlas.geometry().minChunkZ();
        int chunksAlong = atlas.geometry().circumferenceChunks();
        int chunksAcross = atlas.geometry().widthChunks();
        if (chunkX < 0 || chunkX >= chunksAlong || chunkZ < minChunkZ || chunkZ >= minChunkZ + chunksAcross) return false;
        boolean changed = false;
        int step = atlas.sampleStep();
        for (int localZ = step / 2; localZ < 16; localZ += step) {
            for (int localX = step / 2; localX < 16; localX += step) {
                int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
                int blockX = chunk.getPos().getMinBlockX() + localX;
                int blockZ = chunk.getPos().getMinBlockZ() + localZ;
                BlockPos surface = new BlockPos(blockX, surfaceY, blockZ);
                BlockState surfaceState = chunk.getBlockState(surface);
                int color = surfaceColor(world, surface, surfaceState);
                if (atlas.putBlockSample(blockX, blockZ, surfaceY + 1, color)) {
                    changed = true;
                    int atlasX = atlas.geometry().wrapBlockX(blockX) / step;
                    int atlasZ = Math.floorDiv(blockZ - atlas.geometry().minWidthZ(), step);
                    state.dirtyTiles.publish(new TileCoordinate(atlasX / RingTerrainAtlas.TILE_SIZE,
                            atlasZ / RingTerrainAtlas.TILE_SIZE));
                }
            }
        }
        if (changed) {
            state.dirty = true;
            if (state.job != null) state.job.refreshProgress();
        }
        return true;
    }

    private static int surfaceColor(ServerLevel world, BlockPos surface, BlockState state) {
        var biome = world.getBiome(surface).value();
        if (state.getFluidState().is(FluidTags.WATER)) {
            return RingSurfaceLod.applyTextureLuminance(biome.getWaterColor(), WATER_TEXTURE_LUMINANCE);
        }
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)) {
            return RingSurfaceLod.applyTextureLuminanceWithMapFallback(
                    biome.getGrassColor(surface.getX(), surface.getZ()), state.getMapColor(world, surface).col,
                    GRASS_TEXTURE_LUMINANCE);
        }
        if (state.is(BlockTags.LEAVES) || state.is(Blocks.VINE)) {
            return RingSurfaceLod.applyTextureLuminanceWithMapFallback(
                    biome.getFoliageColor(), state.getMapColor(world, surface).col, FOLIAGE_TEXTURE_LUMINANCE);
        }
        return state.getMapColor(world, surface).col;
    }

    private static boolean save(WorldState state, boolean log) {
        if (!state.dirty) return true;
        try {
            state.atlas.save(state.path);
            state.dirty = false;
            if (log) RingWorldMod.LOGGER.info("Saved RingWorld terrain atlas {} ({})", state.path,
                    percent(state.atlas.completion()) + "%");
            return true;
        } catch (IOException exception) {
            RingWorldMod.LOGGER.error("Could not save RingWorld terrain atlas " + state.path, exception);
            return false;
        }
    }

    private static void verifyComplete(WorldState state) throws IOException {
        RingTerrainAtlas reopened = RingTerrainAtlas.load(state.path, state.atlas.geometry(), state.atlas.worldHash());
        if (!reopened.isComplete()) throw new IOException("reopened atlas is incomplete");
    }

    static Path cachePath(ServerLevel world) { return cachePath(RingWorldStorageAccess.dimensionPath(world)); }
    static Path cachePath(Path dimensionPath) { return dimensionPath.resolve("data").resolve(CACHE_DIRECTORY).resolve(CACHE_FILE); }
    static Path legacyCachePath(ServerLevel world) { return legacyCachePath(world.getServer().getWorldPath(LevelResource.ROOT)); }
    static Path legacyCachePath(Path worldRoot) { return worldRoot.resolve("data").resolve(LEGACY_CACHE_FILE); }

    private static void requireOverworld(ServerLevel world) {
        if (world.dimension() != Level.OVERWORLD) throw new IllegalArgumentException("atlas pregeneration is Overworld-only");
    }
    private static void requireServerThread(ServerLevel world) {
        if (!world.getServer().isSameThread()) {
            throw new IllegalStateException("RingWorld atlas service mutations must run on the server thread");
        }
    }
    private static void requireSupportedPolicy(AtlasPregenerationOptions options) {
        if (options.maxInFlightChunks() != AtlasPregenerationOptions.DEFAULT_MAX_IN_FLIGHT_CHUNKS
                || options.pendingTaskSoftLimit() != AtlasPregenerationOptions.DEFAULT_PENDING_TASK_SOFT_LIMIT
                || options.checkpointIntervalChunks() != AtlasPregenerationOptions.DEFAULT_CHECKPOINT_INTERVAL_CHUNKS
                || options.progressIntervalTicks() != AtlasPregenerationOptions.DEFAULT_PROGRESS_INTERVAL_TICKS) {
            throw new IllegalArgumentException("the initial RingWorld atlas service supports only the conservative default execution policy");
        }
        // stopServerWhenComplete is an adapter intent. This service still owns
        // only atlas scheduling/capture/save/verification; a platform
        // coordinator performs world save, report write, and process halt.
    }
    private static WorldState requireState(ServerLevel world) {
        WorldState state = WORLDS.get(world);
        if (state == null) throw new IllegalStateException("RingWorld terrain atlas is unavailable");
        return state;
    }

    /** Checkpoints an interrupted headless run without waiting on its future. */
    public static void interruptForServerStop(ServerLevel world) {
        requireServerThread(world);
        WorldState state = WORLDS.get(world);
        if (state == null || state.job == null || state.job.completion.isDone()) return;
        // A completed future may already contain a fully generated canonical
        // chunk. Capture it before cancellation/checkpoint, exactly as unload
        // does, so restart never loses durable work from the final tick.
        consumeFuture(world, state);
        if (state.job.completion.isDone()) return;
        state.job.cancelForUnload();
    }
    private static String percent(double completion) { return String.format(java.util.Locale.ROOT, "%.1f", completion * 100.0); }
    private static String formatDuration(long seconds) {
        if (seconds < 60L) return seconds + "s";
        long minutes = seconds / 60L;
        return minutes < 60L ? minutes + "m" + seconds % 60L + "s" : minutes / 60L + "h" + minutes % 60L + "m";
    }
    private static void logLoad(RingTerrainAtlas.StorageStatus status, Path path, Path legacy, RingTerrainAtlas atlas) {
        switch (status) {
            case CURRENT -> RingWorldMod.LOGGER.info("Loaded RingWorld terrain atlas {} ({}/{} cells, {}%)", path, atlas.presentCount(), atlas.cellCount(), percent(atlas.completion()));
            case MIGRATED_LEGACY -> RingWorldMod.LOGGER.info("Migrated legacy RingWorld terrain atlas from {} to {} ({}/{} cells, {}%)", legacy, path, atlas.presentCount(), atlas.cellCount(), percent(atlas.completion()));
            case INVALID_CURRENT -> RingWorldMod.LOGGER.warn("Ignoring invalid RingWorld terrain atlas {}; rebuilding without legacy fallback", path);
            case INVALID_LEGACY -> RingWorldMod.LOGGER.warn("Legacy RingWorld terrain atlas {} failed geometry/world-hash validation; rebuilding", legacy);
            case FRESH -> RingWorldMod.LOGGER.info("Creating fresh RingWorld terrain atlas {}", path);
        }
    }

    public record TileCoordinate(int x, int z) {
        public TileCoordinate {
            if (x < 0 || z < 0) throw new IllegalArgumentException("negative atlas tile");
        }
    }

    private static final class WorldState {
        private final RingTerrainAtlas atlas;
        private final Path path;
        private final RingAtlasDirtyTileQueue dirtyTiles = new RingAtlasDirtyTileQueue();
        private boolean dirty;
        private long ticks;
        private Job job;
        private WorldState(RingTerrainAtlas atlas, Path path, boolean dirty) {
            this.atlas = atlas;
            this.path = path;
            this.dirty = dirty;
        }
    }

    private static final class Job implements AtlasPregenerationHandle {
        private static final int MAX_RETRIES = 3;
        private final ServerLevel world;
        private final WorldState owner;
        private final AtlasPregenerationOptions options;
        private final RingAtlasPregenerationSelection selection;
        private final CompletableFuture<AtlasPregenerationResult> completion = new CompletableFuture<>();
        private final ArrayDeque<AtlasPregenerationListener> listeners = new ArrayDeque<>();
        private AtlasPregenerationState state = AtlasPregenerationState.IDLE;
        private CompletableFuture<ChunkResult<ChunkAccess>> future;
        private long completedChunks;
        private final long startedNanos = System.nanoTime();
        private long rateElapsedNanos;
        private long rateStartedNanos = startedNanos;
        private int startingPresentCells;
        private Optional<String> lastError = Optional.empty();
        private boolean cancelRequested;
        private volatile AtlasPregenerationProgress published;

        private Job(ServerLevel world, WorldState owner, AtlasPregenerationOptions options,
                    AtlasPregenerationListener listener) {
            this.world = world;
            this.owner = owner;
            this.options = options;
            this.selection = new RingAtlasPregenerationSelection(owner.atlas.geometry(), owner.atlas);
            this.startingPresentCells = owner.atlas.presentCount();
            addListener(listener);
            this.published = snapshot();
        }

        @Override public AtlasPregenerationProgress progress() { return published; }
        private AtlasPregenerationProgress snapshot() {
            long elapsed = rateElapsedNanos;
            if (state == AtlasPregenerationState.RUNNING) elapsed += Math.max(0L, System.nanoTime() - rateStartedNanos);
            return AtlasPregenerationProgress.snapshot(state, completedChunks, selection.totalChunks(),
                    startingPresentCells, owner.atlas.presentCount(), owner.atlas.cellCount(),
                    Duration.ofNanos(elapsed), lastError);
        }
        @Override public void pause() { enqueue(this::pauseOnServerThread); }
        @Override public void resume() { enqueue(this::resumeOnServerThread); }
        @Override public void cancel() { enqueue(() -> { cancelRequested = true; }); }
        @Override public CompletableFuture<AtlasPregenerationResult> completion() { return completion; }

        private void addListener(AtlasPregenerationListener listener) { listeners.add(listener); }
        private void enqueue(Runnable action) {
            if (world.getServer().isSameThread()) action.run();
            else world.getServer().execute(action);
        }
        private void pauseOnServerThread() {
            if (state == AtlasPregenerationState.RUNNING) transition(AtlasPregenerationState.PAUSED);
        }
        private void resumeOnServerThread() {
            if (state == AtlasPregenerationState.PAUSED) {
                startingPresentCells = owner.atlas.presentCount();
                rateElapsedNanos = 0L;
                rateStartedNanos = System.nanoTime();
                transition(AtlasPregenerationState.RUNNING);
            }
        }
        private void tick() {
            if (cancelRequested) {
                cancelNow("cancelled");
                return;
            }
            if (!RingAtlasPregenerationSchedulingPolicy.maySchedule(state)) return;
            if (owner.atlas.isComplete()) {
                finish();
                return;
            }
            if (future != null || !selection.mayRetryAt(owner.ticks)) return;
            if (world.getChunkSource().getPendingTasksCount() >= options.pendingTaskSoftLimit()) return;
            RingAtlasPregenerationCursor.Chunk selected = selection.select().orElse(null);
            if (selected == null) {
                finish();
                return;
            }
            try {
                future = world.getChunkSource().getChunkFuture(selected.chunkX(), selected.chunkZ(), ChunkStatus.FULL, true);
                notifyProgress();
            } catch (RuntimeException exception) {
                recordFailure(exception);
            }
        }
        private void recordFailure(RuntimeException exception) {
            lastError = Optional.ofNullable(exception.getMessage()).or(() -> Optional.of(exception.getClass().getSimpleName()));
            if (!selection.failed(owner.ticks, MAX_RETRIES)) {
                fail(exception);
                return;
            }
            RingWorldMod.LOGGER.warn("Retrying RingWorld atlas chunk {} after failure {}/{}", selection.selected(), selection.retryAttempt(), MAX_RETRIES, exception);
            notifyProgress();
        }
        private void finish() {
            transition(AtlasPregenerationState.SAVING);
            if (!save(owner, true)) {
                fail(new IOException("could not save completed terrain atlas"));
                return;
            }
            try {
                verifyComplete(owner);
                transition(AtlasPregenerationState.COMPLETE);
                AtlasPregenerationResult result = new AtlasPregenerationResult(owner.atlas.worldHash(), completedChunks,
                        owner.atlas.presentCount(), Duration.ofNanos(System.nanoTime() - startedNanos), owner.path);
                completion.complete(result);
                for (AtlasPregenerationListener listener : listeners) listener.onComplete(result);
            } catch (IOException exception) {
                fail(exception);
            }
        }
        private void completeAlreadyVerified() {
            transition(AtlasPregenerationState.RUNNING);
            transition(AtlasPregenerationState.SAVING);
            transition(AtlasPregenerationState.COMPLETE);
            AtlasPregenerationResult result = new AtlasPregenerationResult(owner.atlas.worldHash(), 0,
                    owner.atlas.presentCount(), Duration.ZERO, owner.path);
            completion.complete(result);
            for (AtlasPregenerationListener listener : listeners) listener.onComplete(result);
        }
        private void cancelNow(String reason) {
            if (future != null && future.isDone()) return; // consumed at the next tick before cancellation.
            if (future != null) future.cancel(false);
            future = null;
            if (!save(owner, true)) {
                fail(new IOException("could not checkpoint terrain atlas before " + reason));
                return;
            }
            transition(AtlasPregenerationState.CANCELLED);
            completion.completeExceptionally(new IllegalStateException("atlas pregeneration " + reason));
            notifyProgress();
        }
        private void cancelForUnload() { cancelRequested = true; cancelNow("cancelled by world unload"); }
        private void fail(Throwable error) {
            if (!state.isTerminal()) transition(AtlasPregenerationState.FAILED);
            completion.completeExceptionally(error);
            for (AtlasPregenerationListener listener : listeners) listener.onFailure(error);
            notifyProgress();
        }
        private void transition(AtlasPregenerationState target) {
            if (state != target && !state.canTransitionTo(target)) throw new IllegalStateException("illegal atlas state transition " + state + " -> " + target);
            if (state == AtlasPregenerationState.RUNNING && target != AtlasPregenerationState.RUNNING) {
                rateElapsedNanos += Math.max(0L, System.nanoTime() - rateStartedNanos);
            }
            state = target;
            if (target == AtlasPregenerationState.RUNNING) rateStartedNanos = System.nanoTime();
            notifyProgress();
        }
        private void notifyProgress() {
            published = snapshot();
            for (AtlasPregenerationListener listener : listeners) listener.onProgress(published);
        }
        private void refreshProgress() { published = snapshot(); }
    }
}
