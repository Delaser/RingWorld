package dev.ringworld.server;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RingTerrainPreviewJobTest {
    @Test
    void cancellingRunningWorldFreesWorkerForNextWorld() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var started = new CountDownLatch(1);
        var stopped = new CountDownLatch(1);
        var first = new RingTerrainAtlasServer.PreviewJob(1);
        try {
            first.start(executor, () -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException expected) {
                    Thread.currentThread().interrupt();
                } finally {
                    stopped.countDown();
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            first.cancel();
            assertTrue(stopped.await(5, TimeUnit.SECONDS), "old world must stop promptly");
            var nextStarted = new CountDownLatch(1);
            var nextRelease = new CountDownLatch(1);
            var nextInterrupted = new AtomicBoolean();
            var next = new RingTerrainAtlasServer.PreviewJob(2);
            next.start(executor, () -> {
                nextInterrupted.set(Thread.currentThread().isInterrupted());
                nextStarted.countDown();
                try {
                    nextRelease.await();
                } catch (InterruptedException unexpected) {
                    nextInterrupted.set(true);
                }
            });
            assertTrue(nextStarted.await(5, TimeUnit.SECONDS));
            first.cancel(); // a repeated late unload must not interrupt the new world
            nextRelease.countDown();
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
            assertFalse(nextInterrupted.get());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void cancellingQueuedWorldSkipsItsWork() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var release = new CountDownLatch(1);
        var ran = new AtomicBoolean();
        try {
            executor.submit(() -> {
                try { release.await(); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            });
            var queued = new RingTerrainAtlasServer.PreviewJob(1);
            queued.start(executor, () -> ran.set(true));
            queued.cancel();
            release.countDown();
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
            assertFalse(ran.get());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
