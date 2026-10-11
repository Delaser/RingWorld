package dev.ringworld.server;

import dev.ringworld.world.RingTerrainAtlas;

import java.io.IOException;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Server-owned checkpoint bookkeeping; only immutable snapshots reach the worker. */
final class RingAtlasCheckpointWriter implements AutoCloseable {
    private final Path path;
    private final Executor executor;
    private long mutation;
    private long persisted;
    private CompletableFuture<Result> active;
    private CompletableFuture<RingTerrainAtlas> allocation;
    private RingTerrainAtlas copying;
    private RingTerrainAtlas reusable;
    private int copiedCells;
    private final BitSet repairs = new BitSet();
    private long copyNanos;
    private long maxCopyStepNanos;
    private boolean verify;
    // 192 KiB per slice; check the time budget between slices.
    private static final int SLICE_CELLS = 16_384;
    private static final long COPY_BUDGET_NANOS = 2_000_000;

    RingAtlasCheckpointWriter(Path path, boolean dirty) {
        this(path, dirty, new ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), task -> new Thread(task, "RingWorld server Atlas persistence")));
    }

    RingAtlasCheckpointWriter(Path path, boolean dirty, Executor executor) {
        this.path = path;
        this.executor = executor;
        mutation = dirty ? 1 : 0;
    }

    void markDirty() { mutation = Math.incrementExact(mutation); }
    boolean dirty() { return persisted != mutation; }
    boolean busy() { return active != null; }
    boolean preparing() { return allocation != null; }

    /** Records changes to already copied slices; later slices see them on their first pass. */
    void changedCell(int column, int row, int columns) {
        int index = row * columns + column;
        if (copying != null && index < copiedCells) repairs.set(index / SLICE_CELLS);
    }

    /** Null means busy or no work. Allocation runs off-thread; copying advances on owner ticks. */
    CompletableFuture<Result> request(RingTerrainAtlas atlas, boolean verify) {
        if (busy() || (!dirty() && !verify)) return null;
        this.verify = verify;
        active = new CompletableFuture<>();
        copiedCells = 0;
        repairs.clear();
        copyNanos = 0;
        maxCopyStepNanos = 0;
        var geometry = atlas.geometry();
        long hash = atlas.worldHash();
        int step = atlas.sampleStep();
        if (reusable != null) {
            allocation = CompletableFuture.completedFuture(reusable);
            reusable = null;
        } else {
            allocation = CompletableFuture.supplyAsync(() -> new RingTerrainAtlas(geometry, hash, step), executor);
        }
        return active;
    }

    /** One bounded owner-thread copy step. No worker reads the live Atlas. */
    void advance(RingTerrainAtlas atlas) { advance(atlas, COPY_BUDGET_NANOS); }

    void advance(RingTerrainAtlas atlas, long budget) {
        if (active == null || active.isDone() || allocation == null) return;
        if (!allocation.isDone()) return;
        long start = System.nanoTime();
        try {
            if (copying == null) copying = allocation.join();
            do {
                if (copiedCells < atlas.cellCount()) {
                    int count = Math.min(SLICE_CELLS, atlas.cellCount() - copiedCells);
                    atlas.copyCheckpointCells(copying, copiedCells, count);
                    copiedCells += count;
                } else if (!repairs.isEmpty()) {
                    int slice = repairs.nextSetBit(0);
                    repairs.clear(slice);
                    int first = slice * SLICE_CELLS;
                    atlas.copyCheckpointCells(copying, first, Math.min(SLICE_CELLS, atlas.cellCount() - first));
                } else {
                    copying.finishCheckpointCopy(atlas);
                    RingTerrainAtlas snapshot = copying;
                    copying = null;
                    allocation = null;
                    long generation = mutation;
                    recordCopyStep(start);
                    CompletableFuture<Result> completion = active;
                    executor.execute(() -> write(snapshot, generation, completion));
                    return;
                }
            } while (System.nanoTime() - start < budget);
            recordCopyStep(start);
        } catch (RuntimeException exception) {
            copying = null;
            allocation = null;
            active.completeExceptionally(exception);
        }
    }

    private void recordCopyStep(long start) {
        long elapsed = System.nanoTime() - start;
        copyNanos += elapsed;
        maxCopyStepNanos = Math.max(maxCopyStepNanos, elapsed);
    }

    private void write(RingTerrainAtlas snapshot, long generation, CompletableFuture<Result> completion) {
        long writeStart = System.nanoTime();
        try {
            snapshot.save(path);
            long writeNanos = System.nanoTime() - writeStart;
            long verifyStart = System.nanoTime();
            if (verify) {
                RingTerrainAtlas reopened = RingTerrainAtlas.load(path, snapshot.geometry(), snapshot.worldHash());
                if (!reopened.isComplete() || reopened.revision() != snapshot.revision()
                        || reopened.presentCount() != snapshot.presentCount()) {
                    throw new IOException("reopened Atlas does not match completed checkpoint");
                }
            }
            reusable = snapshot;
            completion.complete(new Result(generation, snapshot.revision(), snapshot.presentCount(),
                    copyNanos, writeNanos, verify ? System.nanoTime() - verifyStart : 0, maxCopyStepNanos));
        } catch (Throwable exception) {
            reusable = snapshot;
            completion.completeExceptionally(exception);
        }
    }

    /** Called on the owning thread; failed writes leave mutations dirty and can be retried. */
    Result poll() {
        if (active == null || !active.isDone()) return null;
        CompletableFuture<Result> completed = active;
        active = null;
        Result result = completed.join();
        persisted = result.generation();
        return result;
    }

    /** Teardown only. Does not depend on server-queue callbacks. */
    void awaitActive(RingTerrainAtlas atlas) {
        if (active == null) return;
        try {
            if (allocation != null) {
                allocation.get(60, TimeUnit.SECONDS);
                // Captures are frozen at teardown: finish pending copies before waiting for disk.
                advance(atlas, Long.MAX_VALUE);
            }
            active.get(60, TimeUnit.SECONDS);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new CompletionException(exception);
        }
    }

    @Override public void close() {
        if (busy()) throw new IllegalStateException("Atlas persistence still active");
        if (executor instanceof ExecutorService service) service.shutdown();
    }

    record Result(long generation, long revision, int cells,
                  long snapshotNanos, long writeNanos, long verificationNanos, long maxCopyStepNanos) { }
}
