package dev.ringworld.server;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void failedStartReleasesTheTicketAndPreservesReleaseFailure() {
        AtomicInteger releases = new AtomicInteger();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RingAtlasChunkRequest.start(() -> {
                    throw new IllegalStateException("load failed");
                }, () -> "unused", () -> {
                    releases.incrementAndGet();
                    throw new IllegalArgumentException("release failed");
                }));

        assertEquals("load failed", failure.getMessage());
        assertEquals(1, releases.get());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("release failed", failure.getSuppressed()[0].getMessage());
    }

    @Test
    void nullLoadFutureFailsClosedAndReleasesTheTicket() {
        AtomicInteger releases = new AtomicInteger();

        assertThrows(NullPointerException.class, () -> RingAtlasChunkRequest.start(
                () -> null, () -> "unused", releases::incrementAndGet));
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
