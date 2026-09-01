package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingSkyCycleTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(128, 2_048);

    @Test
    void sunNeverMoves() {
        assertEquals(0.0F, RingSkyCycle.FIXED_SUN_ANGLE_RADIANS);
    }

    @Test
    void dimmingSunIsOneTenthVanillaSize() {
        assertEquals(3.0F, RingSkyCycle.SUN_HALF_WIDTH);
        assertEquals(0.2625F, RingSkyCycle.SUN_VISIBLE_TEXTURE_SCALE);
        assertTrue(RingSkyCycle.SUN_ANGULAR_HALF_WIDTH_DEGREES < 0.5F);
    }

    @Test
    void starFieldCounterRotatesWithCanonicalLongitude() {
        assertEquals(0.0F, RingSkyCycle.starFieldAngleRadians(GEOMETRY, 0.0), 0.00001F);
        assertEquals(-(float)(Math.PI / 2.0),
                RingSkyCycle.starFieldAngleRadians(GEOMETRY, 512.0), 0.00001F);
        assertEquals(-(float)Math.PI,
                RingSkyCycle.starFieldAngleRadians(GEOMETRY, 1_024.0), 0.00001F);
        assertEquals(RingSkyCycle.starFieldAngleRadians(GEOMETRY, 37.25),
                RingSkyCycle.starFieldAngleRadians(GEOMETRY, 2_085.25), 0.00001F);
    }

    @Test
    void backdropPoliciesPreserveAtmosphereAndFixDarkModes() {
        assertEquals(-1, RingSkyCycle.fixedBackdropRgb(RingSkyProfile.DEFAULT));
        assertEquals(0x050810, RingSkyCycle.fixedBackdropRgb(new RingSkyProfile(
                RingSkyProfile.Backdrop.NIGHT, RingSkyProfile.LightSource.SMALL,
                RingSkyProfile.FORMAT_VERSION)));
        assertEquals(0x010103, RingSkyCycle.fixedBackdropRgb(new RingSkyProfile(
                RingSkyProfile.Backdrop.VOID, RingSkyProfile.LightSource.NONE,
                RingSkyProfile.FORMAT_VERSION)));
    }

    @Test
    void darkBackdropStarPoliciesAreExact() {
        RingSkyProfile night = new RingSkyProfile(
                RingSkyProfile.Backdrop.NIGHT, RingSkyProfile.LightSource.SMALL,
                RingSkyProfile.FORMAT_VERSION);
        RingSkyProfile empty = new RingSkyProfile(
                RingSkyProfile.Backdrop.VOID, RingSkyProfile.LightSource.NONE,
                RingSkyProfile.FORMAT_VERSION);
        assertEquals(0.42F, RingSkyCycle.starBrightness(RingSkyProfile.DEFAULT, 0.42F));
        assertEquals(0.88F, RingSkyCycle.starBrightness(night, 0.2F));
        assertEquals(0.94F, RingSkyCycle.starBrightness(night, 0.94F));
        assertEquals(0.0F, RingSkyCycle.starBrightness(empty, 1.0F));
    }

    @Test
    void selectedSunScaleMatchesSmallLargeAndNone() {
        assertEquals(1.0F, RingSkyCycle.sunAlphaScale(RingSkyProfile.DEFAULT));
        assertEquals(0.72F, RingSkyCycle.sunAlphaScale(new RingSkyProfile(
                RingSkyProfile.Backdrop.ATMOSPHERE, RingSkyProfile.LightSource.LARGE,
                RingSkyProfile.FORMAT_VERSION)));
        assertEquals(0.0F, RingSkyCycle.sunAlphaScale(new RingSkyProfile(
                RingSkyProfile.Backdrop.VOID, RingSkyProfile.LightSource.NONE,
                RingSkyProfile.FORMAT_VERSION)));
    }

    @Test
    void noonIsBrightAndNeutral() {
        var noon = RingSkyCycle.sunVisual(6_000);
        assertEquals(1.0F, noon.brightness());
        assertEquals(1.0F, noon.red());
        assertEquals(1.0F, noon.green());
        assertEquals(0.94F, noon.blue());
    }

    @Test
    void dawnAndDuskAreWarmAndDim() {
        var dawn = RingSkyCycle.sunVisual(0);
        var dusk = RingSkyCycle.sunVisual(12_000);
        assertEquals(0.35F, dawn.brightness());
        assertEquals(0.35F, dusk.brightness());
        assertTrue(dawn.red() > dawn.green() && dawn.green() > dawn.blue());
        assertTrue(dusk.red() > dusk.green() && dusk.green() > dusk.blue());
    }

    @Test
    void midnightIsCoolAndNearlyDark() {
        var midnight = RingSkyCycle.sunVisual(18_000);
        assertEquals(0.04F, midnight.brightness());
        assertTrue(midnight.blue() > midnight.green() && midnight.green() > midnight.red());
    }

    @Test
    void cycleWrapsForNegativeAndMultiDayTimes() {
        assertEquals(RingSkyCycle.sunVisual(23_500), RingSkyCycle.sunVisual(-500));
        assertEquals(RingSkyCycle.sunVisual(18_000), RingSkyCycle.sunVisual(42_000));
    }

    @Test
    void toneChangesContinuouslyBetweenKeyframes() {
        var before = RingSkyCycle.sunVisual(5_999.9);
        var atNoon = RingSkyCycle.sunVisual(6_000);
        var after = RingSkyCycle.sunVisual(6_000.1);
        assertEquals(atNoon.brightness(), before.brightness(), 0.0001F);
        assertEquals(atNoon.brightness(), after.brightness(), 0.0001F);
    }
}
