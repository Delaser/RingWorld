package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingGeometry;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.GZIPInputStream;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Opt-in, server-thread comparison using real Atlas data and a separate disposable file. */
final class RingAtlasCheckpointProbe {
    private final Path path;
    private final RingAtlasCheckpointWriter writer;
    private final CompletableFuture<RingTerrainAtlas> source;
    private int operation;
    private int nextTick;
    private int submittedTick;
    private double ownerMs;
    private double observedTickMs;
    private boolean tickObserved;
    private CompletableFuture<RingAtlasCheckpointWriter.Result> pending;

    RingAtlasCheckpointProbe(Path atlasPath) {
        path = atlasPath.resolveSibling("checkpoint-performance-probe.rwat.gz");
        writer = new RingAtlasCheckpointWriter(path, false);
        String sourcePath = System.getProperty("ringworld.atlasCheckpointProbeSource");
        source = sourcePath == null ? null : CompletableFuture.supplyAsync(() -> {
            try { return readSource(Path.of(sourcePath)); }
            catch (IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
        });
    }

    void tick(MinecraftServer server, RingTerrainAtlas atlas) {
        if (operation >= 14) return;
        if (source != null) {
            if (!source.isDone()) return;
            try { atlas = source.join(); }
            catch (RuntimeException failure) {
                operation = 14;
                RingWorldMod.LOGGER.error("[atlas-checkpoint-probe] FAILED source", failure);
                return;
            }
        }
        if (!atlas.isComplete()) return;
        int tick = server.getTickCount();
        try {
            if (pending != null) {
                writer.advance(atlas);
                if (tick > submittedTick) {
                    long[] times = server.getTickTimesNanos();
                    observedTickMs = Math.max(observedTickMs,
                            times[Math.floorMod(tick - 1, times.length)] / 1e6);
                    tickObserved = true;
                }
                if (!pending.isDone() || !tickObserved) return;
                var result = pending.join();
                writer.poll();
                boolean asynchronous = asynchronous(operation);
                RingWorldMod.LOGGER.info("[atlas-checkpoint-probe] mode={} warmup={} operation={} cells={} ownerMs={} snapshotMs={} writeMs={} tickMs={} heapUsedMiB={}",
                        asynchronous ? "async" : "sync", operation < 2, operation, atlas.cellCount(), asynchronous ? Math.max(ownerMs, result.maxCopyStepNanos() / 1e6) : ownerMs,
                        result.snapshotNanos() / 1e6, result.writeNanos() / 1e6, observedTickMs,
                        (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576);
                pending = null;
                operation++;
                nextTick = tick + 200;
                if (operation == 14) {
                    RingWorldMod.LOGGER.info("[atlas-checkpoint-probe] COMPLETE measuredPairs=6");
                    writer.close();
                }
                return;
            }
            if (tick < nextTick) return;
            submittedTick = tick;
            tickObserved = false;
            observedTickMs = 0;
            long start = System.nanoTime();
            if (asynchronous(operation)) {
                writer.markDirty();
                pending = writer.request(atlas, false);
            } else {
                // Exactly the pre-fix periodic path, using the same unchanged disk codec.
                atlas.save(path);
                pending = CompletableFuture.completedFuture(new RingAtlasCheckpointWriter.Result(
                        0, atlas.revision(), atlas.presentCount(), 0, System.nanoTime() - start, 0, 0));
            }
            ownerMs = (System.nanoTime() - start) / 1e6;
        } catch (Exception failure) {
            operation = 14;
            RingWorldMod.LOGGER.error("[atlas-checkpoint-probe] FAILED", failure);
        }
    }

    /** Test input only: reads the backed-up data without installing or migrating an authoritative cache. */
    static RingTerrainAtlas readSource(Path source) throws IOException {
        try (var input = new DataInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(source))))) {
            if (input.readInt() != 0x52574154) throw new IOException("not an Atlas probe input");
            int format = input.readInt();
            if (format != 10 && format != 11) throw new IOException("unsupported probe input format");
            long hash = input.readLong();
            int width = input.readInt();
            int circumference = input.readInt();
            int step = input.readInt();
            RingTerrainAtlas atlas = new RingTerrainAtlas(new RingGeometry(width, circumference), hash, step);
            if (input.readInt() != atlas.columns() || input.readInt() != atlas.rows()) throw new IOException("probe dimensions");
            long revision = input.readLong();
            for (int row = 0; row < atlas.rows(); row++) for (int column = 0; column < atlas.columns(); column++) {
                boolean present = input.readBoolean();
                int height = input.readShort();
                int color = input.readInt();
                int packed = input.readUnsignedByte();
                int side = input.readInt();
                if (present) atlas.putCell(column, row, height, color, packed & 15, side, packed >>> 4);
            }
            atlas.commitRevision(revision);
            if (input.read() != -1 || !atlas.isComplete()) throw new IOException("probe requires complete Atlas data");
            return atlas;
        }
    }

    // Warm each path once, then alternate AB / BA for six measured pairs.
    private static boolean asynchronous(int operation) {
        if (operation < 2) return operation == 1;
        int measured = operation - 2;
        return (measured % 2 == 1) != ((measured / 2) % 2 == 1);
    }

    void close(RingTerrainAtlas atlas) {
        if (source != null) atlas = source.join();
        writer.awaitActive(atlas);
        writer.poll();
        writer.close();
    }
}
