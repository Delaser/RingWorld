package dev.ringworld.server;

import dev.ringworld.world.AtlasPregenerationAction;
import dev.ringworld.world.AtlasPregenerationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingAtlasCommandPolicyTest {
    @Test
    void idleStartAndResumeBothBeginFromDurableProgress() {
        assertEquals(RingAtlasCommandPolicy.Outcome.START, RingAtlasCommandPolicy.decide(
                AtlasPregenerationAction.START, AtlasPregenerationState.IDLE));
        assertEquals(RingAtlasCommandPolicy.Outcome.START, RingAtlasCommandPolicy.decide(
                AtlasPregenerationAction.RESUME, AtlasPregenerationState.IDLE));
        assertEquals(RingAtlasCommandPolicy.Outcome.START, RingAtlasCommandPolicy.decide(
                AtlasPregenerationAction.RESUME, AtlasPregenerationState.FAILED));
    }

    @Test
    void processLocalPauseAndResumeRemainStrict() {
        assertEquals(RingAtlasCommandPolicy.Outcome.PAUSE, RingAtlasCommandPolicy.decide(
                AtlasPregenerationAction.PAUSE, AtlasPregenerationState.RUNNING));
        assertEquals(RingAtlasCommandPolicy.Outcome.RESUME, RingAtlasCommandPolicy.decide(
                AtlasPregenerationAction.RESUME, AtlasPregenerationState.PAUSED));
        assertEquals(RingAtlasCommandPolicy.Outcome.NOT_PAUSED, RingAtlasCommandPolicy.decide(
                AtlasPregenerationAction.RESUME, AtlasPregenerationState.RUNNING));
        assertEquals(RingAtlasCommandPolicy.Outcome.NOT_RUNNING, RingAtlasCommandPolicy.decide(
                AtlasPregenerationAction.PAUSE, AtlasPregenerationState.IDLE));
    }

    @Test
    void completeAndActiveJobsAreNotDuplicated() {
        assertEquals(RingAtlasCommandPolicy.Outcome.ALREADY_COMPLETE, RingAtlasCommandPolicy.decide(
                AtlasPregenerationAction.START, AtlasPregenerationState.COMPLETE));
        assertEquals(RingAtlasCommandPolicy.Outcome.ALREADY_ACTIVE, RingAtlasCommandPolicy.decide(
                AtlasPregenerationAction.START, AtlasPregenerationState.RUNNING));
        assertEquals(RingAtlasCommandPolicy.Outcome.UNSUPPORTED, RingAtlasCommandPolicy.decide(
                AtlasPregenerationAction.CANCEL, AtlasPregenerationState.RUNNING));
    }
}
