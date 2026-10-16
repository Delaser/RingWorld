package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.management.ManagementFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RingSurfaceBuildWorkspaceTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(128, 2048);
    private static volatile long blackhole;

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8})
    void reusedCaptureReplacesAllContentAndCountersIncludingMissingCells(int step) {
        var live = new RingTerrainAtlas(GEOMETRY, 42L, step);
        for (int z = 0; z < live.rows(); z++) for (int x = 0; x < live.columns(); x++) {
            live.putCell(x, z, (x + z) % 300 - 64, x * 12345 ^ z, (x ^ z) & 15,
                    x * 98765 ^ z, (x + z) & 15);
        }
        live.advanceRevision();
        var workspace = new RingSurfaceBuildWorkspace();
        RingTerrainAtlas captured;
        try (var lease = workspace.capture(live)) {
            captured = lease.atlas();
            assertEquivalent(live.snapshot(), captured);
            live.clear();
            assertTrue(captured.isComplete(), "Later live mutations cannot alter an active capture");
        }
        live.putCell(0, 0, 123, 0x123456, 7, 0xabcdef, 11);
        live.advanceRevision();
        try (var lease = workspace.capture(live)) {
            assertSame(captured, lease.atlas());
            assertEquivalent(live.snapshot(), lease.atlas());
            assertFalse(lease.atlas().hasCell(1, 0));
        }
    }

    @Test
    void overlappingCaptureAndUseAfterCloseAreRejected() {
        var workspace = new RingSurfaceBuildWorkspace();
        var live = new RingTerrainAtlas(GEOMETRY, 42L, 8);
        var lease = workspace.capture(live);
        assertThrows(IllegalStateException.class, () -> workspace.capture(live));
        lease.close();
        lease.close();
        assertThrows(IllegalStateException.class, lease::atlas);
        assertThrows(IllegalStateException.class, () -> lease.pixels(12));
        assertThrows(IllegalStateException.class, () -> lease.blockLights(12));
        assertThrows(IllegalStateException.class, () -> lease.heights(12));
        try (var next = workspace.capture(live)) { assertNotSame(lease, next); }
    }

    @Test
    void scratchArraysAreReusedButReplacedOnResolutionChange() {
        var workspace = new RingSurfaceBuildWorkspace();
        var live = new RingTerrainAtlas(GEOMETRY, 42L, 8);
        int[] pixels, lights;
        float[] heights;
        try (var lease = workspace.capture(live)) {
            pixels = lease.pixels(128); lights = lease.blockLights(128); heights = lease.heights(128);
            assertNotSame(pixels, lights);
            assertThrows(IllegalArgumentException.class, () -> lease.pixels(0));
            assertThrows(IllegalArgumentException.class, () -> lease.blockLights(-1));
            assertThrows(IllegalArgumentException.class, () -> lease.heights(0));
        }
        try (var lease = workspace.capture(live)) {
            assertSame(pixels, lease.pixels(128)); assertSame(lights, lease.blockLights(128));
            assertSame(heights, lease.heights(128));
            assertNotSame(pixels, lease.pixels(256)); assertEquals(256, lease.pixels(256).length);
            assertNotSame(lights, lease.blockLights(256));
            assertNotSame(heights, lease.heights(256));
        }
    }

    @Test
    void newSessionDoesNotOverwriteAnAbandonedWorkersCapture() throws Exception {
        var oldLive = new RingTerrainAtlas(GEOMETRY, 42L, 8);
        oldLive.putCell(0, 0, 80, 0x123456);
        var oldWorkspace = new RingSurfaceBuildWorkspace();
        var oldLease = oldWorkspace.capture(oldLive);
        var ready = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var abandoned = CompletableFuture.supplyAsync(() -> {
            try (oldLease) {
                ready.countDown();
                if (!finish.await(10, TimeUnit.SECONDS)) throw new AssertionError("Worker was not released");
                return oldLease.atlas().cellHeight(0, 0);
            } catch (InterruptedException exception) { throw new AssertionError(exception); }
        });
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            var newLive = new RingTerrainAtlas(GEOMETRY, 99L, 8);
            newLive.putCell(0, 0, 140, 0xabcdef);
            try (var newLease = new RingSurfaceBuildWorkspace().capture(newLive)) {
                assertEquals(140, newLease.atlas().cellHeight(0, 0));
                assertThrows(IllegalStateException.class, () -> oldWorkspace.capture(newLive));
            }
        } finally { finish.countDown(); }
        assertEquals(80, abandoned.get(10, TimeUnit.SECONDS));
    }

    @Test
    void differentWorldLayoutAndSamplingCannotInheritOldSnapshotStorage() {
        var workspace = new RingSurfaceBuildWorkspace();
        RingTerrainAtlas previous = null;
        for (var live : new RingTerrainAtlas[]{
                new RingTerrainAtlas(GEOMETRY, 42L, 8),
                new RingTerrainAtlas(GEOMETRY, 43L, 8),
                new RingTerrainAtlas(new RingGeometry(416, 2048), 43L, 8),
                new RingTerrainAtlas(new RingGeometry(416, 2048), 43L, 4)}) {
            try (var lease = workspace.capture(live)) {
                assertNotSame(previous, lease.atlas());
                assertEquivalent(live, lease.atlas());
                previous = lease.atlas();
            }
        }
    }

    @Test
    void invalidSnapshotDestinationFailsBeforeWriting() {
        var live = new RingTerrainAtlas(GEOMETRY, 42L, 8);
        assertThrows(IllegalArgumentException.class, () -> live.copySnapshotTo(live));
        for (var destination : new RingTerrainAtlas[]{
                new RingTerrainAtlas(GEOMETRY, 43L, 8),
                new RingTerrainAtlas(GEOMETRY, 42L, 4),
                new RingTerrainAtlas(new RingGeometry(416, 2048), 42L, 8)}) {
            destination.putCell(0, 0, 99, 0xabcdef);
            long before = destination.contentFingerprint();
            assertThrows(IllegalArgumentException.class, () -> live.copySnapshotTo(destination));
            assertEquals(before, destination.contentFingerprint());
            assertEquals(99, destination.cellHeight(0, 0));
        }
    }

    @Test
    void repeatedWarmCapturesDoNotAllocateAtlasOrPixelArrays() {
        var bean = assertInstanceOf(com.sun.management.ThreadMXBean.class, ManagementFactory.getThreadMXBean());
        assertTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        var workspace = new RingSurfaceBuildWorkspace();
        var live = new RingTerrainAtlas(GEOMETRY, 42L, 8);
        captureLoop(workspace, live, 200);
        long id = Thread.currentThread().getId(), start = bean.getThreadAllocatedBytes(id);
        captureLoop(workspace, live, 500);
        long allocated = bean.getThreadAllocatedBytes(id) - start;
        assertTrue(allocated < 65_536L, "Warm capture/scratch reuse allocated " + allocated + " bytes");
        System.out.println("Reusable surface workspace: " + allocated + " bytes for 500 captures");
    }

    private static void captureLoop(RingSurfaceBuildWorkspace workspace, RingTerrainAtlas live, int count) {
        long checksum = 0;
        for (int i = 0; i < count; i++) try (var lease = workspace.capture(live)) {
            checksum += lease.atlas().contentFingerprint() + lease.pixels(4096).length
                    + lease.blockLights(4096).length + lease.heights(4096).length;
        }
        blackhole = checksum;
    }

    private static void assertEquivalent(RingTerrainAtlas expected, RingTerrainAtlas actual) {
        assertEquals(expected.geometry(), actual.geometry()); assertEquals(expected.worldHash(), actual.worldHash());
        assertEquals(expected.sampleStep(), actual.sampleStep()); assertEquals(expected.revision(), actual.revision());
        assertEquals(expected.contentFingerprint(), actual.contentFingerprint());
        assertEquals(expected.presentCount(), actual.presentCount()); assertEquals(expected.presentChunkCount(), actual.presentChunkCount());
        assertEquals(expected.completion(), actual.completion());
        for (int z = 0; z < expected.tileRows(); z++) for (int x = 0; x < expected.tileColumns(); x++) {
            assertArrayEquals(expected.encodeTile(x, z), actual.encodeTile(x, z));
        }
    }
}
