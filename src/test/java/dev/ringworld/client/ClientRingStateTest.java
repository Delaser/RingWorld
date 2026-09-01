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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRingStateTest {
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
                RingTerrainAtlas.TILE_SIZE, 0, false, 0L));
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
}
