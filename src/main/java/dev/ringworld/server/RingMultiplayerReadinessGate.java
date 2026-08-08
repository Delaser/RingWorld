package dev.ringworld.server;

/**
 * Pure pacing barrier for the disposable two-client seam fixture.
 *
 * <p>The seam assertion samples real client interpolation.  Starting it during
 * a cold server hitch turns an infrastructure delay into a false topology
 * failure, so both clients must first observe a bounded run of normally paced
 * server ticks.</p>
 */
final class RingMultiplayerReadinessGate {
    static final int REQUIRED_CONSECUTIVE_ON_TIME_TICKS = 100;
    static final int MAXIMUM_OBSERVED_TICKS = 1_200;
    static final long MAXIMUM_TICK_INTERVAL_NANOS = 100_000_000L;
    static final long MAXIMUM_WAIT_NANOS = 60_000_000_000L;

    enum Result {
        WARMING,
        READY,
        TIMED_OUT
    }

    private long startedNanos = Long.MIN_VALUE;
    private long previousTickNanos = Long.MIN_VALUE;
    private int observedTicks;
    private int consecutiveOnTimeTicks;
    private long longestTickIntervalNanos;

    Result observe(long nowNanos) {
        if (startedNanos == Long.MIN_VALUE) {
            startedNanos = nowNanos;
            previousTickNanos = nowNanos;
            observedTicks = 1;
            consecutiveOnTimeTicks = 0;
            return Result.WARMING;
        }

        long intervalNanos = Math.max(0L, nowNanos - previousTickNanos);
        previousTickNanos = nowNanos;
        longestTickIntervalNanos = Math.max(longestTickIntervalNanos, intervalNanos);
        observedTicks++;
        if (intervalNanos <= MAXIMUM_TICK_INTERVAL_NANOS) {
            consecutiveOnTimeTicks++;
            if (consecutiveOnTimeTicks >= REQUIRED_CONSECUTIVE_ON_TIME_TICKS) {
                return Result.READY;
            }
        } else {
            consecutiveOnTimeTicks = 0;
        }

        if (observedTicks >= MAXIMUM_OBSERVED_TICKS
                || nowNanos - startedNanos >= MAXIMUM_WAIT_NANOS) {
            return Result.TIMED_OUT;
        }
        return Result.WARMING;
    }

    int observedTicks() {
        return observedTicks;
    }

    int consecutiveOnTimeTicks() {
        return consecutiveOnTimeTicks;
    }

    long longestTickIntervalNanos() {
        return longestTickIntervalNanos;
    }
}
