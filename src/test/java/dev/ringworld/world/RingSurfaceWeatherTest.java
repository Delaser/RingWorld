package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingSurfaceWeatherTest {
    @Test
    void clearWeatherIsAnExactNoOp() {
        assertEquals(1.0F, RingSurfaceWeather.reveal(0.0F));
    }

    @Test
    void fullRainIsVisibleButClearlyFaded() {
        float reveal = RingSurfaceWeather.reveal(1.0F);
        assertEquals(0.55F, reveal, 0.000001F);
        assertTrue(reveal > 0.0F && reveal < 1.0F);
    }

    @Test
    void changingRainKeepsAContinuousBoundedFade() {
        float previous = 1.0F;
        for (int step = 0; step <= 1000; step++) {
            float current = RingSurfaceWeather.reveal(step / 1000.0F);
            assertTrue(Float.isFinite(current) && current >= 0.55F && current <= previous);
            assertTrue(previous - current < 0.0005F, "weather transition must not jump");
            previous = current;
        }
    }

    @Test
    void outOfRangeOrMalformedWeatherCannotHideTheSurface() {
        assertEquals(1.0F, RingSurfaceWeather.reveal(-10.0F));
        assertEquals(0.55F, RingSurfaceWeather.reveal(10.0F), 0.000001F);
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertEquals(0.55F, RingSurfaceWeather.reveal(value), 0.000001F);
        }
    }
}
