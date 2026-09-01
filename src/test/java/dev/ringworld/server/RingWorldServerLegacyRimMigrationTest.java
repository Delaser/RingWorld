package dev.ringworld.server;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingWallStyle;
import dev.ringworld.world.RingWorldSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RingWorldServerLegacyRimMigrationTest {
    @Test
    void formatFourStylesNeverEnableAutomaticBlockMigration() {
        RingWallStyle reconstructedLegacy = RingWallStyle.custom(
                5, RingWallStyle.Palette.WEATHERED, RingWallStyle.Pattern.CLUSTERED, 0);
        assertEquals(RingWallStyle.LEGACY, reconstructedLegacy);

        RingWallStyle thickCustom = RingWallStyle.custom(
                32, RingWallStyle.Palette.MONOLITH, RingWallStyle.Pattern.PANELS, 0);

        assertFalse(RingWorldServer.automaticLegacyRimMigrationEnabled(
                settings(RingWallStyle.LEGACY)));
        assertFalse(RingWorldServer.automaticLegacyRimMigrationEnabled(
                settings(reconstructedLegacy)));
        assertFalse(RingWorldServer.automaticLegacyRimMigrationEnabled(settings(thickCustom)));
    }

    private static RingWorldSettings settings(RingWallStyle style) {
        return new RingWorldSettings(
                256, 2_048, 42L, 160, (int) RingGeometry.SURFACE_Y,
                RingTerrainNoiseMapping.CURRENT, style, RingWorldSettings.FORMAT_VERSION);
    }
}
