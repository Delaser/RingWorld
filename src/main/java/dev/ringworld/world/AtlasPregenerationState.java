package dev.ringworld.world;

import java.util.EnumSet;
import java.util.Set;

/** Lifecycle states shared by every pregeneration adapter. */
public enum AtlasPregenerationState {
    IDLE,
    RUNNING,
    PAUSED,
    SAVING,
    COMPLETE,
    CANCELLED,
    FAILED;

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
            case RUNNING -> EnumSet.of(PAUSED, SAVING, COMPLETE, CANCELLED, FAILED);
            case PAUSED -> EnumSet.of(RUNNING, CANCELLED, FAILED);
            case SAVING -> EnumSet.of(RUNNING, COMPLETE, CANCELLED, FAILED);
            case COMPLETE, CANCELLED, FAILED -> EnumSet.noneOf(AtlasPregenerationState.class);
        };
    }
}
