package dev.ringworld.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RingFrameStreamWriterTest {
    @TempDir Path directory;

    @Test void delayedWorkerRetainsEveryFrameAndExactCrossWindowBoundaries() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        var release = new CountDownLatch(1);
        worker.execute(() -> {
            try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
        });
        var metadata = new JsonObject(); metadata.addProperty("role", "A");
        var writer = new RingFrameStreamWriter(directory, "A", metadata, 2, 3, worker);
        metadata.addProperty("role", "mutated");
        try {
            long start = 100;
            for (long duration : new long[]{11, 12, 13, 14, 15}) {
                writer.record(start, start + duration); start += duration;
            }
        } finally { release.countDown(); writer.close(); }
        long next = 100;
        for (int index = 0; index < 3; index++) {
            Path raw = directory.resolve("frames-runtime-A-" + index + ".rwfr");
            var report = JsonParser.parseString(Files.readString(raw.resolveSibling(
                    raw.getFileName().toString().replace(".rwfr", ".json")))).getAsJsonObject();
            assertEquals("A", report.get("role").getAsString());
            assertEquals(index, report.get("sequence").getAsInt());
            assertEquals(next, report.get("firstStartNanoTime").getAsLong());
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(raw))), report.get("sha256").getAsString());
            try (var input = new DataInputStream(Files.newInputStream(raw))) {
                assertEquals(0x52574652, input.readInt()); assertEquals(1, input.readInt());
                int count = input.readInt(); assertEquals(index == 2 ? 1 : 2, count);
                for (int frame = 0; frame < count; frame++) {
                    long duration = 11 + index * 2L + frame;
                    assertEquals(duration, input.readLong()); next += duration;
                }
                assertEquals(-1, input.read());
            }
            assertEquals(next, report.get("lastEndNanoTime").getAsLong());
        }
        assertTrue(worker.isTerminated());
        assertThrows(IllegalStateException.class, () -> writer.record(165, 166));
        writer.close();
    }

    @Test void exhaustionFailsWithoutDroppingSamplesOrWaitingForDisk() {
        var worker = Executors.newSingleThreadExecutor();
        var release = new CountDownLatch(1);
        worker.execute(() -> {
            try { release.await(); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
        });
        var writer = new RingFrameStreamWriter(directory, "A", new JsonObject(), 1, 2, worker);
        try {
            writer.record(1, 2); writer.record(2, 3);
            assertThrows(IllegalStateException.class, () -> writer.record(3, 4));
        } finally {
            release.countDown();
            assertThrows(IllegalStateException.class, writer::close);
        }
        assertTrue(worker.isTerminated());
    }

    @Test void diskFailureAndRejectedSubmissionCannotCloseSuccessfully() throws Exception {
        Path file = directory.resolve("occupied"); Files.writeString(file, "keep");
        var writer = new RingFrameStreamWriter(file, "A", new JsonObject());
        writer.record(1, 2);
        assertThrows(IllegalStateException.class, writer::close);
        assertEquals("keep", Files.readString(file));
        var stopped = Executors.newSingleThreadExecutor(); stopped.shutdown();
        var rejected = new RingFrameStreamWriter(directory, "B", new JsonObject(), 1, 2, stopped);
        assertThrows(IllegalStateException.class, () -> rejected.record(1, 2));
        assertThrows(IllegalStateException.class, rejected::close);
    }

    @Test void gapsOverlapsAndNonpositiveFramesAreRejected() {
        try (var writer = new RingFrameStreamWriter(directory, "A", new JsonObject())) {
            writer.record(100, 110);
            assertThrows(IllegalStateException.class, () -> writer.record(111, 112));
            assertThrows(IllegalStateException.class, () -> writer.record(109, 111));
            assertThrows(IllegalStateException.class, () -> writer.record(110, 110));
            writer.record(110, 120);
        }
    }
}
