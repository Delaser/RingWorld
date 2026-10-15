package dev.ringworld.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RingChunkUnloadProgressTest {
    @TempDir Path directory;

    @Test void completedSaveFutureMustNotStarveQueuedGenerationRelease() throws Exception {
        var callbacks = new ArrayDeque<Runnable>();
        var ownerTasks = new ArrayDeque<Runnable>();
        var references = new AtomicInteger(1);
        var attempts = new AtomicInteger();
        var writes = new AtomicInteger();
        var failure = new AtomicReference<Throwable>();
        var schedule = new AtomicReference<Runnable>();
        var saved = directory.resolve("saved.txt");
        var saveDependency = CompletableFuture.completedFuture(null);
        schedule.set(() -> saveDependency.thenRunAsync(() -> {
            attempts.incrementAndGet();
            if (references.get() != 0) schedule.get().run();
            else {
                try { Files.writeString(saved, "durable edited chunk"); writes.incrementAndGet(); }
                catch (Exception problem) { throw new RuntimeException(problem); }
            }
        }, callbacks::add).whenComplete((ignored, problem) -> {
            if (problem != null) failure.set(problem);
        }));
        ownerTasks.add(references::decrementAndGet);
        schedule.get().run();

        // Reproduce the observed native shutdown shape with real completed
        // futures: each retry keeps the queue nonempty and readiness unchanged.
        for (int index = 0; index < 8; index++) callbacks.remove().run();
        assertEquals(8, attempts.get()); assertEquals(1, callbacks.size());
        assertEquals(1, references.get()); assertFalse(Files.exists(saved));

        int remainingBudget = 4;
        while (!callbacks.isEmpty() && remainingBudget-- > 0) {
            RingChunkUnloadProgress.run(callbacks.remove(), () -> {
                Runnable owner = ownerTasks.poll();
                if (owner != null) owner.run();
            });
        }
        assertTrue(callbacks.isEmpty()); assertTrue(ownerTasks.isEmpty());
        assertEquals(0, references.get()); assertEquals(1, writes.get());
        assertEquals("durable edited chunk", Files.readString(saved));
        assertNull(failure.get()); assertFalse(saveDependency.isCancelled());
    }

    @Test void exceptionsRemainVisibleInsteadOfDiscardingOwnerWork() {
        var expected = new IllegalStateException("generation failed");
        assertSame(expected, assertThrows(IllegalStateException.class,
                () -> RingChunkUnloadProgress.run(() -> {}, () -> { throw expected; })));
    }
}
