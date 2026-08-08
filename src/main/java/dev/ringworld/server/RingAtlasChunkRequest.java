package dev.ringworld.server;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Owns one non-blocking, ticket-backed Minecraft chunk load.
 *
 * <p>Minecraft 26.1.2's ordinary {@code getChunkFuture} blocks the server
 * thread with {@code managedBlock}. Atlas generation instead starts the
 * public ticket-and-load operation on the server thread and retains its
 * ticket until the completed chunk has been consumed there. The release is
 * idempotent so cancellation and world unload cannot leak a loading ticket.</p>
 */
final class RingAtlasChunkRequest<T> implements AutoCloseable {
    private final CompletableFuture<?> loadFuture;
    private final Supplier<T> loadedResult;
    private final Runnable release;
    private boolean closed;

    private RingAtlasChunkRequest(CompletableFuture<?> loadFuture,
                                  Supplier<T> loadedResult,
                                  Runnable release) {
        this.loadFuture = Objects.requireNonNull(loadFuture, "chunk load returned no future");
        this.loadedResult = Objects.requireNonNull(loadedResult, "loadedResult");
        this.release = Objects.requireNonNull(release, "release");
    }

    static <T> RingAtlasChunkRequest<T> start(Supplier<CompletableFuture<?>> load,
                                               Supplier<T> loadedResult,
                                               Runnable release) {
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(release, "release");
        try {
            return new RingAtlasChunkRequest<>(load.get(), loadedResult, release);
        } catch (RuntimeException | Error failure) {
            try {
                release.run();
            } catch (RuntimeException | Error releaseFailure) {
                failure.addSuppressed(releaseFailure);
            }
            throw failure;
        }
    }

    boolean isDone() {
        return loadFuture.isDone();
    }

    T joinResult() {
        loadFuture.join();
        return loadedResult.get();
    }

    void cancel() {
        cancelWithReleaseAttempts(1);
    }

    /**
     * Teardown has no later server tick on which to retry a transient ticket
     * release failure. Retry synchronously there, while retaining the same
     * request and preserving fail-closed behavior if every attempt fails.
     */
    void cancelWithReleaseAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        // A completed load future does not guarantee that its chunk remains in
        // the cache during level teardown. Cancellation deliberately never
        // calls loadedResult; it only cancels where possible and releases the
        // retained ticket.
        loadFuture.cancel(false);
        RuntimeException previous = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                close();
                return;
            } catch (RuntimeException releaseFailure) {
                if (previous != null && previous != releaseFailure) {
                    releaseFailure.addSuppressed(previous);
                }
                previous = releaseFailure;
            }
        }
        throw previous;
    }

    @Override
    public void close() {
        if (closed) return;
        release.run();
        closed = true;
    }
}
