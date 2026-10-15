package dev.ringworld.server;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Counts native asynchronous writes without changing their result or retaining chunks. */
public final class RingChunkWriteTracker {
    private final AtomicInteger active = new AtomicInteger();

    public int active() { return active.get(); }

    public <T> CompletableFuture<T> track(Supplier<CompletableFuture<T>> start) {
        active.incrementAndGet();
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) active.decrementAndGet();
        };
        try {
            CompletableFuture<T> future = Objects.requireNonNull(start.get(), "chunk write future");
            future.whenComplete((value, failure) -> release.run());
            return future;
        } catch (RuntimeException | Error failure) {
            release.run();
            throw failure;
        }
    }
}
