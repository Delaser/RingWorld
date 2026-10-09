package dev.ringworld.server;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingAtlasChunkRequestTest {
    @Test
    void retainsLoadUntilResultIsReadyAndReleasesExactlyOnce() {
        CompletableFuture<Void> load = new CompletableFuture<>();
        AtomicInteger releases = new AtomicInteger();
        RingAtlasChunkRequest<String> request = RingAtlasChunkRequest.start(
                () -> load, () -> "full chunk", releases::incrementAndGet);

        assertFalse(request.isDone());
        load.complete(null);
        assertTrue(request.isDone());
        assertEquals("full chunk", request.joinResult());

        request.close();
        request.close();
        assertEquals(1, releases.get());
    }

    @Test
    void cancellationClosesTheTicketLeaseExactlyOnce() {
        CompletableFuture<Void> load = new CompletableFuture<>();
        AtomicInteger releases = new AtomicInteger();
        RingAtlasChunkRequest<String> request = RingAtlasChunkRequest.start(
                () -> load, () -> "unused", releases::incrementAndGet);

        request.cancel();
        request.cancel();

        assertTrue(load.isCancelled());
        assertEquals(1, releases.get());
    }

    @Test
    void cancellationOfCompletedLoadNeverResolvesAnUnloadedResult() {
        AtomicInteger resultReads = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        RingAtlasChunkRequest<String> request = RingAtlasChunkRequest.start(
                () -> CompletableFuture.completedFuture(null), () -> {
                    resultReads.incrementAndGet();
                    return null;
                }, releases::incrementAndGet);

        assertTrue(request.isDone());
        request.cancel();
        request.cancel();

        assertEquals(0, resultReads.get());
        assertEquals(1, releases.get());
    }

    @Test
    void failedStartRetainsLeaseForCleanupAndPreservesBothFailures() {
        AtomicInteger releases = new AtomicInteger();
        AtomicInteger resultReads = new AtomicInteger();
        RingAtlasChunkRequest<String> request = RingAtlasChunkRequest.start(() -> {
            throw new IllegalStateException("load failed");
        }, () -> { resultReads.incrementAndGet(); return "unused"; }, () -> {
            if (releases.incrementAndGet() == 1) throw new IllegalArgumentException("release failed");
        });
        assertTrue(request.isDone());
        CompletionException failure = assertThrows(CompletionException.class, request::joinResult);
        assertEquals("load failed", failure.getCause().getMessage());
        assertEquals("release failed", assertThrows(IllegalArgumentException.class, request::cancel).getMessage());
        request.cancel();
        request.close();
        assertEquals(2, releases.get());
        assertEquals(0, resultReads.get());
    }

    @Test
    void nullLoadFutureRetainsFailedRequestUntilTicketIsReleased() {
        AtomicInteger releases = new AtomicInteger();
        RingAtlasChunkRequest<String> request = RingAtlasChunkRequest.start(
                () -> null, () -> "unused", releases::incrementAndGet);
        assertInstanceOf(NullPointerException.class,
                assertThrows(CompletionException.class, request::joinResult).getCause());
        assertEquals(0, releases.get());
        request.cancel();
        assertEquals(1, releases.get());
    }

    @Test
    void failedReleaseRemainsRetryable() {
        AtomicInteger releases = new AtomicInteger();
        RingAtlasChunkRequest<String> request = RingAtlasChunkRequest.start(
                () -> CompletableFuture.completedFuture(null), () -> "full chunk", () -> {
                    if (releases.incrementAndGet() == 1) {
                        throw new IllegalStateException("transient release failure");
                    }
                });

        assertThrows(IllegalStateException.class, request::close);
        request.close();
        request.close();
        assertEquals(2, releases.get());
    }

    @Test
    void teardownRetriesTransientReleaseBeforeReturning() {
        AtomicInteger releases = new AtomicInteger();
        RingAtlasChunkRequest<String> request = RingAtlasChunkRequest.start(
                () -> new CompletableFuture<>(), () -> "unused", () -> {
                    if (releases.incrementAndGet() < 3) {
                        throw new IllegalStateException("transient release failure");
                    }
                });

        request.cancelWithReleaseAttempts(3);
        request.cancelWithReleaseAttempts(3);

        assertEquals(3, releases.get());
    }
}
