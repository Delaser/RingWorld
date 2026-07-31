package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingSkyCycleTest {
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
