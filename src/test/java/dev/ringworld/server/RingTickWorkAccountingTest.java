package dev.ringworld.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingTickWorkAccountingTest {
    @Test void lateModHooksAndScheduledTasksRemainInTheWorkBudget() {
        var sample = new RingTickWorkAccounting();
        sample.begin(0);
        // Vanilla may have stored its history at 4, before an 80-unit tail hook.
        sample.gameFinished(84);
        sample.taskStarted(); sample.taskFinished();
        sample.idleStarted(94); sample.idleFinished(105);
        sample.finish(105);
        assertEquals(84, sample.gameNanos());
        assertEquals(10, sample.scheduledNanos());
        assertEquals(11, sample.idleNanos());
    }

    @Test void managedBlockingInsideAQueuedTaskIsNotIntentionalIdle() {
        var sample = new RingTickWorkAccounting();
        sample.begin(10); sample.gameFinished(20);
        sample.taskStarted(); sample.idleStarted(25); sample.idleFinished(125); sample.taskFinished();
        sample.idleStarted(130); sample.idleFinished(140); sample.finish(145);
        assertEquals(115, sample.scheduledNanos());
        assertEquals(10, sample.idleNanos());
    }

    @Test void gameBlockingCountsAndNestedSchedulerWaitsAreNotDoubleCounted() {
        var sample = new RingTickWorkAccounting();
        sample.begin(0); sample.idleStarted(1); sample.idleFinished(101); sample.gameFinished(110);
        sample.idleStarted(115); sample.idleStarted(116); sample.idleFinished(125); sample.idleFinished(130);
        sample.finish(135);
        assertEquals(110, sample.gameNanos()); assertEquals(15, sample.idleNanos());
        assertEquals(10, sample.scheduledNanos());
    }

    @Test void incompleteOrUnbalancedMeasurementsCannotBecomeSamples() {
        var sample = new RingTickWorkAccounting();
        sample.begin(0); assertThrows(IllegalStateException.class, () -> sample.begin(1));
        assertThrows(IllegalStateException.class, () -> sample.finish(2));
        sample.gameFinished(5); sample.taskStarted();
        assertThrows(IllegalStateException.class, () -> sample.finish(6));
    }
}
