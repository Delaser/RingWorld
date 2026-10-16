package dev.ringworld.world;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Serialized, coalescing cache writes with caller-owned snapshot capture. */
public final class RingAtlasCacheWriter {
    private final Executor executor;
    private final CacheSink sink;
    private final LinkedHashMap<Path, Write> pending = new LinkedHashMap<>();
    private boolean draining;
    private RingTerrainAtlas reusableSnapshot;
    private long snapshotGeneration;

    public RingAtlasCacheWriter() {
        this(new ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), task -> {
                    Thread thread = new Thread(task, "RingWorld atlas cache writer");
                    // Finish queued final snapshots on normal JVM exit. Idle workers
                    // expire, so this does not keep an otherwise closed client alive.
                    thread.setDaemon(false);
                    return thread;
                }));
        Runtime.getRuntime().addShutdownHook(new Thread(this::awaitIdle,
                "RingWorld atlas cache final flush"));
    }

    RingAtlasCacheWriter(Executor executor) { this(executor, (path, snapshot) -> snapshot.save(path)); }

    RingAtlasCacheWriter(Executor executor, CacheSink sink) {
        this.executor = java.util.Objects.requireNonNull(executor, "executor");
        this.sink = java.util.Objects.requireNonNull(sink, "sink");
    }

    @FunctionalInterface
    interface CacheSink {
        void save(Path path, RingTerrainAtlas snapshot) throws java.io.IOException;
    }

    public CompletableFuture<Void> submit(Path path, RingTerrainAtlas atlas) {
        Path target = path.toAbsolutePath().normalize();
        synchronized (this) {
            Write previous = pending.get(target);
            // A pending snapshot cannot be read by the writer while this lock
            // is held. An active write is removed from pending and never lends
            // its storage until all encoding and file replacement have finished.
            RingTerrainAtlas snapshot = previous == null ? reusableSnapshot : previous.snapshot();
            if (previous == null) reusableSnapshot = null;
            if (snapshot == null || !snapshot.geometry().equals(atlas.geometry())
                    || snapshot.worldHash() != atlas.worldHash() || snapshot.sampleStep() != atlas.sampleStep()) {
                snapshot = new RingTerrainAtlas(atlas.geometry(), atlas.worldHash(), atlas.sampleStep());
            }
            atlas.copySnapshotTo(snapshot);
            CompletableFuture<Void> completion = previous == null
                    ? new CompletableFuture<>() : previous.completion();
            pending.put(target, new Write(snapshot, completion, snapshotGeneration));
            if (!draining) {
                draining = true;
                try {
                    executor.execute(this::drain);
                } catch (RuntimeException | Error failure) {
                    abortPending(failure);
                    throw failure;
                }
            }
            return completion;
        }
    }

    /** Drop idle storage and prevent earlier-session writes from repopulating it. Does not cancel saves. */
    public synchronized void discardReusableSnapshots() {
        snapshotGeneration++;
        reusableSnapshot = null;
    }

    public synchronized boolean hasReusableSnapshot() { return reusableSnapshot != null; }

    private void drain() {
        while (true) {
            Path path;
            Write write;
            synchronized (this) {
                var iterator = pending.entrySet().iterator();
                if (!iterator.hasNext()) {
                    draining = false;
                    notifyAll();
                    return;
                }
                var entry = iterator.next();
                path = entry.getKey();
                write = entry.getValue();
                iterator.remove();
            }
            CacheSaveEvent event = new CacheSaveEvent();
            event.revision = write.snapshot().revision();
            event.cells = write.snapshot().cellCount();
            event.begin();
            try {
                sink.save(path, write.snapshot());
                event.success = true;
                write.completion().complete(null);
            } catch (Exception exception) {
                write.completion().completeExceptionally(exception);
            } catch (Error fatal) {
                abortPending(fatal);
                write.completion().completeExceptionally(fatal);
                throw fatal;
            } finally {
                event.commit();
                synchronized (this) {
                    if (event.success && write.generation() == snapshotGeneration && reusableSnapshot == null) {
                        reusableSnapshot = write.snapshot();
                    }
                }
            }
        }
    }

    private void abortPending(Throwable failure) {
        Write[] rejected;
        synchronized (this) {
            reusableSnapshot = null;
            snapshotGeneration++;
            rejected = pending.values().toArray(Write[]::new);
            pending.clear();
            draining = false;
            notifyAll();
        }
        for (Write write : rejected) write.completion().completeExceptionally(failure);
    }

    /** Only the JVM shutdown hook waits; render-thread teardown merely queues a snapshot. */
    private synchronized void awaitIdle() {
        boolean interrupted = false;
        while (draining) {
            try {
                wait();
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private record Write(RingTerrainAtlas snapshot, CompletableFuture<Void> completion, long generation) {}

    @jdk.jfr.Name("ringworld.AtlasCacheSave")
    @jdk.jfr.Label("RingWorld Atlas cache save")
    private static final class CacheSaveEvent extends jdk.jfr.Event {
        long revision;
        int cells;
        boolean success;
    }
}
