package dev.ringworld.server;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.AtlasPregenerationOptions;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RingAtlasPregenerationBatchTest {
    private final RingTerrainAtlas atlas = new RingTerrainAtlas(new RingGeometry(256, 1024), 7L);

    @Test void sharedCursorNeverDuplicatesConcurrentSelectionsAndRetriesStayLocal() {
        var batch = new RingAtlasPregenerationBatch<String>(atlas, 8);
        var indices = new HashSet<Long>();
        for (var slot : batch.slots) assertTrue(indices.add(slot.selection.select().orElseThrow().index()));
        var failed = batch.slots.get(0);
        var original = failed.selection.selected();
        assertTrue(failed.selection.failed(10, 3));
        assertFalse(failed.selection.mayRetryAt(29));
        assertEquals(original, failed.selection.select().orElseThrow());
        var other = batch.slots.get(7);
        other.selection.captured();
        assertEquals(8, other.selection.select().orElseThrow().index());
        assertEquals(1, failed.selection.retryAttempt());
        assertEquals(0, other.selection.retryAttempt());
    }

    @Test void laterCompletionDoesNotWaitOnAnEarlierSlowRequestAndReadySlotsRotate() {
        var batch = new RingAtlasPregenerationBatch<String>(atlas, 4);
        var pending = new CompletableFuture<Void>();
        batch.slots.get(0).request = RingAtlasChunkRequest.start(() -> pending, () -> "slow", () -> {});
        for (int i=1; i<4; i++) batch.slots.get(i).request = ready(() -> {});
        assertEquals(4, batch.inFlight());
        assertSame(batch.slots.get(1), batch.nextCompleted());
        batch.slots.get(1).processed = true;
        assertSame(batch.slots.get(2), batch.nextCompleted());
        batch.slots.get(2).processed = true;
        assertSame(batch.slots.get(3), batch.nextCompleted());
        batch.slots.get(3).processed = true;
        assertNull(batch.nextCompleted());
        pending.complete(null);
        assertSame(batch.slots.get(0), batch.nextCompleted());
    }

    @Test void cancellationAttemptsEveryTicketAndRetainsOnlyFailedReleasesForRetry() {
        var batch = new RingAtlasPregenerationBatch<String>(atlas, 4);
        var releases = new AtomicInteger();
        var broken = new AtomicInteger();
        batch.slots.get(0).request = ready(() -> {
            if (broken.incrementAndGet() == 1) throw new IllegalStateException("release failed");
        });
        for (int i=1; i<4; i++) batch.slots.get(i).request = ready(releases::incrementAndGet);
        assertThrows(IllegalStateException.class, () -> batch.cancelAll(1));
        assertEquals(3, releases.get());
        assertEquals(1, batch.inFlight());
        batch.cancelAll(1);
        batch.cancelAll(1);
        assertEquals(2, broken.get());
        assertEquals(3, releases.get());
        assertEquals(0, batch.inFlight());
    }

    @Test void cancelledCompletedLoadsNeverResolveResultsAndResumeStartsAtMissingCells() {
        var batch = new RingAtlasPregenerationBatch<String>(atlas, 4);
        var reads = new AtomicInteger();
        for (var slot : batch.slots) {
            slot.selection.select();
            slot.request = RingAtlasChunkRequest.start(() -> CompletableFuture.completedFuture(null),
                    () -> { reads.incrementAndGet(); return "do not read on unload"; }, () -> {});
        }
        assertTrue(batch.hasWork());
        batch.cancelAll(3);
        assertEquals(0, reads.get());
        assertEquals(0, batch.inFlight());
        var restarted = new RingAtlasPregenerationBatch<String>(atlas, 4);
        assertEquals(0, restarted.slots.get(0).selection.select().orElseThrow().index());
    }

    @Test void trialAdmissionKeepsSerialDefaultAndRejectsUnboundedRequests() {
        assertEquals(1, AtlasPregenerationOptions.trialConcurrency(null));
        for (int count : new int[]{1,2,4,8}) assertEquals(count, AtlasPregenerationOptions.trialConcurrency(""+count));
        for (String bad : new String[]{"0","9","-1","a",""})
            assertThrows(IllegalArgumentException.class, () -> AtlasPregenerationOptions.trialConcurrency(bad));
        assertThrows(IllegalArgumentException.class, () -> new RingAtlasPregenerationBatch<String>(atlas, 0));
        assertThrows(IllegalArgumentException.class, () -> new RingAtlasPregenerationBatch<String>(atlas, 9));
    }

    @Test void restartUsesDurableCellsAfterOutOfOrderCapturesLeavingEarlierHoles() {
        var batch = new RingAtlasPregenerationBatch<String>(atlas, 4);
        for (var slot : batch.slots) slot.selection.select();
        for (int index : new int[]{1,3}) {
            var selected = batch.slots.get(index).selection.selected();
            int z0 = atlas.geometry().minWidthZ() + selected.chunkRow() * 16;
            for (int z=0; z<16; z++) for (int x=0; x<16; x++)
                atlas.putBlockSample(selected.chunkX()*16+x, z0+z, 70, 0x123456);
            batch.slots.get(index).selection.captured();
        }
        var restarted = new RingAtlasPregenerationBatch<String>(atlas, 4);
        assertEquals(java.util.List.of(0L,2L,4L,5L), restarted.slots.stream()
                .map(slot -> slot.selection.select().orElseThrow().index()).toList());
    }

    @Test void multipleFailedReleasesStayReachableAndDoNotPreventOtherTicketsClosing() {
        var batch = new RingAtlasPregenerationBatch<String>(atlas, 4);
        var goodReleases = new AtomicInteger();
        for (int i=0; i<4; i++) {
            String ticket = "ticket-" + i;
            if (i%2==0) batch.slots.get(i).request = ready(() -> { throw new IllegalStateException(ticket); });
            else batch.slots.get(i).request = ready(goodReleases::incrementAndGet);
        }
        var failure = assertThrows(IllegalStateException.class, () -> batch.cancelAll(3));
        assertEquals("ticket-0", failure.getMessage());
        // Teardown retains each attempt's failure too; require the other lease's
        // failure rather than assuming the aggregate contains no retry history.
        assertTrue(java.util.Arrays.stream(failure.getSuppressed())
                .anyMatch(other -> "ticket-2".equals(other.getMessage())));
        assertEquals(2, goodReleases.get());
        assertEquals(2, batch.inFlight());
    }

    private RingAtlasChunkRequest<String> ready(Runnable release) {
        return RingAtlasChunkRequest.start(() -> CompletableFuture.completedFuture(null), () -> "chunk", release);
    }
}
