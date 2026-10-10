package dev.ringworld.server;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTerrainAtlas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class RingAtlasCheckpointWriterTest {
    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private final RingGeometry geometry = new RingGeometry(128, 2048);
    private final RingTerrainAtlas atlas = new RingTerrainAtlas(geometry, 17);

    private void prepare(RingAtlasCheckpointWriter writer) {
        while (writer.preparing()) {
            if (!tasks.isEmpty()) tasks.remove().run();
            writer.advance(atlas);
        }
    }

    @Test void repairsEditsToCopiedSlicesBeforePublishingCoherentMetadata(@TempDir Path directory)
            throws Exception {
        Path file = directory.resolve("atlas.gz");
        var writer = new RingAtlasCheckpointWriter(file, true, tasks::add);
        atlas.putCell(0, 0, 70, 0x112233);
        writer.request(atlas, false);
        tasks.remove().run(); // Allocate privately on the worker.
        writer.advance(atlas, 0); // Exactly one bounded slice.
        assertTrue(tasks.isEmpty());
        atlas.putCell(0, 0, 90, 0x445566);
        writer.changedCell(0, 0, atlas.columns());
        atlas.putCell(1, 0, 85, 0x123456);
        writer.changedCell(1, 0, atlas.columns());
        atlas.advanceRevision();
        writer.markDirty();
        while (tasks.isEmpty()) writer.advance(atlas, 0);
        tasks.remove().run();
        writer.poll();
        assertFalse(writer.dirty());
        var saved = RingTerrainAtlas.load(file, geometry, 17);
        assertEquals(90, saved.cellHeight(0, 0));
        assertEquals(85, saved.cellHeight(1, 0));
        assertEquals(atlas.presentCount(), saved.presentCount());
        assertEquals(atlas.revision(), saved.revision());
        writer.close();
    }

    @Test void capturesOneSnapshotAndKeepsNewMutationsDirty(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("atlas.gz");
        var writer = new RingAtlasCheckpointWriter(file, false, tasks::add);
        assertNull(writer.request(atlas, false));
        atlas.putCell(0, 0, 70, 0x112233);
        writer.markDirty();
        var first = writer.request(atlas, false);
        assertFalse(Files.exists(file));
        prepare(writer);
        atlas.putCell(0, 0, 80, 0x445566);
        writer.markDirty();
        assertNull(writer.request(atlas, false));
        assertEquals(1, tasks.size());
        tasks.remove().run();
        assertTrue(first.isDone());
        assertEquals(1, writer.poll().generation());
        assertTrue(writer.dirty());
        assertEquals(70, RingTerrainAtlas.load(file, geometry, 17).cellHeight(0, 0));
        writer.request(atlas, false);
        assertTrue(tasks.isEmpty(), "reuse the completed buffer without another large allocation");
        prepare(writer);
        tasks.remove().run();
        writer.poll();
        assertFalse(writer.dirty());
        assertEquals(80, RingTerrainAtlas.load(file, geometry, 17).cellHeight(0, 0));
        writer.close();
    }

    @Test void repeatedEditsDuringCopyMatchAnEntireDirectCheckpoint(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("atlas.gz");
        var writer = new RingAtlasCheckpointWriter(file, true, tasks::add);
        writer.request(atlas, false);
        tasks.remove().run();
        for (int tick = 0; tick < 16; tick++) {
            writer.advance(atlas, 0);
            int column = tick * 3;
            atlas.putCell(column, 0, 70 + tick, 0x123400 + tick, tick & 15, 0x654300 + tick, tick & 15);
            writer.changedCell(column, 0, atlas.columns());
            atlas.advanceRevision();
            writer.markDirty();
        }
        while (tasks.isEmpty()) writer.advance(atlas, 0);
        tasks.remove().run();
        writer.poll();
        Path reference = directory.resolve("reference.gz");
        atlas.save(reference);
        assertArrayEquals(Files.readAllBytes(reference), Files.readAllBytes(file));
        writer.close();
    }

    @Test void failureRetainsDirtyStateAndRetryWorks(@TempDir Path directory) throws Exception {
        Path obstruction = directory.resolve("blocked");
        Files.writeString(obstruction, "file");
        var writer = new RingAtlasCheckpointWriter(obstruction.resolve("atlas.gz"), true, tasks::add);
        writer.request(atlas, false);
        prepare(writer);
        tasks.remove().run();
        assertThrows(CompletionException.class, writer::poll);
        assertFalse(writer.busy());
        assertTrue(writer.dirty());
        Files.delete(obstruction);
        writer.request(atlas, false);
        prepare(writer);
        tasks.remove().run();
        writer.poll();
        assertFalse(writer.dirty());
        writer.close();
    }

    @Test void verificationRejectsIncompleteSnapshot(@TempDir Path directory) {
        var writer = new RingAtlasCheckpointWriter(directory.resolve("atlas.gz"), true, tasks::add);
        var future = writer.request(atlas, true);
        prepare(writer);
        tasks.remove().run();
        assertTrue(future.isCompletedExceptionally());
        assertThrows(CompletionException.class, writer::poll);
        assertTrue(writer.dirty());
        writer.close();
    }

    @Test void finalDrainNeedsNoOwnerThreadCallbacks(@TempDir Path directory) throws Exception {
        var writer = new RingAtlasCheckpointWriter(directory.resolve("atlas.gz"), true);
        writer.request(atlas, false);
        writer.awaitActive(atlas);
        writer.poll();
        assertFalse(writer.dirty());
        writer.close();
        assertEquals(17, RingTerrainAtlas.load(directory.resolve("atlas.gz"), geometry, 17).worldHash());
    }

    @Test void verifiedCompletionUsesCapturedStateAndRequiresWorker(@TempDir Path directory) {
        for (int z = 0; z < atlas.rows(); z++) for (int x = 0; x < atlas.columns(); x++) {
            atlas.putCell(x, z, 70, 0x123456);
        }
        var writer = new RingAtlasCheckpointWriter(directory.resolve("atlas.gz"), true, tasks::add);
        var future = writer.request(atlas, true);
        assertFalse(future.isDone());
        assertThrows(IllegalStateException.class, writer::close);
        prepare(writer);
        atlas.putCell(0, 0, 90, 0x654321);
        writer.markDirty();
        tasks.remove().run();
        assertEquals(atlas.cellCount(), future.join().cells());
        assertTrue(future.join().verificationNanos() > 0);
        writer.poll();
        assertTrue(writer.dirty());
        writer.close();
    }
}
