package dev.ringworld.world;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingSkyProfileTest {
    @Test
    void everyCoherentPresetRoundTrips() {
        for (RingSkyProfile.Preset preset : RingSkyProfile.Preset.values()) {
            var encoded = RingSkyProfile.CODEC.encodeStart(JsonOps.INSTANCE, preset.profile())
                    .getOrThrow();
            RingSkyProfile decoded = RingSkyProfile.CODEC.parse(JsonOps.INSTANCE, encoded)
                    .getOrThrow();
            assertEquals(preset.profile(), decoded);
            assertEquals(preset, RingSkyProfile.Preset.matching(decoded));
        }
    }

    @Test
    void lightSourcesHaveDeliberateRelativeScale() {
        assertEquals(0.0F, RingSkyProfile.LightSource.NONE.halfWidth());
        assertTrue(RingSkyProfile.LightSource.DISTANT_SUN.halfWidth()
                > RingSkyProfile.LightSource.COMPACT_SUN.halfWidth());
    }

    @Test
    void rejectsUnknownWireIdentifiersAndFormats() {
        assertThrows(IllegalArgumentException.class, () -> RingSkyProfile.Backdrop.fromId(99));
        assertThrows(IllegalArgumentException.class, () -> RingSkyProfile.LightSource.fromId(99));
        assertThrows(IllegalArgumentException.class, () -> new RingSkyProfile(
                RingSkyProfile.Backdrop.ATMOSPHERE,
                RingSkyProfile.LightSource.COMPACT_SUN, 99));
    }
}
