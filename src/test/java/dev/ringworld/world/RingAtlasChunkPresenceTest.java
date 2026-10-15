package dev.ringworld.world;

import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Independent cell oracle for the derived progress index, including checkpoint removal. */
class RingAtlasChunkPresenceTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(144, 1056);

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void editsWireSnapshotAndDiskRetainExactChunkCounts(int step, @TempDir Path directory)
            throws Exception {
        var atlas = new RingTerrainAtlas(GEOMETRY, 19L, step);
        var random = new Random(4511);
        for (int i = 0; i < 15000; i++) {
            atlas.putCell(random.nextInt(atlas.columns()), random.nextInt(atlas.rows()),
                    64, random.nextInt(0x1000000));
        }
        fillChunk(atlas, 0, 0);
        fillChunk(atlas, GEOMETRY.circumferenceChunks() - 1, GEOMETRY.widthChunks() - 1);
        assertCounts(atlas);
        assertTrue(atlas.isChunkPresent(-1, GEOMETRY.widthChunks() - 1));
        assertTrue(atlas.isChunkPresent(GEOMETRY.circumferenceChunks(), 0));
        assertFalse(atlas.isChunkPresent(0, -1));
        assertFalse(atlas.isChunkPresent(0, GEOMETRY.widthChunks()));

        var copy = atlas.snapshot();
        assertCounts(copy);
        assertEquals(atlas.presentChunkCount(), copy.presentChunkCount());
        var wire = new RingTerrainAtlas(GEOMETRY, 19L, step);
        for (int z = 0; z < atlas.tileRows(); z++) for (int x = 0; x < atlas.tileColumns(); x++) {
            wire.applyTile(x, z, atlas.encodeTile(x, z));
            wire.applyTile(x, z, atlas.encodeTile(x, z)); // repeated tiles never double count
        }
        assertCounts(wire);
        assertEquals(atlas.presentChunkCount(), wire.presentChunkCount());
        var empty = new RingTerrainAtlas(GEOMETRY, 19L, step);
        wire.applyTile(0, 0, empty.encodeTile(0, 0)); // absent wire cells preserve cached samples
        assertCounts(wire);
        Path path = directory.resolve("atlas.gz");
        wire.save(path);
        var loaded = RingTerrainAtlas.load(path, GEOMETRY, 19L);
        assertCounts(loaded);
        assertEquals(wire.presentChunkCount(), loaded.presentChunkCount());
        atlas.clear();
        assertEquals(0, atlas.presentChunkCount());
        assertCounts(copy); // no shared derived storage
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void repairingReusableCheckpointBuffersCanAddAndRemoveCompleteChunks(int step) {
        var source = new RingTerrainAtlas(GEOMETRY, 19L, step);
        for (int row = 0; row < source.rows(); row++) for (int x = 0; x < source.columns(); x++) {
            source.putCell(x, row, 64, 0x123456);
        }
        var destination = source.snapshot();
        assertEquals((long)GEOMETRY.widthChunks() * GEOMETRY.circumferenceChunks(),
                destination.presentChunkCount());
        source.clear();
        fillChunk(source, 1, 1);
        source.putCell(0, 0, 100, 0xabcdef);
        for (int first = 0; first < source.cellCount(); first += 7919) {
            source.copyCheckpointCells(destination, first, Math.min(7919, source.cellCount() - first));
            assertCounts(destination);
        }
        destination.finishCheckpointCopy(source);
        assertEquals(source.presentChunkCount(), destination.presentChunkCount());
        assertEquals(source.presentCount(), destination.presentCount());
        source.copyCheckpointCells(destination, 0, source.cellCount());
        assertCounts(destination); // identical repairs do not change counters
    }

    private static void fillChunk(RingTerrainAtlas atlas, int chunkX, int chunkRow) {
        int size = 16 / atlas.sampleStep();
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            atlas.putCell(chunkX * size + x, chunkRow * size + z, 80, 0x789abc);
        }
    }

    private static void assertCounts(RingTerrainAtlas atlas) {
        long complete = 0;
        int size = 16 / atlas.sampleStep();
        for (int chunkX = 0; chunkX < GEOMETRY.circumferenceChunks(); chunkX++) {
            for (int chunkRow = 0; chunkRow < GEOMETRY.widthChunks(); chunkRow++) {
                boolean expected = true;
                for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
                    expected &= atlas.hasCell(chunkX * size + x, chunkRow * size + z);
                }
                assertEquals(expected, atlas.isChunkPresent(chunkX, chunkRow));
                if (expected) complete++;
            }
        }
        assertEquals(complete, atlas.presentChunkCount());
    }
}
