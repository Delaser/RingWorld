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
    void panelMovesOffTheSunDuringDayAndCoversItAtNight() {
        assertTrue(RingSkyCycle.shadowPanel(6_000).visible());
        assertEquals(0.0F, RingSkyCycle.shadowPanel(6_000).occlusion());
        assertTrue(RingSkyCycle.shadowPanel(18_000).visible());
        assertEquals(1.0F, RingSkyCycle.shadowPanel(18_000).occlusion());
    }

    @Test
    void slabEdgesDefineDuskAndDawn() {
        double halfTransitionTicks = RingSkyCycle.SUN_ORBITAL_HALF_WIDTH_DEGREES
                / RingSkyCycle.PANEL_SPEED_DEGREES_PER_TICK;
        double dawnStarts = 18_000.0
                + (RingSkyCycle.PANEL_ANGULAR_HALF_LENGTH_DEGREES
                - RingSkyCycle.SUN_ORBITAL_HALF_WIDTH_DEGREES)
                / RingSkyCycle.PANEL_SPEED_DEGREES_PER_TICK;
        double dawnMidpoint = 18_000.0
                + RingSkyCycle.PANEL_ANGULAR_HALF_LENGTH_DEGREES
                / RingSkyCycle.PANEL_SPEED_DEGREES_PER_TICK;
        var duskContact = RingSkyCycle.shadowPanel(12_000);
        var dusk = RingSkyCycle.shadowPanel(12_000 + halfTransitionTicks);
        var nightStart = RingSkyCycle.shadowPanel(12_000 + 2.0 * halfTransitionTicks + 0.1);
        var midnight = RingSkyCycle.shadowPanel(18_000);
        var dawnStart = RingSkyCycle.shadowPanel(dawnStarts);
        var dawn = RingSkyCycle.shadowPanel(dawnMidpoint);
        var dawnEnd = RingSkyCycle.shadowPanel(24_000);
        assertEquals(-(RingSkyCycle.PANEL_ANGULAR_HALF_LENGTH_DEGREES
                + RingSkyCycle.SUN_ORBITAL_HALF_WIDTH_DEGREES), duskContact.offset(), 0.001F);
        assertEquals(0.0F, duskContact.occlusion(), 0.001F);
        assertTrue(dusk.occlusion() > 0.49F && dusk.occlusion() < 0.51F);
        assertTrue(nightStart.occlusion() > 0.99F);
        assertEquals(1.0F, midnight.occlusion());
        assertEquals(1.0F, dawnStart.occlusion(), 0.001F);
        assertTrue(dawn.occlusion() > 0.49F && dawn.occlusion() < 0.51F);
        assertEquals(0.0F, dawnEnd.occlusion(), 0.001F);
    }

    @Test
    void panelMovesAtConstantSpeedAndMatchesSunWidth() {
        assertEquals((float)Math.toDegrees(Math.atan((RingSkyCycle.SUN_HALF_WIDTH
                        * RingSkyCycle.SUN_VISIBLE_TEXTURE_SCALE) / RingSkyCycle.SUN_RENDER_DISTANCE)),
                RingSkyCycle.SUN_ANGULAR_HALF_WIDTH_DEGREES);
        assertEquals((float)Math.tan(Math.toRadians(RingSkyCycle.SUN_ANGULAR_HALF_WIDTH_DEGREES))
                        * RingSkyCycle.PANEL_NEAR_DISTANCE,
                RingSkyCycle.PANEL_PHYSICAL_HALF_WIDTH);
        assertEquals(0.75F,
                RingSkyCycle.shadowPanel(13_000).offset() - RingSkyCycle.shadowPanel(12_000).offset(),
                0.001F);
        assertEquals(0.75F,
                RingSkyCycle.shadowPanel(18_000).offset() - RingSkyCycle.shadowPanel(17_000).offset(),
                0.001F);
    }

    @Test
    void twentySmallPanelsPreserveOneLocalCyclePerMinecraftDay() {
        assertEquals(20, RingSkyCycle.SHADOW_PANEL_COUNT);
        assertEquals(18.0F, RingSkyCycle.PANEL_SPACING_DEGREES);
        assertEquals(480_000L, RingSkyCycle.PANEL_ROTATION_PERIOD_TICKS);
        assertEquals(360.0F / RingSkyCycle.PANEL_ROTATION_PERIOD_TICKS,
                RingSkyCycle.PANEL_SPEED_DEGREES_PER_TICK);
        assertTrue(RingSkyCycle.PANEL_ANGULAR_HALF_LENGTH_DEGREES > 0.0F);
        assertTrue(RingSkyCycle.PANEL_ANGULAR_HALF_LENGTH_DEGREES
                < RingSkyCycle.PANEL_SPACING_DEGREES / 2.0F);
        assertEquals(RingSkyCycle.shadowPanel(6_000), RingSkyCycle.shadowPanel(30_000));
    }

    @Test
    void cycleWrapsForNegativeAndMultiDayTimes() {
        assertEquals(RingSkyCycle.shadowPanel(23_500), RingSkyCycle.shadowPanel(-500));
        assertEquals(RingSkyCycle.shadowPanel(18_000), RingSkyCycle.shadowPanel(42_000));
    }
}
