package dev.ringworld.world;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RingSkySettingsTest {
    @Test
    void savedProfileRoundTripsIndependentlyFromLayoutSettings() {
        RingSkyProfile profile = new RingSkyProfile(
                RingSkyProfile.Backdrop.VOID,
                RingSkyProfile.LightSource.NONE,
                RingSkyProfile.FORMAT_VERSION);
        RingSkySettings source = new RingSkySettings(profile);
        var encoded = RingSkySettings.codecForTests()
                .encodeStart(JsonOps.INSTANCE, source).getOrThrow();

        RingSkySettings decoded = RingSkySettings.codecForTests()
                .parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(profile, decoded.profile());
    }

    @Test
    void profileIsRequiredAndDefaultRemainsAtmosphereWithSmallSun() {
        assertEquals(RingSkyProfile.Backdrop.ATMOSPHERE, RingSkyProfile.DEFAULT.backdrop());
        assertEquals(RingSkyProfile.LightSource.SMALL, RingSkyProfile.DEFAULT.lightSource());
        assertThrows(NullPointerException.class, () -> new RingSkySettings(null));
    }
}
