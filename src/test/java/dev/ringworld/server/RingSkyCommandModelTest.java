package dev.ringworld.server;

import dev.ringworld.world.RingSkyProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RingSkyCommandModelTest {
    @Test
    void parsesEveryDocumentedCommandValueCaseInsensitively() {
        assertEquals(RingSkyProfile.Backdrop.ATMOSPHERE,
                RingSkyCommandModel.parseBackdrop("ATMOSPHERE"));
        assertEquals(RingSkyProfile.Backdrop.NIGHT,
                RingSkyCommandModel.parseBackdrop("night"));
        assertEquals(RingSkyProfile.Backdrop.VOID,
                RingSkyCommandModel.parseBackdrop("Void"));
        assertEquals(RingSkyProfile.LightSource.SMALL,
                RingSkyCommandModel.parseLightSource("SMALL"));
        assertEquals(RingSkyProfile.LightSource.LARGE,
                RingSkyCommandModel.parseLightSource("large"));
        assertEquals(RingSkyProfile.LightSource.NONE,
                RingSkyCommandModel.parseLightSource("None"));
    }

    @Test
    void skyAndSunUpdatesPreserveTheOtherProfileDimension() {
        RingSkyProfile initial = new RingSkyProfile(
                RingSkyProfile.Backdrop.NIGHT,
                RingSkyProfile.LightSource.LARGE,
                RingSkyProfile.FORMAT_VERSION);

        RingSkyProfile sky = RingSkyCommandModel.withBackdrop(
                initial, RingSkyProfile.Backdrop.VOID);
        assertEquals(RingSkyProfile.Backdrop.VOID, sky.backdrop());
        assertEquals(RingSkyProfile.LightSource.LARGE, sky.lightSource());

        RingSkyProfile sun = RingSkyCommandModel.withLightSource(
                initial, RingSkyProfile.LightSource.NONE);
        assertEquals(RingSkyProfile.Backdrop.NIGHT, sun.backdrop());
        assertEquals(RingSkyProfile.LightSource.NONE, sun.lightSource());
    }

    @Test
    void rejectsUndocumentedAndMissingValues() {
        assertThrows(IllegalArgumentException.class,
                () -> RingSkyCommandModel.parseBackdrop("day"));
        assertThrows(IllegalArgumentException.class,
                () -> RingSkyCommandModel.parseLightSource("medium"));
        assertThrows(IllegalArgumentException.class,
                () -> RingSkyCommandModel.parseBackdrop(null));
        assertThrows(IllegalArgumentException.class,
                () -> RingSkyCommandModel.parseLightSource(null));
        assertThrows(IllegalArgumentException.class,
                () -> RingSkyCommandModel.withBackdrop(
                        null, RingSkyProfile.Backdrop.ATMOSPHERE));
        assertThrows(IllegalArgumentException.class,
                () -> RingSkyCommandModel.withLightSource(
                        null, RingSkyProfile.LightSource.SMALL));
    }
}
