package dev.ringworld.server;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTerrainAtlas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Persistence seams used by the world-owned service without a Minecraft server fixture. */
class RingAtlasPregenerationServiceStorageTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(320, 1_024);
    private static final long HASH = 0x1234L;

    @Test
    void interruptedPartialCheckpointResumesAtSameMissingChunkWithoutRewritingBytes(@TempDir Path directory)
            throws Exception {
        Path path = directory.resolve("terrain-atlas.rwat.gz");
        RingTerrainAtlas atlas = new RingTerrainAtlas(GEOMETRY, HASH);
        completeChunk(atlas, 0, 0);
        atlas.save(path);
        byte[] checkpoint = Files.readAllBytes(path);

        RingTerrainAtlas resumed = RingTerrainAtlas.load(path, GEOMETRY, HASH);
        RingAtlasPregenerationSelection selection = new RingAtlasPregenerationSelection(GEOMETRY, resumed);

        assertEquals(1, selection.select().orElseThrow().index());
        assertArrayEquals(checkpoint, Files.readAllBytes(path));
    }

    @Test
    void completeReloadIsIdempotentAndPreservesVerifiedFormatFiveBytes(@TempDir Path directory)
            throws Exception {
        Path path = directory.resolve("terrain-atlas.rwat.gz");
        RingTerrainAtlas atlas = completeAtlas();
        atlas.save(path);
        byte[] expected = Files.readAllBytes(path);

        RingTerrainAtlas reloaded = RingTerrainAtlas.load(path, GEOMETRY, HASH);

        assertTrue(reloaded.isComplete());
        assertEquals(5, RingTerrainAtlas.FORMAT_VERSION);
        assertArrayEquals(expected, Files.readAllBytes(path));
    }

    @Test
    void corruptCheckpointIsRejectedBeforeAnyResumeSelection(@TempDir Path directory) throws Exception {
        Path current = directory.resolve("terrain-atlas.rwat.gz");
        Files.write(current, new byte[] {1, 2, 3});

        RingTerrainAtlas.StorageLoad storage = RingTerrainAtlas.loadStorage(current,
                directory.resolve("legacy.rwat.gz"), GEOMETRY, HASH);

        assertEquals(RingTerrainAtlas.StorageStatus.INVALID_CURRENT, storage.status());
        assertFalse(storage.atlas().isComplete());
    }

    private static RingTerrainAtlas completeAtlas() {
        RingTerrainAtlas atlas = new RingTerrainAtlas(GEOMETRY, HASH);
        for (int z = 0; z < atlas.rows(); z++) for (int x = 0; x < atlas.columns(); x++) {
            atlas.putCell(x, z, 70, 0x445566);
        }
        return atlas;
    }

    private static void completeChunk(RingTerrainAtlas atlas, int chunkX, int chunkRow) {
        int firstX = chunkX * 16;
        int firstZ = atlas.geometry().minWidthZ() + chunkRow * 16;
        for (int z = 4; z < 16; z += RingTerrainAtlas.SAMPLE_STEP_BLOCKS) {
            for (int x = 4; x < 16; x += RingTerrainAtlas.SAMPLE_STEP_BLOCKS) {
                atlas.putBlockSample(firstX + x, firstZ + z, 70, 0x445566);
            }
        }
    }
}
