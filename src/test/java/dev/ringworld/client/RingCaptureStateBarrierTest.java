package dev.ringworld.client;

import dev.ringworld.world.AtlasPregenerationState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RingCaptureStateBarrierTest {
    @Test
    void waitingBeforeAcknowledgementCannotCountAsRenderedAcknowledgement() {
        var barrier = new RingCaptureStateBarrier(3);
        assertFalse(barrier.ready(AtlasPregenerationState.PAUSED, AtlasPregenerationState.RUNNING, 100));
        assertFalse(barrier.ready(AtlasPregenerationState.RUNNING, AtlasPregenerationState.RUNNING, 200));
        assertFalse(barrier.ready(AtlasPregenerationState.RUNNING, AtlasPregenerationState.RUNNING, 202));
        assertTrue(barrier.ready(AtlasPregenerationState.RUNNING, AtlasPregenerationState.RUNNING, 203));
        assertEquals(200, barrier.firstObservedFrame());
    }

    @Test
    void multipleTicksInOneFrameDoNotAdvanceCapture() {
        var barrier = new RingCaptureStateBarrier(3);
        for (int i = 0; i < 100; i++) assertFalse(barrier.ready(AtlasPregenerationState.PAUSED, AtlasPregenerationState.PAUSED, 100));
        assertFalse(barrier.ready(AtlasPregenerationState.PAUSED, AtlasPregenerationState.PAUSED, 102));
        assertTrue(barrier.ready(AtlasPregenerationState.PAUSED, AtlasPregenerationState.PAUSED, 103));
    }

    @Test
    void losingExpectedStateRequiresANewRenderedWindow() {
        var barrier = new RingCaptureStateBarrier(3);
        assertFalse(barrier.ready(AtlasPregenerationState.RUNNING, AtlasPregenerationState.RUNNING, 10));
        assertFalse(barrier.ready(null, AtlasPregenerationState.RUNNING, 12));
        assertFalse(barrier.ready(AtlasPregenerationState.RUNNING, AtlasPregenerationState.RUNNING, 20));
        assertFalse(barrier.ready(AtlasPregenerationState.RUNNING, AtlasPregenerationState.RUNNING, 22));
        assertTrue(barrier.ready(AtlasPregenerationState.RUNNING, AtlasPregenerationState.RUNNING, 23));
    }

    @Test
    void resetAndNewExpectedStateCannotInheritAnEarlierReadyWindow() {
        var barrier = new RingCaptureStateBarrier(3);
        barrier.ready(AtlasPregenerationState.RUNNING, AtlasPregenerationState.RUNNING, 10);
        assertTrue(barrier.ready(AtlasPregenerationState.RUNNING, AtlasPregenerationState.RUNNING, 13));
        barrier.reset();
        assertFalse(barrier.ready(AtlasPregenerationState.RUNNING, AtlasPregenerationState.RUNNING, 20));
        assertFalse(barrier.ready(AtlasPregenerationState.CANCELLED, AtlasPregenerationState.CANCELLED, 30));
        assertTrue(barrier.ready(AtlasPregenerationState.CANCELLED, AtlasPregenerationState.CANCELLED, 33));
    }

    @Test
    void backwardsOrInvalidFrameCountersFailClosed() {
        var barrier = new RingCaptureStateBarrier(3);
        barrier.ready(AtlasPregenerationState.PAUSED, AtlasPregenerationState.PAUSED, 100);
        assertThrows(IllegalStateException.class, () -> barrier.ready(AtlasPregenerationState.PAUSED, AtlasPregenerationState.PAUSED, 99));
        assertThrows(IllegalArgumentException.class, () -> barrier.ready(null, AtlasPregenerationState.PAUSED, -1));
        assertThrows(NullPointerException.class, () -> barrier.ready(null, null, 101));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void invalidDelayIsRejected(int delay) {
        assertThrows(IllegalArgumentException.class, () -> new RingCaptureStateBarrier(delay));
    }
}
