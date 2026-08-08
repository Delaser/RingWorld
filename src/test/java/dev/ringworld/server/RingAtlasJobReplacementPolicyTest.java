package dev.ringworld.server;

import dev.ringworld.world.AtlasPregenerationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingAtlasJobReplacementPolicyTest {
    @Test
    void terminalJobCannotBeReplacedWhileItsTicketReleaseIsRetrying() {
        for (AtlasPregenerationState state : new AtlasPregenerationState[] {
                AtlasPregenerationState.FAILED,
                AtlasPregenerationState.CANCELLED,
                AtlasPregenerationState.COMPLETE
        }) {
            assertEquals(RingAtlasJobReplacementPolicy.Decision.BLOCK_OUTSTANDING_REQUEST,
                    RingAtlasJobReplacementPolicy.decide(state, false, true));
            assertEquals(RingAtlasJobReplacementPolicy.Decision.REPLACE,
                    RingAtlasJobReplacementPolicy.decide(state, false, false));
        }
    }

    @Test
    void headlessIdleReplacementAlsoCannotOrphanAnImpossibleOutstandingRequest() {
        assertEquals(RingAtlasJobReplacementPolicy.Decision.BLOCK_OUTSTANDING_REQUEST,
                RingAtlasJobReplacementPolicy.decide(
                        AtlasPregenerationState.IDLE, true, true));
        assertEquals(RingAtlasJobReplacementPolicy.Decision.REPLACE,
                RingAtlasJobReplacementPolicy.decide(
                        AtlasPregenerationState.IDLE, true, false));
        assertEquals(RingAtlasJobReplacementPolicy.Decision.REUSE,
                RingAtlasJobReplacementPolicy.decide(
                        AtlasPregenerationState.RUNNING, false, true));
    }
}
