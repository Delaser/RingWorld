package dev.ringworld.world;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RingAtlasContentIdentityTest {
    @Test void everyRenderedFieldAndTheCellPositionAffectIdentityWithoutDependingOnEditOrder() throws Exception {
        var atlas = new RingTerrainAtlas(new RingGeometry(128, 2048), 1);
        atlas.putCell(0, 0, -64, 0x123456, 4, 0xABCDEF, 7);
        long original = atlas.contentFingerprint();
        // Independently computed unsigned 64-bit Python vector, including signed height and packed water/light.
        assertEquals(0xE5C305F9F3BA1215L, original);
        assertNotEquals(0, original);
        assertFalse(atlas.putCell(0, 0, -64, 0x123456, 4, 0xABCDEF, 7));
        assertEquals(original, atlas.contentFingerprint());
        int[][] edits = {{-63,0x123456,4,0xABCDEF,7},{-64,0x123457,4,0xABCDEF,7},
                {-64,0x123456,5,0xABCDEF,7},{-64,0x123456,4,0xABCDEE,7},{-64,0x123456,4,0xABCDEF,8}};
        for (int[] edit : edits) {
            atlas.putCell(0, 0, edit[0], edit[1], edit[2], edit[3], edit[4]);
            assertNotEquals(original, atlas.contentFingerprint());
            atlas.putCell(0, 0, -64, 0x123456, 4, 0xABCDEF, 7);
            assertEquals(original, atlas.contentFingerprint());
        }
        var other = new RingTerrainAtlas(atlas.geometry(), 1);
        other.putCell(1, 0, -64, 0x123456, 4, 0xABCDEF, 7);
        assertNotEquals(original, other.contentFingerprint());
        atlas.putCell(1, 0, 320, 0x654321, 0, 0x112233, 15);
        other.clear();
        other.putCell(1, 0, 320, 0x654321, 0, 0x112233, 15);
        other.putCell(0, 0, -64, 0x123456, 4, 0xABCDEF, 7);
        other.commitRevision(999);
        assertEquals(atlas.contentFingerprint(), other.contentFingerprint());
        assertFalse(RingAtlasCacheIdentity.matches(42, original, 42, original ^ 1));
        assertFalse(RingAtlasCacheIdentity.matches(42, original, 43, original));
        assertFalse(RingAtlasCacheIdentity.matches(-1, original, -1, original));
        assertTrue(RingAtlasCacheIdentity.matches(42, original, 42, original));
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 4, 8, 16})
    void diskWireSnapshotsAndRepairedReusableCheckpointsKeepExactContentIdentity(int step, @TempDir Path directory)
            throws Exception {
        var source = new RingTerrainAtlas(new RingGeometry(144, 1056), 0x1234, step);
        source.putCell(0, 0, -64, 0xABCDEF, 15, 0x123456, 7);
        source.putCell(source.columns() - 1, source.rows() - 1, 320, 0x345678, 0, 0x765432, 15);
        source.commitRevision(42);
        var file = directory.resolve("atlas.gz");
        source.save(file);
        assertEquals(source.contentFingerprint(), RingTerrainAtlas.load(file, source.geometry(), source.worldHash()).contentFingerprint());
        assertEquals(source.contentFingerprint(), source.snapshot().contentFingerprint());
        var wire = new RingTerrainAtlas(source.geometry(), source.worldHash(), step);
        for (int z = 0; z < source.tileRows(); z++) for (int x = 0; x < source.tileColumns(); x++)
            wire.applyTile(x, z, source.encodeTile(x, z));
        assertEquals(source.contentFingerprint(), wire.contentFingerprint());
        var checkpoint = source.snapshot();
        checkpoint.putCell(0, 0, 99, 0, 0, 0, 0);
        assertThrows(IllegalStateException.class, () -> checkpoint.finishCheckpointCopy(source));
        checkpoint.putCell(1, 1, 50, 0xFF0000, 0, 0xFF0000, 0);
        int half = source.cellCount() / 2;
        source.copyCheckpointCells(checkpoint, 0, half);
        source.putCell(0, 0, 77, 0xABCDEF, 15, 0x123456, 7);
        source.copyCheckpointCells(checkpoint, 0, 1);
        source.copyCheckpointCells(checkpoint, half, source.cellCount() - half);
        checkpoint.finishCheckpointCopy(source);
        assertEquals(source.contentFingerprint(), checkpoint.contentFingerprint());
        source.clear();
        source.copyCheckpointCells(checkpoint, 0, source.cellCount());
        checkpoint.finishCheckpointCopy(source);
        assertEquals(0, checkpoint.contentFingerprint());
        assertEquals(0, checkpoint.presentCount());
    }
}
