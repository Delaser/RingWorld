package dev.ringworld.world;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RingAtlasCacheWriterTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(128, 2048);

    @Test void capturesSnapshotWithoutDiskWorkAndCoalescesPendingRevisions(@TempDir Path directory)
            throws Exception {
        var tasks = new ArrayDeque<Runnable>();
        var writer = new RingAtlasCacheWriter(tasks::add);
        var atlas = new RingTerrainAtlas(GEOMETRY, 123, 1);
        Path path = directory.resolve("atlas.gz");
        atlas.putCell(0, 0, 10, 42);
        atlas.commitRevision(1);
        var first = writer.submit(path, atlas);
        atlas.putCell(0, 0, 20, 43);
        atlas.commitRevision(2);
        var latest = writer.submit(path, atlas);
        atlas.putCell(0, 0, 30, 44);
        assertFalse(Files.exists(path));
        assertFalse(first.isDone());
        assertEquals(1, tasks.size());
        tasks.remove().run();
        assertTrue(first.isDone());
        latest.join();
        var saved = RingTerrainAtlas.load(path, GEOMETRY, 123);
        assertEquals(2, saved.revision());
        assertEquals(20, saved.cellHeight(0, 0));
    }

    @Test void finalSavesRetainPathsAcrossWorldSwitchAndReentry(@TempDir Path directory)
            throws Exception {
        var tasks = new ArrayDeque<Runnable>();
        var writer = new RingAtlasCacheWriter(tasks::add);
        var a = new RingTerrainAtlas(GEOMETRY, 1, 8);
        var b = new RingTerrainAtlas(GEOMETRY, 2, 8);
        Path pathA = directory.resolve("a.gz"), pathB = directory.resolve("b.gz");
        a.commitRevision(1);
        writer.submit(pathA, a);
        writer.submit(pathB, b);
        a.commitRevision(3);
        var finalSave = writer.submit(pathA, a);
        tasks.remove().run();
        finalSave.join();
        assertEquals(3, RingTerrainAtlas.load(pathA, GEOMETRY, 1).revision());
        assertEquals(2, RingTerrainAtlas.load(pathB, GEOMETRY, 2).worldHash());
    }

    @Test void failureDoesNotBlockOtherSavesOrRetry(@TempDir Path directory) throws Exception {
        var tasks = new ArrayDeque<Runnable>();
        var writer = new RingAtlasCacheWriter(tasks::add);
        Path obstruction = directory.resolve("not-a-directory");
        Files.writeString(obstruction, "blocked");
        var atlas = new RingTerrainAtlas(GEOMETRY, 1, 8);
        var failed = writer.submit(obstruction.resolve("cache.gz"), atlas);
        var good = writer.submit(directory.resolve("good.gz"), atlas);
        tasks.remove().run();
        assertTrue(failed.isCompletedExceptionally());
        good.join();
        Files.delete(obstruction);
        var retry = writer.submit(obstruction.resolve("cache.gz"), atlas);
        tasks.remove().run();
        retry.join();
        assertTrue(Files.exists(obstruction.resolve("cache.gz")));
    }

    @Test void revisionArrivingWhileWorkerDrainsIsWrittenAfterActiveSnapshot(@TempDir Path directory)
            throws Exception {
        var tasks = new ArrayDeque<Runnable>();
        var writer = new RingAtlasCacheWriter(tasks::add);
        var atlas = new RingTerrainAtlas(GEOMETRY, 1, 8);
        Path path = directory.resolve("atlas.gz");
        atlas.commitRevision(1);
        var first = writer.submit(path, atlas);
        first.thenRun(() -> {
            try {
                atlas.commitRevision(2);
                writer.submit(path, atlas);
            } catch (java.io.IOException exception) {
                throw new AssertionError(exception);
            }
        });
        tasks.remove().run();
        assertEquals(2, RingTerrainAtlas.load(path, GEOMETRY, 1).revision());
        assertTrue(tasks.isEmpty());
    }
}
