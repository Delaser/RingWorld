package dev.ringworld.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingChunkWorkBudgetTest {
    @Test void repeatedPollsShareOneTickAllowanceAndResetOnlyOnTheNextTick() {
        var budget = new RingChunkWorkBudget();
        budget.begin(40);
        for (int i = 0; i < 8; i++) {
            assertEquals(256, budget.nextBatch());
            budget.record(256, 10);
            budget.begin(40);
        }
        assertEquals(0, budget.nextBatch());
        budget.begin(41);
        assertEquals(256, budget.nextBatch());
    }

    @Test void anExpensiveFirstBatchStopsFurtherWorkWithoutLosingTheNextTicksProgress() {
        var budget = new RingChunkWorkBudget();
        budget.begin(1);
        budget.record(17, 3_000_000);
        assertEquals(0, budget.nextBatch());
        budget.begin(2);
        assertEquals(256, budget.nextBatch());
    }

    @Test void synchronousEscapeAndFailuresRestoreTheOuterGraphScope() {
        var outer = new RingChunkWorkBudget();
        assertNull(RingChunkWorkContext.graphBudget());
        assertTrue(RingChunkWorkContext.graph(outer, () -> {
            assertSame(outer, RingChunkWorkContext.graphBudget());
            assertThrows(IllegalStateException.class, () -> RingChunkWorkContext.graph(null, () -> {
                assertNull(RingChunkWorkContext.graphBudget());
                throw new IllegalStateException();
            }));
            assertSame(outer, RingChunkWorkContext.graphBudget());
            return true;
        }));
        assertNull(RingChunkWorkContext.graphBudget());
    }

    @Test void scheduledSaveScopeDoesNotLeakIntoExplicitOrShutdownSaves() {
        assertFalse(RingChunkWorkContext.periodicSave());
        assertThrows(IllegalArgumentException.class, () -> RingChunkWorkContext.periodicSave(() -> {
            assertTrue(RingChunkWorkContext.periodicSave());
            assertTrue(RingChunkWorkContext.periodicSave(() -> RingChunkWorkContext.periodicSave()));
            throw new IllegalArgumentException();
        }));
        assertFalse(RingChunkWorkContext.periodicSave());
    }
}
