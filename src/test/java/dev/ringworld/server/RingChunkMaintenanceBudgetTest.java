package dev.ringworld.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingChunkMaintenanceBudgetTest {
    @Test void ordinaryWorkStaysSmallUntilTheVanillaBacklogThreshold() {
        var ordinary = RingChunkMaintenanceBudget.select(2000, 20, 100);
        assertEquals(2_000_000L, ordinary.nanos());
        assertEquals(16, ordinary.saves());
        assertEquals(8_000_000L, RingChunkMaintenanceBudget.select(2001, 20, 100).nanos());
    }
    @Test void aLargeTransitionGetsBoundedCleanupBeforeHeapFills() {
        var pressure = RingChunkMaintenanceBudget.select(8192, 20, 100);
        assertEquals(20_000_000L, pressure.nanos());
        assertEquals(128, pressure.saves());
        assertEquals(16384, pressure.tasks());
    }
    @Test void memoryPressureBoostsEvenASmallQueueWithoutChangingHeapSettings() {
        assertEquals(8_000_000L, RingChunkMaintenanceBudget.select(1, 80, 100).nanos());
        assertEquals(20_000_000L, RingChunkMaintenanceBudget.select(1, 90, 100).nanos());
        assertEquals(2_000_000L, RingChunkMaintenanceBudget.select(1, 79, 100).nanos());
        assertEquals(2_000_000L, RingChunkMaintenanceBudget.select(0, 0, 0).nanos());
    }
}
