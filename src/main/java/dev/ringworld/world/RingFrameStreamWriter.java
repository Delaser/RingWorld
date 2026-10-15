package dev.ringworld.world;

import com.google.gson.JsonObject;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Bounded opt-in measurement I/O; an exhausted pool fails instead of blocking or dropping frames. */
public final class RingFrameStreamWriter implements AutoCloseable {
    private final Path directory;
    private final String label;
    private final JsonObject metadata;
    private final RingClockAnchor anchor;
    private final ArrayBlockingQueue<long[]> available;
    private final ExecutorService worker;
    private long[] current;
    private long firstStart, lastEnd;
    private long streamStart, totalSamples;
    private int count, sequence;
    private boolean closed;
    private boolean hasPrevious;
    private volatile Throwable failure;

    public RingFrameStreamWriter(Path directory, String label, JsonObject metadata) {
        this(directory, label, metadata, 16_384, 3, Executors.newSingleThreadExecutor(
                task -> new Thread(task, "RingWorld runtime frame evidence")));
    }

    public RingFrameStreamWriter(Path directory, String label, JsonObject metadata,
                                 int capacity, int buffers, ExecutorService worker) {
        if (!label.matches("[A-Za-z0-9._-]{1,48}") || capacity < 1 || capacity > 65_536
                || buffers < 2 || buffers > 4) throw new IllegalArgumentException("invalid frame evidence budget");
        this.directory = directory; this.label = label; this.metadata = metadata.deepCopy();
        this.anchor = RingClockAnchor.capture(); this.worker = worker;
        available = new ArrayBlockingQueue<>(buffers);
        for (int i = 0; i < buffers; i++) available.add(new long[capacity]);
    }

    public void record(long start, long end) {
        checkFailure();
        if (closed || end <= start || hasPrevious && start != lastEnd)
            throw new IllegalStateException("invalid runtime frame boundary");
        if (current == null) {
            current = available.poll();
            if (current == null) {
                failure = new IllegalStateException("Runtime frame evidence buffers exhausted; no samples dropped silently");
                checkFailure();
            }
            firstStart = start;
        }
        current[count++] = end - start;
        if (!hasPrevious) streamStart = start;
        hasPrevious = true;
        totalSamples++;
        lastEnd = end;
        if (count == current.length) submit();
    }

    private void submit() {
        long[] owned = current;
        int samples = count, ordinal = sequence++;
        long first = firstStart, last = lastEnd;
        current = null; count = 0;
        Runnable write = () -> {
            try {
                Files.createDirectories(directory);
                String name = "frames-runtime-" + label + '-' + ordinal;
                Path raw = directory.resolve(name + ".rwfr");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (var output = new DataOutputStream(new BufferedOutputStream(new DigestOutputStream(
                        Files.newOutputStream(raw, StandardOpenOption.CREATE_NEW), digest)))) {
                    output.writeInt(0x52574652); output.writeInt(1); output.writeInt(samples);
                    for (int index = 0; index < samples; index++) output.writeLong(owned[index]);
                }
                var report = metadata.deepCopy(); report.addProperty("schemaVersion", 1);
                report.addProperty("kind", "runtime-frame-window-not-performance-verdict");
                report.addProperty("samples", samples); report.addProperty("sequence", ordinal);
                report.addProperty("firstStartNanoTime", first); report.addProperty("lastEndNanoTime", last);
                report.addProperty("sha256", HexFormat.of().formatHex(digest.digest()));
                report.addProperty("clockAnchorNanoTime", anchor.nanoTime());
                report.addProperty("clockAnchorEpochSecond", anchor.epochSecond());
                report.addProperty("clockAnchorNanoAdjustment", anchor.nanoAdjustment());
                report.addProperty("clockAnchorUncertaintyNanos", anchor.uncertaintyNanos());
                try (var output = Files.newBufferedWriter(directory.resolve(name + ".json"), StandardOpenOption.CREATE_NEW)) {
                    output.write(report.toString());
                }
            } catch (Throwable problem) { failure = problem; }
            finally { available.add(owned); }
        };
        try { worker.execute(write); }
        catch (RuntimeException problem) {
            failure = problem; available.add(owned); checkFailure();
        }
    }

    private void checkFailure() {
        if (failure != null) throw new IllegalStateException("Could not retain every runtime frame", failure);
    }

    /** Teardown only; ordinary recording never waits for disk. */
    @Override public void close() {
        if (closed) { checkFailure(); return; }
        closed = true;
        try {
            if (current != null && count > 0) submit();
        } finally {
            worker.shutdown();
            try {
                if (!worker.awaitTermination(60, TimeUnit.SECONDS))
                    throw new IllegalStateException("Runtime frame evidence shutdown timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted frame evidence shutdown", interrupted);
            }
        }
        checkFailure();
        try {
            if (totalSamples == 0) throw new IllegalStateException("Empty runtime frame stream");
            var report = new JsonObject();
            report.addProperty("schemaVersion", 1); report.addProperty("label", label);
            report.addProperty("kind", "complete-runtime-frame-stream-not-performance-verdict");
            report.addProperty("samples", totalSamples); report.addProperty("windows", sequence);
            report.addProperty("firstStartNanoTime", streamStart); report.addProperty("lastEndNanoTime", lastEnd);
            var end = RingClockAnchor.capture();
            report.addProperty("clockAnchorNanoTime", end.nanoTime());
            report.addProperty("clockAnchorEpochSecond", end.epochSecond());
            report.addProperty("clockAnchorNanoAdjustment", end.nanoAdjustment());
            report.addProperty("clockAnchorUncertaintyNanos", end.uncertaintyNanos());
            try (var output = Files.newBufferedWriter(directory.resolve("frames-runtime-" + label + "-complete.json"),
                    StandardOpenOption.CREATE_NEW)) { output.write(report.toString()); }
        } catch (Exception problem) { failure = problem; checkFailure(); }
    }
}
