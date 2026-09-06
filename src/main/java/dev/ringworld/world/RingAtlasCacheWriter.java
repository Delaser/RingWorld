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
    private final LinkedHashMap<Path, Write> pending = new LinkedHashMap<>();
    private boolean draining;

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

    RingAtlasCacheWriter(Executor executor) { this.executor = executor; }

    public CompletableFuture<Void> submit(Path path, RingTerrainAtlas atlas) {
        Path target = path.toAbsolutePath().normalize();
        RingTerrainAtlas snapshot = atlas.snapshot();
        synchronized (this) {
            Write previous = pending.get(target);
            CompletableFuture<Void> completion = previous == null
                    ? new CompletableFuture<>() : previous.completion();
            pending.put(target, new Write(snapshot, completion));
            if (!draining) {
                draining = true;
                executor.execute(this::drain);
            }
            return completion;
        }
    }

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
                write.snapshot().save(path);
                event.success = true;
                write.completion().complete(null);
            } catch (Exception exception) {
                write.completion().completeExceptionally(exception);
            } finally {
                event.commit();
            }
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

    private record Write(RingTerrainAtlas snapshot, CompletableFuture<Void> completion) {}

    @jdk.jfr.Name("ringworld.AtlasCacheSave")
    @jdk.jfr.Label("RingWorld Atlas cache save")
    private static final class CacheSaveEvent extends jdk.jfr.Event {
        long revision;
        int cells;
        boolean success;
    }
}
