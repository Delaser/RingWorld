package dev.ringworld.world;

import java.util.EnumSet;
import java.util.Set;

/** Lifecycle states shared by every pregeneration adapter. */
public enum AtlasPregenerationState {
    IDLE(1),
    RUNNING(2),
    PAUSED(3),
    SAVING(4),
    COMPLETE(5),
    CANCELLED(6),
    FAILED(7);

    private final int wireValue;
    AtlasPregenerationState(int wireValue) { this.wireValue = wireValue; }
    public int wireValue() { return wireValue; }

    public static AtlasPregenerationState fromWireValue(int wireValue) {
        for (AtlasPregenerationState value : values()) if (value.wireValue == wireValue) return value;
        throw new IllegalArgumentException("unknown atlas state wire value: " + wireValue);
    }

    /** Returns whether a job may make this direct state transition. */
    public boolean canTransitionTo(AtlasPregenerationState target) {
        return allowedTransitions().contains(target);
    }

    /** Terminal jobs cannot be resumed; a later start creates a new job. */
    public boolean isTerminal() {
        return this == COMPLETE || this == CANCELLED || this == FAILED;
    }

    private Set<AtlasPregenerationState> allowedTransitions() {
        return switch (this) {
            case IDLE -> EnumSet.of(RUNNING, CANCELLED, FAILED);
            case RUNNING -> EnumSet.of(PAUSED, SAVING, CANCELLED, FAILED);
            case PAUSED -> EnumSet.of(RUNNING, CANCELLED, FAILED);
            case SAVING -> EnumSet.of(RUNNING, COMPLETE, CANCELLED, FAILED);
            case COMPLETE, CANCELLED, FAILED -> EnumSet.noneOf(AtlasPregenerationState.class);
        };
    }
}
