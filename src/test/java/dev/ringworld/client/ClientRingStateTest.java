package dev.ringworld.client;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSkyProfile;
import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingWallStyle;
import dev.ringworld.world.RingWorldSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRingStateTest {
    @AfterEach
    void clearSessionState() {
        ClientRingState.clear();
    }

    @Test
    void clearRestoresServerOwnedAppearanceDefaultsBeforeAnotherWorldCanLoad() {
        RingWallStyle wallStyle = RingWallStyle.Preset.INDUSTRIAL_SUPERSTRUCTURE.style();
        RingSkyProfile skyProfile = new RingSkyProfile(
                RingSkyProfile.Backdrop.NIGHT, RingSkyProfile.LightSource.NONE,
                RingSkyProfile.FORMAT_VERSION);
        ClientRingState.set(new RingGeometry(128, 2_048), 160, 64,
                RingTerrainNoiseMapping.CURRENT, wallStyle, skyProfile,
                0x5EEDL, RingWorldSettings.FORMAT_VERSION, 0x1234L);

        assertEquals(wallStyle, ClientRingState.wallStyle());
        assertEquals(skyProfile, ClientRingState.skyProfile());

        ClientRingState.clear();

        assertTrue(ClientRingState.sessionCleared());
        assertEquals(RingWallStyle.LEGACY, ClientRingState.wallStyle());
        assertEquals(RingSkyProfile.DEFAULT, ClientRingState.skyProfile());
    }
}
