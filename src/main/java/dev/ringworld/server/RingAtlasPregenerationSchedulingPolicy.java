package dev.ringworld.server;

import dev.ringworld.world.AtlasPregenerationState;

/** Pure scheduling gate shared by the server-thread service and regression tests. */
final class RingAtlasPregenerationSchedulingPolicy {
    private RingAtlasPregenerationSchedulingPolicy() { }

    static boolean maySchedule(AtlasPregenerationState state) {
        return state == AtlasPregenerationState.RUNNING;
    }
}
