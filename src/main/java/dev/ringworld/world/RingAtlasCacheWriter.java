package dev.ringworld.world;

import java.nio.file.Path;
import java.nio.ByteBuffer;
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
    private final MemoryFactory memoryFactory;
    private final LinkedHashMap<Path, Write> pending = new LinkedHashMap<>();
    private boolean draining;
    private RingAtlasCacheImage reusableSnapshot;
    private long snapshotGeneration;

    public RingAtlasCacheWriter() {
        this(RingAtlasCacheImage::allocateHeap);
    }

    public RingAtlasCacheWriter(MemoryFactory memoryFactory) {
        this(new ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), task -> {
                    Thread thread = new Thread(task, "RingWorld atlas cache writer");
                    // Finish queued final snapshots on normal JVM exit. Idle workers
                    // expire, so this does not keep an otherwise closed client alive.
                    thread.setDaemon(false);
                    return thread;
                }), (path,snapshot)->snapshot.save(path), memoryFactory);
        Runtime.getRuntime().addShutdownHook(new Thread(this::awaitIdle,
                "RingWorld atlas cache final flush"));
    }

    RingAtlasCacheWriter(Executor executor) { this(executor, (path, snapshot) -> snapshot.save(path)); }

    RingAtlasCacheWriter(Executor executor, CacheSink sink) {
        this(executor,sink,RingAtlasCacheImage::allocateHeap);
    }

    RingAtlasCacheWriter(Executor executor, CacheSink sink, MemoryFactory memoryFactory) {
        this.executor = java.util.Objects.requireNonNull(executor, "executor");
        this.sink = java.util.Objects.requireNonNull(sink, "sink");
        this.memoryFactory = java.util.Objects.requireNonNull(memoryFactory, "memoryFactory");
    }

    /** Each allocation must be exclusive until close; the client supplies native memory. */
    @FunctionalInterface public interface MemoryFactory { Memory allocate(int bytes); }
    public interface Memory extends AutoCloseable {
        ByteBuffer buffer();
        @Override void close();
    }

    @FunctionalInterface
    interface CacheSink {
        void save(Path path, RingAtlasCacheImage snapshot) throws java.io.IOException;
    }

    public CompletableFuture<Void> submit(Path path, RingTerrainAtlas atlas) {
        java.util.Objects.requireNonNull(atlas,"atlas");
        Path target = path.toAbsolutePath().normalize();
        synchronized (this) {
            Write previous = pending.get(target);
            // A pending snapshot cannot be read by the writer while this lock
            // is held. An active write is removed from pending and never lends
            // its storage until all encoding and file replacement have finished.
            RingAtlasCacheImage candidate = previous == null ? reusableSnapshot : previous.snapshot();
            RingAtlasCacheImage snapshot = candidate;
            if (snapshot == null || !snapshot.matches(atlas)) {
                snapshot = new RingAtlasCacheImage(atlas,memoryFactory);
            }
            try { atlas.copyCacheImageTo(snapshot); }
            catch(RuntimeException | Error failure) {
                if(snapshot==candidate)abortPending(failure,false);
                snapshot.close();
                throw failure;
            }
            if(previous==null)reusableSnapshot=null;
            if(candidate!=null && candidate!=snapshot)candidate.close();
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
        if(reusableSnapshot!=null)reusableSnapshot.close();
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
                    } else write.snapshot().close();
                }
            }
        }
    }

    private void abortPending(Throwable failure) {
        abortPending(failure,true);
    }

    private void abortPending(Throwable failure,boolean resetDrain) {
        Write[] rejected;
        synchronized (this) {
            if(reusableSnapshot!=null)reusableSnapshot.close();
            reusableSnapshot = null;
            snapshotGeneration++;
            rejected = pending.values().toArray(Write[]::new);
            pending.clear();
            if(resetDrain)draining = false;
            notifyAll();
        }
        for (Write write : rejected) {
            try { write.snapshot().close(); } finally { write.completion().completeExceptionally(failure); }
        }
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

    private record Write(RingAtlasCacheImage snapshot, CompletableFuture<Void> completion, long generation) {}

    @jdk.jfr.Name("ringworld.AtlasCacheSave")
    @jdk.jfr.Label("RingWorld Atlas cache save")
    private static final class CacheSaveEvent extends jdk.jfr.Event {
        long revision;
        int cells;
        boolean success;
    }
}
