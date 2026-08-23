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
import dev.ringworld.world.RingAtlasSurfaceInvalidation;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSurfaceLod;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingWorldSettings;
import dev.ringworld.world.RingWorldStorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Comparator;
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
    public static final int RECAPTURE_CELLS_PER_TICK = 64;
    private static final int TEARDOWN_RELEASE_ATTEMPTS = 3;
    private static final double WATER_TEXTURE_LUMINANCE = 0.58;
    private static final double GRASS_TEXTURE_LUMINANCE = 0.68;
    private static final double FOLIAGE_TEXTURE_LUMINANCE = 0.52;
    private static final String CACHE_FILE = "terrain-atlas.rwat.gz";
    private static final String LEGACY_CACHE_FILE = "ringworld-terrain-atlas.rwat.gz";
    private static final String CACHE_DIRECTORY = RingWorldMod.MOD_ID;
    private static final Map<ServerLevel, WorldState> WORLDS = new IdentityHashMap<>();
    private static final AtlasPregenerationListener NOOP_LISTENER = progress -> { };
    private RingAtlasPregenerationService() { }

    /**
     * Distinct by identity from every vanilla ticket and never persisted.
     * Keep initialization behind the runtime scheduling path: loading this
     * service only for pure path helpers must not bootstrap Minecraft's
     * built-in registries in unit tests.
     */
    private static TicketType atlasLoadingTicket() {
        return AtlasLoadingTicketHolder.INSTANCE;
    }

    private static final class AtlasLoadingTicketHolder {
        private static final TicketType<ChunkPos> INSTANCE = TicketType.create(
                "ringworld_atlas", Comparator.comparingLong(ChunkPos::toLong));
    }

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
        WorldState state = WORLDS.get(world);
        if (state == null) return;
        // Level unload can run after the chunk source has evicted the result
        // named by an already-completed load future. Never resolve that result
        // during teardown: cancel/release its ticket and leave the selected
        // cursor unadvanced so durable atlas cells remain the resume journal.
        // Do not discard the only retained request before its loading ticket
        // is actually released. Persistent release failure is exceptional and
        // fails this unload closed with the state still reachable.
        if (state.job != null) state.job.cancelForUnload();
        save(state, true);
        WORLDS.remove(world, state);
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
        if (active != null) {
            boolean replaceIdle = AtlasPregenerationHeadlessPolicy.mayReplaceIdleHandle(
                    active.state, options);
            RingAtlasJobReplacementPolicy.Decision replacement =
                    RingAtlasJobReplacementPolicy.decide(
                            active.state, replaceIdle, active.request != null);
            if (replacement == RingAtlasJobReplacementPolicy.Decision.BLOCK_OUTSTANDING_REQUEST) {
                // A failed ticket release remains attached to this job so the
                // normal tick path can retry close(). Replacing the terminal
                // job here would orphan that retained loading ticket.
                throw new IllegalStateException(
                        RingAtlasJobReplacementPolicy.OUTSTANDING_REQUEST_MESSAGE);
            }
            if (replacement == RingAtlasJobReplacementPolicy.Decision.REPLACE) {
                // Config-disabled startup creates an observable IDLE
                // background handle. Terminal handles are also restartable,
                // but only after every retained request has been released.
                active = null;
            }
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
        consumeFuture(world, state);
        processRecaptures(world, state);
        commitPendingRevision(state);
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
        if (state != null) {
            boolean wasComplete = state.atlas.isComplete();
            CaptureResult result = captureChunk(world, chunk, state);
            if (wasComplete && result.changed()) state.revisionPending = true;
        }
    }

    /** Queues one potentially surface-affecting block mutation without rescanning inline. */
    public static void blockChanged(ServerLevel world, BlockPos position) {
        if (world.dimension() != Level.OVERWORLD) return;
        requireServerThread(world);
        WorldState state = WORLDS.get(world);
        if (state == null) return;
        RingTerrainAtlas atlas = state.atlas;
        RingAtlasSurfaceInvalidation.Cell cell = RingAtlasSurfaceInvalidation.cellFor(
                atlas.geometry(), atlas.sampleStep(), position.getX(), position.getZ()).orElse(null);
        if (cell == null || !atlas.hasCell(cell.column(), cell.row())) return;
        if (RingAtlasSurfaceInvalidation.mayAffectSurface(
                position.getY(), atlas.cellHeight(cell.column(), cell.row()))) {
            state.recaptures.enqueue(cell);
        }
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
        if (job == null || job.request == null || !job.request.isDone()) return;
        RingAtlasChunkRequest<LevelChunk> request = job.request;
        if (!job.requestProcessed) {
            try {
                LevelChunk levelChunk = request.joinResult();
                if (levelChunk == null) {
                    throw new IllegalStateException("pregeneration returned no full chunk for " + job.selection.selected());
                }
                if (!job.selection.accepts(levelChunk.getPos().x, levelChunk.getPos().z)) {
                    throw new IllegalStateException("pregeneration returned unexpected chunk " + levelChunk.getPos()
                            + " for " + job.selection.selected());
                }
                CaptureResult capture = captureChunk(world, levelChunk, state);
                if (!capture.valid()
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
            } finally {
                // Never process this completed request twice if ticket release
                // itself fails and has to be retried on a later tick/unload.
                job.requestProcessed = true;
            }
        }
        try {
            request.close();
            job.request = null;
            job.requestProcessed = false;
        } catch (RuntimeException releaseFailure) {
            job.fail(new IllegalStateException("could not release RingWorld atlas chunk ticket", releaseFailure));
        }
    }

    private static CaptureResult captureChunk(ServerLevel world, LevelChunk chunk, WorldState state) {
        RingTerrainAtlas atlas = state.atlas;
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        int minChunkZ = atlas.geometry().minChunkZ();
        int chunksAlong = atlas.geometry().circumferenceChunks();
        int chunksAcross = atlas.geometry().widthChunks();
        if (chunkX < 0 || chunkX >= chunksAlong || chunkZ < minChunkZ || chunkZ >= minChunkZ + chunksAcross) {
            return CaptureResult.OUTSIDE;
        }
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
        return new CaptureResult(true, changed);
    }

    private static void processRecaptures(ServerLevel world, WorldState state) {
        RingTerrainAtlas atlas = state.atlas;
        boolean changed = false;
        for (RingAtlasSurfaceInvalidation.Cell cell : state.recaptures.drain(
                RECAPTURE_CELLS_PER_TICK, atlas.columns(), atlas.rows())) {
            int blockX = cell.column() * atlas.sampleStep() + atlas.sampleStep() / 2;
            int blockZ = atlas.geometry().minWidthZ()
                    + cell.row() * atlas.sampleStep() + atlas.sampleStep() / 2;
            BlockPos sample = new BlockPos(blockX, 0, blockZ);
            if (!world.hasChunkAt(sample)) {
                state.recaptures.enqueue(cell);
                continue;
            }
            LevelChunk chunk = world.getChunkAt(sample);
            int localX = Math.floorMod(blockX, 16);
            int localZ = Math.floorMod(blockZ, 16);
            int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
            BlockPos surface = new BlockPos(blockX, surfaceY, blockZ);
            int color = surfaceColor(world, surface, chunk.getBlockState(surface));
            if (atlas.putCell(cell.column(), cell.row(), surfaceY + 1, color)) {
                changed = true;
                state.dirtyTiles.publish(new TileCoordinate(
                        cell.column() / RingTerrainAtlas.TILE_SIZE,
                        cell.row() / RingTerrainAtlas.TILE_SIZE));
            }
        }
        if (changed) state.revisionPending = true;
    }

    private static void commitPendingRevision(WorldState state) {
        if (!state.revisionPending) return;
        state.revisionPending = false;
        state.atlas.advanceRevision();
        state.dirty = true;
        if (state.job != null) state.job.refreshProgress();
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
        commitPendingRevision(state);
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
        if (reopened.revision() != state.atlas.revision()) {
            throw new IOException("reopened atlas revision does not match saved state");
        }
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
        if (state == null || state.job == null) return;
        // Shutdown is not a normal completed-request consumption tick. The
        // chunk source may already be tearing down, so discard the lease and
        // checkpoint only cells that were authoritatively captured earlier.
        // The still-selected cursor chunk is therefore retried after resume.
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

    private record CaptureResult(boolean valid, boolean changed) {
        private static final CaptureResult OUTSIDE = new CaptureResult(false, false);
    }

    private static final class WorldState {
        private final RingTerrainAtlas atlas;
        private final Path path;
        private final RingAtlasDirtyTileQueue dirtyTiles = new RingAtlasDirtyTileQueue();
        private final RingAtlasRecaptureQueue recaptures = new RingAtlasRecaptureQueue();
        private boolean dirty;
        private boolean revisionPending;
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
        private RingAtlasChunkRequest<LevelChunk> request;
        private boolean requestProcessed;
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
        @Override public void cancel() { enqueue(this::requestCancelOnServerThread); }
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
        private void requestCancelOnServerThread() {
            if (!state.isTerminal()) cancelRequested = true;
        }
        private void tick() {
            if (cancelRequested) {
                cancelRequested = false;
                if (state.isTerminal()) return;
                cancelNow("cancelled");
                return;
            }
            if (!RingAtlasPregenerationSchedulingPolicy.maySchedule(state)) return;
            if (owner.atlas.isComplete()) {
                // A normal chunk-load callback may populate the last atlas
                // cells before the ticket future is observed complete. The
                // request still owns a lease and must be consumed/released.
                if (request == null) finish();
                return;
            }
            if (request != null || !selection.mayRetryAt(owner.ticks)) return;
            if (world.getChunkSource().getPendingTasksCount() >= options.pendingTaskSoftLimit()) return;
            RingAtlasPregenerationCursor.Chunk selected = selection.select().orElse(null);
            if (selected == null) {
                finish();
                return;
            }
            try {
                // getChunkFuture would managedBlock this server tick. The
                // public ticket-and-load API schedules FULL generation and
                // returns immediately; retain our unique ticket until the
                // completed LevelChunk has been captured on a later tick.
                ChunkPos position = new ChunkPos(selected.chunkX(), selected.chunkZ());
                TicketType ticket = atlasLoadingTicket();
                request = RingAtlasChunkRequest.start(
                        () -> {
                            world.getChunkSource().addRegionTicket(ticket, position, 0, position);
                            return world.getChunkSource().getChunkFuture(
                                    position.x, position.z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                                    true);
                        },
                        () -> world.getChunkSource().getChunkNow(
                                selected.chunkX(), selected.chunkZ()),
                        () -> world.getChunkSource().removeRegionTicket(ticket, position, 0, position));
                requestProcessed = false;
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
            cancelNow(reason, 1);
        }
        private void cancelNow(String reason, int releaseAttempts) {
            cancelRequested = false;
            if (state.isTerminal()) {
                if (!releaseOutstandingRequest(true, releaseAttempts)
                        && releaseAttempts > 1) {
                    throw new IllegalStateException(
                            "could not release RingWorld atlas chunk ticket during " + reason);
                }
                return;
            }
            if (!releaseOutstandingRequest(true, releaseAttempts)) {
                if (releaseAttempts > 1) {
                    throw new IllegalStateException(
                            "could not release RingWorld atlas chunk ticket during " + reason);
                }
                return;
            }
            if (!save(owner, true)) {
                fail(new IOException("could not checkpoint terrain atlas before " + reason));
                return;
            }
            transition(AtlasPregenerationState.CANCELLED);
            completion.completeExceptionally(new IllegalStateException("atlas pregeneration " + reason));
            notifyProgress();
        }
        private void cancelForUnload() {
            cancelNow("cancelled by world unload", TEARDOWN_RELEASE_ATTEMPTS);
        }
        private boolean releaseOutstandingRequest(boolean cancelLoad) {
            return releaseOutstandingRequest(cancelLoad, 1);
        }
        private boolean releaseOutstandingRequest(boolean cancelLoad, int releaseAttempts) {
            RingAtlasChunkRequest<LevelChunk> outstanding = request;
            if (outstanding == null) return true;
            try {
                if (cancelLoad) outstanding.cancelWithReleaseAttempts(releaseAttempts);
                else outstanding.close();
                request = null;
                requestProcessed = false;
                return true;
            } catch (RuntimeException releaseFailure) {
                fail(new IllegalStateException("could not release RingWorld atlas chunk ticket", releaseFailure));
                return false;
            }
        }
        private void fail(Throwable error) {
            if (state.isTerminal()) return;
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
