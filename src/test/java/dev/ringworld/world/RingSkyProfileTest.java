package dev.ringworld.world;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingSkyProfileTest {
    @Test
    void everyBackdropAndSunCombinationRoundTrips() {
        for (RingSkyProfile.Backdrop backdrop : RingSkyProfile.Backdrop.values()) {
            for (RingSkyProfile.LightSource source : RingSkyProfile.LightSource.values()) {
                RingSkyProfile profile = new RingSkyProfile(
                        backdrop, source, RingSkyProfile.FORMAT_VERSION);
                var encoded = RingSkyProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile)
                        .getOrThrow();
                RingSkyProfile decoded = RingSkyProfile.CODEC.parse(JsonOps.INSTANCE, encoded)
                        .getOrThrow();
                assertEquals(profile, decoded);
            }
        }
    }

    @Test
    void lightSourcesHaveDeliberateRelativeScale() {
        assertEquals(0.0F, RingSkyProfile.LightSource.NONE.halfWidth());
        assertTrue(RingSkyProfile.LightSource.LARGE.halfWidth()
                > RingSkyProfile.LightSource.SMALL.halfWidth());
    }

    @Test
    void rejectsUnknownWireIdentifiersAndFormats() {
        assertThrows(IllegalArgumentException.class, () -> RingSkyProfile.Backdrop.fromId(99));
        assertThrows(IllegalArgumentException.class, () -> RingSkyProfile.LightSource.fromId(99));
        assertThrows(IllegalArgumentException.class, () -> new RingSkyProfile(
                RingSkyProfile.Backdrop.ATMOSPHERE,
                RingSkyProfile.LightSource.SMALL, 99));
    }
}
