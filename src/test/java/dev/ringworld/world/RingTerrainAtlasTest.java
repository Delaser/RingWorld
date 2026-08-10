package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingTerrainAtlasTest {
    /** Pure atlas fixture only; no physical cylinder is constructed from this geometry. */
    private static final RingGeometry GEOMETRY = new RingGeometry(320, 1_024);
    private static final long HASH = 0x1234_5678_9ABC_DEF0L;

    @Test
    void samplesContinuouslyAcrossCircumferenceSeam() {
        RingTerrainAtlas atlas = new RingTerrainAtlas(GEOMETRY, HASH);
        int z = GEOMETRY.minWidthZ() + 4;
        atlas.putBlockSample(4, z, 80, 0x204060);
        atlas.putBlockSample(1_020, z, 80, 0x204060);

        RingTerrainAtlas.SurfaceSample before = atlas.sample(-0.01, z);
        RingTerrainAtlas.SurfaceSample after = atlas.sample(GEOMETRY.circumferenceBlocks() - 0.01, z);

        assertTrue(before.present());
        assertEquals(before.height(), after.height(), 1.0e-9);
        assertEquals(before.color(), after.color());
    }

    @Test
    void bilinearlyInterpolatesRealHeightAndColour() {
        RingTerrainAtlas atlas = new RingTerrainAtlas(GEOMETRY, HASH);
        int z0 = GEOMETRY.minWidthZ() + 4;
        atlas.putBlockSample(4, z0, 64, 0x000000);
        atlas.putBlockSample(12, z0, 96, 0x804020);
        atlas.putBlockSample(4, z0 + 8, 96, 0x408020);
        atlas.putBlockSample(12, z0 + 8, 128, 0xC0C040);

        RingTerrainAtlas.SurfaceSample center = atlas.sample(8, z0 + 4);

        assertEquals(96.0, center.height(), 1.0e-9);
        assertEquals(0x606020, center.color());
        assertEquals(1.0, center.coverage(), 1.0e-9);
    }

    @Test
    void tileAndDiskRoundTripsPreserveMissingCells(@TempDir Path directory) throws Exception {
        RingTerrainAtlas source = new RingTerrainAtlas(GEOMETRY, HASH);
        int z = GEOMETRY.minWidthZ() + 4;
        source.putBlockSample(4, z, 77, 0xABCDEF);

        RingTerrainAtlas tiled = new RingTerrainAtlas(GEOMETRY, HASH);
        assertTrue(tiled.applyTile(0, 0, source.encodeTile(0, 0)));
        assertFalse(tiled.applyTile(0, 0, source.encodeTile(0, 0)));
        assertEquals(1, tiled.presentCount());
        assertEquals(0xABCDEF, tiled.sample(4, z).color());
        assertFalse(tiled.isComplete());

        Path cache = directory.resolve("atlas.rwat.gz");
        tiled.save(cache);
        RingTerrainAtlas loaded = RingTerrainAtlas.load(cache, GEOMETRY, HASH);
        assertEquals(tiled.presentCount(), loaded.presentCount());
        assertEquals(77.0, loaded.sample(4, z).height(), 1.0e-9);
        assertFalse(loaded.hasCell(1, 0));
    }

    @Test
    void committedRevisionSurvivesDiskAndRejectsRollback(@TempDir Path directory) throws Exception {
        RingTerrainAtlas atlas = new RingTerrainAtlas(GEOMETRY, HASH);
        assertTrue(atlas.commitRevision(4L));
        assertFalse(atlas.commitRevision(4L));
        assertFalse(atlas.commitRevision(3L));

        Path cache = directory.resolve("revisioned.rwat.gz");
        atlas.save(cache);
        RingTerrainAtlas loaded = RingTerrainAtlas.load(cache, GEOMETRY, HASH);

        assertEquals(4L, loaded.revision());
        assertThrows(java.io.IOException.class, () -> loaded.commitRevision(-1L));
    }

    @Test
    void revisionedTileBatchConvergesIdenticallyOnMultipleClients() throws Exception {
        RingTerrainAtlas server = new RingTerrainAtlas(GEOMETRY, HASH);
        RingTerrainAtlas first = new RingTerrainAtlas(GEOMETRY, HASH);
        RingTerrainAtlas second = new RingTerrainAtlas(GEOMETRY, HASH);
        int z = GEOMETRY.minWidthZ() + 4;
        server.putBlockSample(4, z, 201, 0xF6D03D);
        server.advanceRevision();
        byte[] tile = server.encodeTile(0, 0);

        assertTrue(first.applyTile(0, 0, tile));
        assertTrue(second.applyTile(0, 0, tile));
        assertTrue(first.commitRevision(server.revision()));
        assertTrue(second.commitRevision(server.revision()));

        assertEquals(server.revision(), first.revision());
        assertEquals(first.revision(), second.revision());
        assertArrayEquals(first.encodeTile(0, 0), second.encodeTile(0, 0));
    }

    @Test
    void snapshotIsIndependentFromLaterLiveUpdates() {
        RingTerrainAtlas live = new RingTerrainAtlas(GEOMETRY, HASH);
        int z = GEOMETRY.minWidthZ() + 4;
        live.putBlockSample(4, z, 80, 0x123456);
        live.advanceRevision();

        RingTerrainAtlas snapshot = live.snapshot();
        live.putBlockSample(4, z, 96, 0xABCDEF);
        live.advanceRevision();

        assertEquals(80, snapshot.cellHeight(0, 0));
        assertEquals(0x123456, snapshot.cellColor(0, 0));
        assertEquals(1L, snapshot.revision());
        assertEquals(96, live.cellHeight(0, 0));
        assertEquals(2L, live.revision());
    }

    @Test
    void chunkCoverageAdvancesOnlyAfterEveryCellArrives() {
        RingTerrainAtlas atlas = new RingTerrainAtlas(GEOMETRY, HASH);
        int firstZ = GEOMETRY.minWidthZ();
        assertFalse(atlas.isChunkPresent(0, 0));
        for (int z = 4; z < 16; z += 8) {
            for (int x = 4; x < 16; x += 8) {
                atlas.putBlockSample(x, firstZ + z, 70, 0x556677);
            }
        }
        assertTrue(atlas.isChunkPresent(0, 0));
        assertEquals(1, atlas.firstMissingChunkIndex());
    }

    @Test
    void incompleteServerTileCannotEraseMoreCompleteClientCache() throws Exception {
        RingTerrainAtlas cached = new RingTerrainAtlas(GEOMETRY, HASH);
        int z = GEOMETRY.minWidthZ() + 4;
        cached.putBlockSample(4, z, 91, 0x123456);
        RingTerrainAtlas emptyServer = new RingTerrainAtlas(GEOMETRY, HASH);

        assertFalse(cached.applyTile(0, 0, emptyServer.encodeTile(0, 0)));

        assertEquals(1, cached.presentCount());
        assertEquals(91.0, cached.sample(4, z).height(), 1.0e-9);
        assertEquals(0x123456, cached.sample(4, z).color());
    }

    @Test
    void tileApplyReportsChangedPresentCells() throws Exception {
        RingTerrainAtlas source = new RingTerrainAtlas(GEOMETRY, HASH);
        RingTerrainAtlas client = new RingTerrainAtlas(GEOMETRY, HASH);
        int z = GEOMETRY.minWidthZ() + 4;
        source.putBlockSample(4, z, 80, 0x112233);

        assertTrue(client.applyTile(0, 0, source.encodeTile(0, 0)));
        assertFalse(client.applyTile(0, 0, source.encodeTile(0, 0)));

        source.putBlockSample(4, z, 81, 0x445566);
        assertTrue(client.applyTile(0, 0, source.encodeTile(0, 0)));
        assertEquals(81.0, client.sample(4, z).height());
        assertEquals(0x445566, client.sample(4, z).color());
    }

    @Test
    void worldHashChangesWithImmutableGeometryOrSeed() {
        RingWorldSettings first = new RingWorldSettings(320, 1_024, 123L, 160, 1);
        RingWorldSettings differentSeed = new RingWorldSettings(320, 1_024, 124L, 160, 1);
        RingWorldSettings differentLength = new RingWorldSettings(320, 1_040, 123L, 160, 1);
        RingWorldSettings differentWall = new RingWorldSettings(320, 1_024, 123L, 176, 1);
        RingWorldSettings legacyMapping = new RingWorldSettings(
                320, 1_024, 123L, 160, 64,
                RingTerrainNoiseMapping.LEGACY_AXIAL, RingWorldSettings.FORMAT_VERSION);
        RingWorldSettings annularMapping = new RingWorldSettings(
                320, 1_024, 123L, 160, 64,
                RingTerrainNoiseMapping.ANNULAR, RingWorldSettings.FORMAT_VERSION);
        RingWorldSettings completeMapping = new RingWorldSettings(
                320, 1_024, 123L, 160, 64,
                RingTerrainNoiseMapping.ANNULAR_COMPLETE, RingWorldSettings.FORMAT_VERSION);
        RingWorldSettings completeV2Mapping = new RingWorldSettings(
                320, 1_024, 123L, 160, 64,
                RingTerrainNoiseMapping.ANNULAR_COMPLETE_V2, RingWorldSettings.FORMAT_VERSION);

        assertFalse(RingTerrainAtlas.worldHash(first) == RingTerrainAtlas.worldHash(differentSeed));
        assertFalse(RingTerrainAtlas.worldHash(first) == RingTerrainAtlas.worldHash(differentLength));
        assertFalse(RingTerrainAtlas.worldHash(first) == RingTerrainAtlas.worldHash(differentWall));
        assertFalse(RingTerrainAtlas.worldHash(legacyMapping)
                == RingTerrainAtlas.worldHash(annularMapping));
        assertFalse(RingTerrainAtlas.worldHash(annularMapping)
                == RingTerrainAtlas.worldHash(completeMapping));
        assertFalse(RingTerrainAtlas.worldHash(completeMapping)
                == RingTerrainAtlas.worldHash(completeV2Mapping));
    }

    @Test
    void legacyMappingAtlasIsRejectedForTheSameAnnularWorldGeometry(@TempDir Path directory)
            throws Exception {
        RingWorldSettings legacy = new RingWorldSettings(
                GEOMETRY.widthBlocks(), GEOMETRY.circumferenceBlocks(), 123L, 160, 64,
                RingTerrainNoiseMapping.LEGACY_AXIAL, RingWorldSettings.FORMAT_VERSION);
        RingWorldSettings annular = new RingWorldSettings(
                GEOMETRY.widthBlocks(), GEOMETRY.circumferenceBlocks(), 123L, 160, 64,
                RingTerrainNoiseMapping.ANNULAR, RingWorldSettings.FORMAT_VERSION);
        Path current = directory.resolve("dimension/data/ringworld/atlas.rwat.gz");
        Path legacyPath = directory.resolve("data/atlas.rwat.gz");
        RingTerrainAtlas oldAtlas = new RingTerrainAtlas(
                GEOMETRY, RingTerrainAtlas.worldHash(legacy));
        oldAtlas.putCell(0, 0, 70, 0x123456);
        oldAtlas.save(current);

        RingTerrainAtlas.StorageLoad storage = RingTerrainAtlas.loadStorage(
                current, legacyPath, GEOMETRY, RingTerrainAtlas.worldHash(annular));

        assertEquals(RingTerrainAtlas.StorageStatus.INVALID_CURRENT, storage.status());
        assertEquals(0, storage.atlas().presentCount());
        assertEquals(RingTerrainAtlas.worldHash(annular), storage.atlas().worldHash());
    }

    @Test
    void shippedFormatTwoAtlasIsRejectedAfterLegacySettingsMigration(@TempDir Path directory)
            throws Exception {
        RingWorldSettings alpha = new RingWorldSettings(
                GEOMETRY.widthBlocks(), GEOMETRY.circumferenceBlocks(), 123L, 160, 64,
                RingTerrainNoiseMapping.LEGACY_AXIAL, 2);
        RingWorldSettings upgraded = RingWorldSettings.upgradeToCurrentFormat(alpha);
        // Frozen from the public alpha-3 fingerprint-v1/settings-format-2 algorithm.
        long shippedAlphaWorldHash = 0xAB21_6FD5_047B_650EL;
        Path current = directory.resolve("dimension/data/ringworld/atlas.rwat.gz");
        Path legacyPath = directory.resolve("data/atlas.rwat.gz");
        RingTerrainAtlas alphaAtlas = new RingTerrainAtlas(GEOMETRY, shippedAlphaWorldHash);
        alphaAtlas.putCell(0, 0, 70, 0x123456);
        alphaAtlas.save(current);

        assertEquals(RingTerrainNoiseMapping.LEGACY_AXIAL, upgraded.terrainNoiseMapping());
        assertFalse(shippedAlphaWorldHash == RingTerrainAtlas.worldHash(upgraded));

        RingTerrainAtlas.StorageLoad storage = RingTerrainAtlas.loadStorage(
                current, legacyPath, GEOMETRY, RingTerrainAtlas.worldHash(upgraded));

        assertEquals(RingTerrainAtlas.StorageStatus.INVALID_CURRENT, storage.status());
        assertEquals(0, storage.atlas().presentCount());
        assertEquals(RingTerrainAtlas.worldHash(upgraded), storage.atlas().worldHash());
    }

    @Test
    void allocationBudgetIsEnforcedInsideTheAtlasToo() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new RingTerrainAtlas(
                        new RingGeometry(1_048_576, 1_048_576), HASH));

        assertTrue(exception.getMessage().contains("terrain atlas requires"));
    }

    @Test
    void freshStorageStartsEmptyWithoutCreatingAFile(@TempDir Path directory) {
        Path current = directory.resolve("dimension/data/ringworld/atlas.rwat.gz");
        Path legacy = directory.resolve("data/atlas.rwat.gz");

        RingTerrainAtlas.StorageLoad storage =
                RingTerrainAtlas.loadStorage(current, legacy, GEOMETRY, HASH);

        assertEquals(RingTerrainAtlas.StorageStatus.FRESH, storage.status());
        assertEquals(0, storage.atlas().presentCount());
        assertFalse(Files.exists(current));
    }

    @Test
    void validLegacyAtlasMigratesOnceToDimensionStorage(@TempDir Path directory)
            throws Exception {
        Path current = directory.resolve("dimension/data/ringworld/atlas.rwat.gz");
        Path legacy = directory.resolve("data/atlas.rwat.gz");
        RingTerrainAtlas source = new RingTerrainAtlas(GEOMETRY, HASH);
        int z = GEOMETRY.minWidthZ() + 4;
        source.putBlockSample(4, z, 88, 0x778899);
        source.save(legacy);

        RingTerrainAtlas.StorageLoad migrated =
                RingTerrainAtlas.loadStorage(current, legacy, GEOMETRY, HASH);
        RingTerrainAtlas.StorageLoad reloaded =
                RingTerrainAtlas.loadStorage(current, legacy, GEOMETRY, HASH);

        assertEquals(RingTerrainAtlas.StorageStatus.MIGRATED_LEGACY, migrated.status());
        assertEquals(88.0, migrated.atlas().sample(4, z).height(), 1.0e-9);
        assertTrue(Files.isRegularFile(current));
        assertEquals(RingTerrainAtlas.StorageStatus.CURRENT, reloaded.status());
    }

    @Test
    void corruptLegacyAtlasIsRejectedWithoutCreatingCurrentState(@TempDir Path directory)
            throws Exception {
        Path current = directory.resolve("dimension/data/ringworld/atlas.rwat.gz");
        Path legacy = directory.resolve("data/atlas.rwat.gz");
        Files.createDirectories(legacy.getParent());
        Files.write(legacy, new byte[] {1, 2, 3});

        RingTerrainAtlas.StorageLoad storage =
                RingTerrainAtlas.loadStorage(current, legacy, GEOMETRY, HASH);

        assertEquals(RingTerrainAtlas.StorageStatus.INVALID_LEGACY, storage.status());
        assertEquals(0, storage.atlas().presentCount());
        assertFalse(Files.exists(current));
    }

    @Test
    void interruptedWriteDoesNotBlockValidatedLegacyMigration(@TempDir Path directory)
            throws Exception {
        Path current = directory.resolve("dimension/data/ringworld/atlas.rwat.gz");
        Path temporary = current.resolveSibling(current.getFileName() + ".tmp");
        Path legacy = directory.resolve("data/atlas.rwat.gz");
        RingTerrainAtlas source = new RingTerrainAtlas(GEOMETRY, HASH);
        source.putCell(0, 0, 70, 0x123456);
        source.save(legacy);
        Files.createDirectories(temporary.getParent());
        Files.write(temporary, new byte[] {9, 9, 9});

        RingTerrainAtlas.StorageLoad storage =
                RingTerrainAtlas.loadStorage(current, legacy, GEOMETRY, HASH);

        assertEquals(RingTerrainAtlas.StorageStatus.MIGRATED_LEGACY, storage.status());
        assertTrue(Files.isRegularFile(current));
        assertFalse(Files.exists(temporary));
    }

    @Test
    void invalidCurrentAtlasNeverFallsBackToLegacy(@TempDir Path directory)
            throws Exception {
        Path current = directory.resolve("dimension/data/ringworld/atlas.rwat.gz");
        Path legacy = directory.resolve("data/atlas.rwat.gz");
        RingTerrainAtlas legacyAtlas = new RingTerrainAtlas(GEOMETRY, HASH);
        legacyAtlas.putCell(0, 0, 70, 0x123456);
        legacyAtlas.save(legacy);
        Files.createDirectories(current.getParent());
        Files.write(current, new byte[] {4, 5, 6});

        RingTerrainAtlas.StorageLoad storage =
                RingTerrainAtlas.loadStorage(current, legacy, GEOMETRY, HASH);

        assertEquals(RingTerrainAtlas.StorageStatus.INVALID_CURRENT, storage.status());
        assertEquals(0, storage.atlas().presentCount());
    }

    @Test
    void legacyAtlasWithDifferentWorldHashIsNeverMigrated(@TempDir Path directory)
            throws Exception {
        Path current = directory.resolve("dimension/data/ringworld/atlas.rwat.gz");
        Path legacy = directory.resolve("data/atlas.rwat.gz");
        new RingTerrainAtlas(GEOMETRY, HASH + 1).save(legacy);

        RingTerrainAtlas.StorageLoad storage =
                RingTerrainAtlas.loadStorage(current, legacy, GEOMETRY, HASH);

        assertEquals(RingTerrainAtlas.StorageStatus.INVALID_LEGACY, storage.status());
        assertEquals(HASH, storage.atlas().worldHash());
        assertFalse(Files.exists(current));
    }

    @Test
    void oldFormatLegacyAtlasIsInvalidatedInsteadOfMigrated(@TempDir Path directory)
            throws Exception {
        Path current = directory.resolve("dimension/data/ringworld/atlas.rwat.gz");
        Path legacy = directory.resolve("data/atlas.rwat.gz");
        Files.createDirectories(legacy.getParent());
        try (DataOutputStream output = new DataOutputStream(new GZIPOutputStream(
                new BufferedOutputStream(Files.newOutputStream(legacy))))) {
            output.writeInt(0x52574154);
            output.writeInt(RingTerrainAtlas.FORMAT_VERSION - 1);
        }

        RingTerrainAtlas.StorageLoad storage =
                RingTerrainAtlas.loadStorage(current, legacy, GEOMETRY, HASH);

        assertEquals(RingTerrainAtlas.StorageStatus.INVALID_LEGACY, storage.status());
        assertEquals(0, storage.atlas().presentCount());
        assertFalse(Files.exists(current));
    }
}
