package dev.ringworld.server;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingChunkWriteTrackerTest {
    @Test void pendingSuccessFailureAndCancellationKeepOriginalFuturesAndConverge() {
        var tracker = new RingChunkWriteTracker();
        var success = new CompletableFuture<String>();
        var failed = new CompletableFuture<String>();
        var cancelled = new CompletableFuture<String>();
        assertSame(success, tracker.track(() -> success));
        assertSame(failed, tracker.track(() -> failed));
        assertSame(cancelled, tracker.track(() -> cancelled));
        assertEquals(3, tracker.active());
        success.complete("saved");
        assertEquals(2, tracker.active());
        failed.completeExceptionally(new IllegalStateException("disk failure"));
        assertEquals(1, tracker.active());
        cancelled.cancel(false);
        assertEquals(0, tracker.active());
        assertEquals("saved", success.join());
        assertTrue(failed.isCompletedExceptionally());
        assertTrue(cancelled.isCancelled());
    }

    @Test void immediateResultsAndFailedStartsDoNotLeavePhantomWrites() {
        var tracker = new RingChunkWriteTracker();
        tracker.track(() -> CompletableFuture.completedFuture("saved"));
        tracker.track(() -> CompletableFuture.failedFuture(new IllegalArgumentException("failure")));
        assertThrows(IllegalStateException.class, () -> tracker.track(() -> { throw new IllegalStateException(); }));
        assertThrows(NullPointerException.class, () -> tracker.track(() -> null));
        assertEquals(0, tracker.active());
    }
}
