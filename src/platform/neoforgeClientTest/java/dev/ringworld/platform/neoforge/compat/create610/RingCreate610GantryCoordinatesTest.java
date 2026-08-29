package dev.ringworld.platform.neoforge.compat.create610;

import dev.ringworld.world.RingGeometry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingCreate610GantryCoordinatesTest {
    private static final RingGeometry GEOMETRY = new RingGeometry(416, 2048);

    @Test
    void positiveSeamCorrectionStaysInHighChart() {
        assertEquals(0.5, RingCreate610GantryCoordinates.nearestClientOffset(
                2047.75, 0.25 - 2047.75, GEOMETRY, true));
    }

    @Test
    void negativeSeamCorrectionStaysInLowChart() {
        assertEquals(-0.5, RingCreate610GantryCoordinates.nearestClientOffset(
                0.25, 2047.75 - 0.25, GEOMETRY, true));
    }

    @Test
    void localAndMultiLapCorrectionsAreIdempotent() {
        assertEquals(0.375, RingCreate610GantryCoordinates.nearestClientOffset(
                2050.0, 0.375, GEOMETRY, true));
        assertEquals(0.5, RingCreate610GantryCoordinates.nearestClientOffset(
                4095.75, 0.25 - 4095.75, GEOMETRY, true));
    }

    @Test
    void inapplicableAxesAndMissingGeometryAreNoOps() {
        assertEquals(-2047.5, RingCreate610GantryCoordinates.nearestClientOffset(
                2047.75, -2047.5, GEOMETRY, false));
        assertEquals(-2047.5, RingCreate610GantryCoordinates.nearestClientOffset(
                2047.75, -2047.5, null, true));
    }

    @Test
    void nonFiniteInputsFailOpenToNativeValue() {
        assertEquals(Double.POSITIVE_INFINITY,
                RingCreate610GantryCoordinates.nearestClientOffset(
                        1.0, Double.POSITIVE_INFINITY, GEOMETRY, true));
        assertEquals(3.0, RingCreate610GantryCoordinates.nearestClientOffset(
                Double.NaN, 3.0, GEOMETRY, true));
        assertEquals(Double.MAX_VALUE, RingCreate610GantryCoordinates.nearestClientOffset(
                Double.MAX_VALUE, Double.MAX_VALUE, GEOMETRY, true));
    }
}
