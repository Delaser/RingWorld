package dev.ringworld.server;

import dev.ringworld.world.AtlasPregenerationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingAtlasPregenerationSchedulingPolicyTest {
    @Test
    void onlyRunningHandleMayScheduleChunks() {
        assertTrue(RingAtlasPregenerationSchedulingPolicy.maySchedule(AtlasPregenerationState.RUNNING));
        assertFalse(RingAtlasPregenerationSchedulingPolicy.maySchedule(AtlasPregenerationState.IDLE));
        assertFalse(RingAtlasPregenerationSchedulingPolicy.maySchedule(AtlasPregenerationState.PAUSED));
        assertFalse(RingAtlasPregenerationSchedulingPolicy.maySchedule(AtlasPregenerationState.SAVING));
        assertFalse(RingAtlasPregenerationSchedulingPolicy.maySchedule(AtlasPregenerationState.CANCELLED));
    }
}
