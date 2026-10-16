package dev.ringworld.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RingCaptureDeadlineTest {
    @Test
    void noDeadlineExistsBeforeTheStageIsArmed() {
        var deadline = new RingCaptureDeadline(100);
        assertFalse(deadline.expired(Long.MIN_VALUE));
        assertFalse(deadline.expired(Long.MAX_VALUE));
    }

    @Test
    void readinessPollingCannotExtendTheStageDeadline() {
        var deadline = new RingCaptureDeadline(100);
        deadline.start(1_000);
        for (long now = 1_000; now < 1_100; now++) assertFalse(deadline.expired(now));
        assertTrue(deadline.expired(1_100));
        assertTrue(deadline.expired(1_200));
    }

    @Test
    void aNewStageGetsItsOwnCompleteBudget() {
        var deadline = new RingCaptureDeadline(100);
        deadline.start(100);
        assertTrue(deadline.expired(200));
        deadline.start(300);
        assertFalse(deadline.expired(399));
        assertTrue(deadline.expired(400));
    }

    @Test
    void nanoTimeRolloverPreservesElapsedTime() {
        var deadline = new RingCaptureDeadline(25);
        deadline.start(Long.MAX_VALUE - 10);
        assertFalse(deadline.expired(Long.MIN_VALUE + 13));
        assertTrue(deadline.expired(Long.MIN_VALUE + 14));
    }

    @Test
    void anUnexpectedBackwardClockFailsClosed() {
        var deadline = new RingCaptureDeadline(100);
        deadline.start(1_000);
        assertTrue(deadline.expired(999));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MAX_VALUE, Long.MAX_VALUE / 2})
    void invalidBudgetsAreRejected(long budget) {
        assertThrows(IllegalArgumentException.class, () -> new RingCaptureDeadline(budget));
    }
}
