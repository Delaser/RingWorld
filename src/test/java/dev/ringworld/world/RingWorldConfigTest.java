package dev.ringworld.world;

import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RingWorldConfigTest {
    @Test
    void missingOptionalVisualKeysUseTheDocumentedNewWorldDefaults() {
        Properties properties = new Properties();

        assertEquals(RingWallStyle.Preset.WEATHERED_FORTIFICATION.style(),
                RingWorldConfig.wallStyle(properties));
        assertEquals(RingSkyProfile.DEFAULT, RingWorldConfig.skyProfile(properties));
    }

    @Test
    void everyWallPresetCanBeSelectedByItsStableConfigurationName() {
        for (RingWallStyle.Preset preset : RingWallStyle.Preset.values()) {
            Properties properties = new Properties();
            properties.setProperty("wallPreset", preset.name());
            assertEquals(preset.style(), RingWorldConfig.wallStyle(properties));
        }
    }

    @Test
    void explicitWallFieldsOverrideThePresetAndRoundTripCustomValues() {
        Properties properties = new Properties();
        properties.setProperty("wallPreset", RingWallStyle.Preset.CLEAN_MONOLITH.name());
        properties.setProperty("wallThicknessBlocks", "32");
        properties.setProperty("wallPalette", Integer.toString(RingWallStyle.Palette.INDUSTRIAL.id()));
        properties.setProperty("wallPattern", Integer.toString(RingWallStyle.Pattern.HYBRID.id()));
        properties.setProperty("wallDecayPercent", "70");
        properties.setProperty("wallStyleFormat", Integer.toString(RingWallStyle.FORMAT_VERSION));

        assertEquals(RingWallStyle.custom(32, RingWallStyle.Palette.INDUSTRIAL,
                RingWallStyle.Pattern.HYBRID, 70), RingWorldConfig.wallStyle(properties));
    }

    @Test
    void independentSkyKeysAndLegacyCombinedPresetsMapToStableProfiles() {
        Properties independent = new Properties();
        independent.setProperty("skyBackdrop", "void");
        independent.setProperty("sunStyle", "large");
        assertEquals(new RingSkyProfile(RingSkyProfile.Backdrop.VOID,
                        RingSkyProfile.LightSource.LARGE, RingSkyProfile.FORMAT_VERSION),
                RingWorldConfig.skyProfile(independent));

        assertLegacySky("MINECRAFT_ATMOSPHERE",
                RingSkyProfile.Backdrop.ATMOSPHERE, RingSkyProfile.LightSource.SMALL);
        assertLegacySky("SPACE_HABITAT",
                RingSkyProfile.Backdrop.NIGHT, RingSkyProfile.LightSource.SMALL);
        assertLegacySky("DISTANT_STAR",
                RingSkyProfile.Backdrop.NIGHT, RingSkyProfile.LightSource.LARGE);
        assertLegacySky("NIGHT_HABITAT",
                RingSkyProfile.Backdrop.NIGHT, RingSkyProfile.LightSource.NONE);
        assertLegacySky("MINIMAL_VOID",
                RingSkyProfile.Backdrop.VOID, RingSkyProfile.LightSource.NONE);
    }

    @Test
    void invalidVisualConfigurationFailsClosed() {
        Properties wall = new Properties();
        wall.setProperty("wallPreset", "UNKNOWN");
        assertThrows(IllegalArgumentException.class, () -> RingWorldConfig.wallStyle(wall));

        Properties sky = new Properties();
        sky.setProperty("skyBackdrop", "UNKNOWN");
        assertThrows(IllegalArgumentException.class, () -> RingWorldConfig.skyProfile(sky));
    }

    private static void assertLegacySky(String name, RingSkyProfile.Backdrop backdrop,
                                        RingSkyProfile.LightSource source) {
        Properties properties = new Properties();
        properties.setProperty("skyPreset", name);
        assertEquals(new RingSkyProfile(backdrop, source, RingSkyProfile.FORMAT_VERSION),
                RingWorldConfig.skyProfile(properties));
    }
}
