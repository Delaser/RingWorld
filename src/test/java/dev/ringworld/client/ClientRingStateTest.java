package dev.ringworld.client;

import dev.ringworld.net.RingTerrainAtlasMetadataPayload;
import dev.ringworld.net.RingTerrainPreviewPayload;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSkyProfile;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingTerrainPreview;
import dev.ringworld.world.RingTerrainPreviewStage;
import dev.ringworld.world.RingWallStyle;
import dev.ringworld.world.RingWorldSettings;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRingStateTest {
    @Test
    void coarseFormatTenCacheCannotReplaceTheOneBlockMaster(@TempDir Path cacheDirectory)
            throws Exception {
        RingGeometry geometry = new RingGeometry(128, 2_048);
        long worldHash = 0x434F_4152_5345L;
        ClientRingState.configureCacheDirectory(cacheDirectory);
        setSession(geometry);
        RingTerrainAtlas coarse = new RingTerrainAtlas(geometry, worldHash, 8);
        coarse.putCell(0, 0, 90, 0x123456, 7, 0x123456, 15);
        coarse.save(cacheDirectory.resolve("terrain-" + Long.toUnsignedString(worldHash, 16) + ".rwat.gz"));
        ClientRingState.installTerrainAtlas(new RingTerrainAtlasMetadataPayload(worldHash, 1,
                2048, 128, RingTerrainAtlas.TILE_SIZE, 0, false, 0, 0));
        assertEquals(1, ClientRingState.terrainAtlas().sampleStep());
        assertEquals(0, ClientRingState.terrainAtlas().presentCount());
    }

    @AfterEach
    void clearSessionState() {
        ClientRingState.clear();
    }

    @Test
    void clearRestoresAppearanceAndPreviewDefaultsBeforeAnotherWorldCanLoad(
            @TempDir Path cacheDirectory) throws Exception {
        RingGeometry geometry = new RingGeometry(128, 2_048);
        RingWallStyle wallStyle = RingWallStyle.Preset.INDUSTRIAL_SUPERSTRUCTURE.style();
        RingSkyProfile skyProfile = new RingSkyProfile(
                RingSkyProfile.Backdrop.NIGHT, RingSkyProfile.LightSource.NONE,
                RingSkyProfile.FORMAT_VERSION);
        long worldHash = 0x12345678L;
        ClientRingState.configureCacheDirectory(cacheDirectory);
        ClientRingState.set(geometry, 160, (int) RingGeometry.SURFACE_Y,
                RingTerrainNoiseMapping.CURRENT, wallStyle, skyProfile,
                0x5EEDL, RingWorldSettings.FORMAT_VERSION, 0xABCDL);
        ClientRingState.installTerrainAtlas(new RingTerrainAtlasMetadataPayload(
                worldHash, RingTerrainAtlas.SAMPLE_STEP_BLOCKS,
                geometry.circumferenceBlocks() / RingTerrainAtlas.SAMPLE_STEP_BLOCKS,
                geometry.widthBlocks() / RingTerrainAtlas.SAMPLE_STEP_BLOCKS,
                RingTerrainAtlas.TILE_SIZE, 0, false, 0L, 0L));
        RingTerrainPreview preview = new RingTerrainPreview(
                worldHash, 2, 1, new int[] {0x102030, 0x405060},
                new short[] {64, 72});
        ClientRingState.installTerrainPreview(new RingTerrainPreviewPayload(
                worldHash, RingTerrainPreviewStage.HIGH.wireValue(), preview.encode()));
        RingSkyProfile liveSky = new RingSkyProfile(
                RingSkyProfile.Backdrop.VOID, RingSkyProfile.LightSource.LARGE,
                RingSkyProfile.FORMAT_VERSION);
        ClientRingState.setSkyProfile(liveSky);

        assertEquals(wallStyle, ClientRingState.wallStyle());
        assertEquals(liveSky, ClientRingState.skyProfile());
        assertEquals(0x5EEDL, ClientRingState.generatorSeed());
        assertEquals(RingWorldSettings.FORMAT_VERSION,
                ClientRingState.settingsFormatVersion());
        assertNotNull(ClientRingState.terrainPreview());
        assertEquals(RingTerrainPreviewStage.HIGH.wireValue(),
                ClientRingState.terrainPreviewStage());

        ClientRingState.clear();

        assertTrue(ClientRingState.sessionCleared());
        assertEquals(RingWallStyle.LEGACY, ClientRingState.wallStyle());
        assertEquals(RingSkyProfile.DEFAULT, ClientRingState.skyProfile());
        assertEquals(0L, ClientRingState.generatorSeed());
        assertEquals(0, ClientRingState.settingsFormatVersion());
        assertNull(ClientRingState.terrainPreview());
        assertEquals(-1, ClientRingState.terrainPreviewStage());
    }

    @Test
    void partialAtlasCachePreservesBlockLightAcrossReconnect(@TempDir Path cacheDirectory)
            throws Exception {
        RingGeometry geometry = new RingGeometry(128, 2_048);
        long worldHash = 0x4C49_4748_54L;
        ClientRingState.configureCacheDirectory(cacheDirectory);
        setSession(geometry);

        RingTerrainAtlas server = new RingTerrainAtlas(geometry, worldHash);
        server.putCell(0, 0, 78, 0x445566, 13, 0x445566, 15);
        server.advanceRevision();
        RingTerrainAtlasMetadataPayload metadata = new RingTerrainAtlasMetadataPayload(
                worldHash, server.sampleStep(), server.columns(), server.rows(),
                RingTerrainAtlas.TILE_SIZE, server.presentCount(), false, server.revision(), server.contentFingerprint());

        ClientRingState.installTerrainAtlas(metadata);
        ClientRingState.applyTerrainAtlasTile(worldHash, 0, 0, server.encodeTile(0, 0));
        ClientRingState.commitTerrainAtlasRevision(worldHash, server.revision());
        assertEquals(13, ClientRingState.terrainAtlas().cellBlockLight(0, 0));
        assertEquals(15, ClientRingState.terrainAtlas().cellWaterCoverage(0, 0));

        ClientRingState.saveTerrainAtlasIfDue(true);
        var save = ClientRingState.class.getDeclaredField("terrainAtlasSave");
        save.setAccessible(true);
        ((java.util.concurrent.CompletableFuture<?>) save.get(null))
                .get(10, java.util.concurrent.TimeUnit.SECONDS);
        ClientRingState.clear();
        setSession(geometry);
        ClientRingState.installTerrainAtlas(metadata);

        assertEquals(server.revision(), ClientRingState.terrainAtlasDurableRevision());
        assertEquals(13, ClientRingState.terrainAtlas().cellBlockLight(0, 0));
        assertEquals(15, ClientRingState.terrainAtlas().cellWaterCoverage(0, 0));
    }

    @Test
    void lateOlderPreviewCannotReplaceNewerStage(@TempDir Path cacheDirectory)
            throws Exception {
        RingGeometry geometry = new RingGeometry(128, 2_048);
        long worldHash = 0x5052_4556L;
        ClientRingState.configureCacheDirectory(cacheDirectory);
        setSession(geometry);
        ClientRingState.installTerrainAtlas(new RingTerrainAtlasMetadataPayload(
                worldHash, RingTerrainAtlas.SAMPLE_STEP_BLOCKS,
                geometry.circumferenceBlocks() / RingTerrainAtlas.SAMPLE_STEP_BLOCKS,
                geometry.widthBlocks() / RingTerrainAtlas.SAMPLE_STEP_BLOCKS,
                RingTerrainAtlas.TILE_SIZE, 0, false, 0L, 0L));
        RingTerrainPreview high = new RingTerrainPreview(
                worldHash, 1, 1, new int[] {0xABCDEF}, new short[] {80});
        RingTerrainPreview current = new RingTerrainPreview(
                worldHash, 1, 1, new int[] {0x123456}, new short[] {60});

        ClientRingState.installTerrainPreview(new RingTerrainPreviewPayload(
                worldHash, RingTerrainPreviewStage.HIGH.wireValue(), high.encode()));
        ClientRingState.installTerrainPreview(new RingTerrainPreviewPayload(
                worldHash, RingTerrainPreviewStage.CURRENT.wireValue(), current.encode()));

        assertEquals(RingTerrainPreviewStage.HIGH.wireValue(),
                ClientRingState.terrainPreviewStage());
        assertEquals(0xABCDEF, ClientRingState.terrainPreview().color(0, 0));
    }

    @Test
    void sameLayoutAndRevisionCannotCertifyDifferentEditedContent(@TempDir Path cache) throws Exception {
        var settings = new RingWorldSettings(128, 2048, 259L, 160, 5);
        var geometry = settings.geometry();
        long hash = RingTerrainAtlas.worldHash(settings);
        var first = new RingTerrainAtlas(geometry, hash);
        var second = new RingTerrainAtlas(geometry, hash);
        for (int row = 0; row < first.rows(); row++) for (int column = 0; column < first.columns(); column++) {
            first.putCell(column, row, 64, 0x123456, 0, 0x123456, 0);
            second.putCell(column, row, 64, 0x123456, 0, 0x123456, 0);
        }
        first.putCell(0, 0, 80, 0x123456, 0, 0x123456, 0);
        second.putCell(0, 0, 90, 0x123456, 0, 0x123456, 0);
        first.commitRevision(42); second.commitRevision(42);
        assertNotEquals(first.contentFingerprint(), second.contentFingerprint());
        ClientRingState.configureCacheDirectory(cache);
        setSession(geometry);
        first.save(cache.resolve("terrain-" + Long.toUnsignedString(hash, 16) + ".rwat.gz"));
        assertTrue(ClientRingState.installTerrainAtlas(new RingTerrainAtlasMetadataPayload(
                hash, 1, first.columns(), first.rows(), RingTerrainAtlas.TILE_SIZE,
                first.presentCount(), true, 42, first.contentFingerprint())));
        setSession(geometry);
        assertFalse(ClientRingState.installTerrainAtlas(new RingTerrainAtlasMetadataPayload(
                hash, 1, second.columns(), second.rows(), RingTerrainAtlas.TILE_SIZE,
                second.presentCount(), true, 42, second.contentFingerprint())));
        assertEquals(0, ClientRingState.terrainAtlas().presentCount(), "foreign content must not be rendered provisionally");
    }

    private static void setSession(RingGeometry geometry) {
        ClientRingState.set(geometry, 160, (int)RingGeometry.SURFACE_Y,
                RingTerrainNoiseMapping.CURRENT, RingWallStyle.LEGACY,
                RingSkyProfile.DEFAULT, 0x5EEDL, RingWorldSettings.FORMAT_VERSION,
                0xABCDL);
    }
}
