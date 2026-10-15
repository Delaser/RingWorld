package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingAtlasConcurrencyOptionsTest {
    @Test void automaticAndFixedRequestInputsAreDistinctAndBounded() {
        assertTrue(AtlasPregenerationOptions.isAdaptiveConcurrency(null));
        assertTrue(AtlasPregenerationOptions.isAdaptiveConcurrency(" AUTO "));
        assertEquals(4, AtlasPregenerationOptions.trialConcurrency(null));
        assertEquals(4, AtlasPregenerationOptions.trialConcurrency("auto"));
        for (int rate = 1; rate <= 8; rate++) {
            assertEquals(rate, AtlasPregenerationOptions.trialConcurrency(" " + rate + " "));
            assertFalse(AtlasPregenerationOptions.isAdaptiveConcurrency(Integer.toString(rate)));
        }
        for (String value : new String[]{"", "0", "9", "-1", "1.5", "fast", "2147483648"}) {
            assertThrows(IllegalArgumentException.class, () -> AtlasPregenerationOptions.trialConcurrency(value));
        }
    }
}
