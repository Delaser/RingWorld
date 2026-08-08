package dev.ringworld.server;

import dev.ringworld.world.AtlasPregenerationState;

import java.util.Objects;

/** Pure ownership rule for replacing the one world-owned atlas job. */
final class RingAtlasJobReplacementPolicy {
    static final String OUTSTANDING_REQUEST_MESSAGE =
            "the previous atlas generation is still releasing its chunk ticket; retry shortly";

    enum Decision {
        REUSE,
        REPLACE,
        BLOCK_OUTSTANDING_REQUEST
    }

    private RingAtlasJobReplacementPolicy() { }

    static Decision decide(AtlasPregenerationState state,
                           boolean replaceIdleForHeadless,
                           boolean ownsOutstandingRequest) {
        Objects.requireNonNull(state, "state");
        if (replaceIdleForHeadless && state != AtlasPregenerationState.IDLE) {
            throw new IllegalArgumentException("only an idle job can use the headless replacement path");
        }
        boolean replacementCandidate = state.isTerminal() || replaceIdleForHeadless;
        if (!replacementCandidate) return Decision.REUSE;
        return ownsOutstandingRequest
                ? Decision.BLOCK_OUTSTANDING_REQUEST
                : Decision.REPLACE;
    }
}
