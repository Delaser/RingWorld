package dev.ringworld.server;

import dev.ringworld.world.AtlasPregenerationAction;
import dev.ringworld.world.AtlasPregenerationState;

import java.util.Objects;

/** Pure command-state policy shared by the adapter and unit tests. */
final class RingAtlasCommandPolicy {
    enum Outcome {
        START,
        PAUSE,
        RESUME,
        ALREADY_COMPLETE,
        ALREADY_ACTIVE,
        NOT_RUNNING,
        NOT_PAUSED,
        UNSUPPORTED
    }

    private RingAtlasCommandPolicy() { }

    static Outcome decide(AtlasPregenerationAction action, AtlasPregenerationState state) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(state, "state");
        return switch (action) {
            case START -> switch (state) {
                case COMPLETE -> Outcome.ALREADY_COMPLETE;
                case RUNNING, PAUSED, SAVING -> Outcome.ALREADY_ACTIVE;
                case IDLE, CANCELLED, FAILED -> Outcome.START;
            };
            case PAUSE -> state == AtlasPregenerationState.RUNNING
                    ? Outcome.PAUSE : Outcome.NOT_RUNNING;
            case RESUME -> switch (state) {
                case IDLE, CANCELLED, FAILED -> Outcome.START;
                case PAUSED -> Outcome.RESUME;
                case RUNNING, SAVING, COMPLETE -> Outcome.NOT_PAUSED;
            };
            case CANCEL -> Outcome.UNSUPPORTED;
        };
    }
}
