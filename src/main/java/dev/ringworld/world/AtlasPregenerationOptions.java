package dev.ringworld.world;

import java.util.Objects;

/**
 * Immutable, loader-neutral scheduling policy for an atlas pregeneration job.
 *
 * <p>The default is adaptive, starting at four requests with a pending-task soft limit of 64.
 * The explicit concurrency trial accepts up to eight requests; all callers
 * share the same server-owned writer and bounded completion capture.</p>
 */
public record AtlasPregenerationOptions(
        AtlasPregenerationMode mode,
        int maxInFlightChunks,
        int pendingTaskSoftLimit,
        int checkpointIntervalChunks,
        int progressIntervalTicks,
        boolean stopServerWhenComplete) {
    public static final int DEFAULT_MAX_IN_FLIGHT_CHUNKS = 4;
    public static final String CONCURRENCY_PROPERTY = "ringworld.atlasInFlightChunks";
    public static final int DEFAULT_PENDING_TASK_SOFT_LIMIT = 64;
    public static final int DEFAULT_CHECKPOINT_INTERVAL_CHUNKS = 200;
    public static final int DEFAULT_PROGRESS_INTERVAL_TICKS = 20;

    private static final int MAX_IN_FLIGHT_CHUNKS = 16;
    private static final int MAX_PENDING_TASK_SOFT_LIMIT = 4_096;
    private static final int MAX_CHECKPOINT_INTERVAL_CHUNKS = 1_000_000;
    private static final int MAX_PROGRESS_INTERVAL_TICKS = 1_200;

    public AtlasPregenerationOptions {
        Objects.requireNonNull(mode, "mode");
        requireRange("maxInFlightChunks", maxInFlightChunks, 1, MAX_IN_FLIGHT_CHUNKS);
        requireRange("pendingTaskSoftLimit", pendingTaskSoftLimit, 1, MAX_PENDING_TASK_SOFT_LIMIT);
        requireRange("checkpointIntervalChunks", checkpointIntervalChunks, 1,
                MAX_CHECKPOINT_INTERVAL_CHUNKS);
        requireRange("progressIntervalTicks", progressIntervalTicks, 1,
                MAX_PROGRESS_INTERVAL_TICKS);
        if (stopServerWhenComplete && mode != AtlasPregenerationMode.HEADLESS_PREWARM) {
            throw new IllegalArgumentException(
                    "only headless prewarm jobs may stop the server when complete");
        }
    }

    public static AtlasPregenerationOptions backgroundDefaults() {
        return defaults(AtlasPregenerationMode.BACKGROUND);
    }

    public static AtlasPregenerationOptions interactiveDefaults() {
        return defaults(AtlasPregenerationMode.INTERACTIVE);
    }

    public static AtlasPregenerationOptions headlessPrewarmDefaults() {
        return defaults(AtlasPregenerationMode.HEADLESS_PREWARM);
    }

    public static AtlasPregenerationOptions defaults(AtlasPregenerationMode mode) {
        Objects.requireNonNull(mode, "mode");
        return new AtlasPregenerationOptions(mode,
                trialConcurrency(System.getProperty(CONCURRENCY_PROPERTY)),
                DEFAULT_PENDING_TASK_SOFT_LIMIT,
                DEFAULT_CHECKPOINT_INTERVAL_CHUNKS,
                DEFAULT_PROGRESS_INTERVAL_TICKS,
                mode == AtlasPregenerationMode.HEADLESS_PREWARM);
    }

    /** Numeric overrides are fixed; absent/auto starts the adaptive four-request policy. */
    public static int trialConcurrency(String value) {
        if (isAdaptiveConcurrency(value)) return DEFAULT_MAX_IN_FLIGHT_CHUNKS;
        try {
            int result = Integer.parseInt(value.trim());
            if (result >= 1 && result <= 8) return result;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(CONCURRENCY_PROPERTY + " must be auto or an integer from 1 to 8");
    }

    public static boolean isAdaptiveConcurrency(String value) {
        return value == null || "auto".equalsIgnoreCase(value.trim());
    }

    /**
     * Whether two callers can safely share the one world-owned writer. Mode is
     * an intent label for UI/headless adapters; it must not manufacture a
     * second scheduler when background and interactive callers use the same
     * conservative execution policy.
     */
    public boolean sharesExecutionPolicyWith(AtlasPregenerationOptions other) {
        Objects.requireNonNull(other, "other");
        return maxInFlightChunks == other.maxInFlightChunks
                && pendingTaskSoftLimit == other.pendingTaskSoftLimit
                && checkpointIntervalChunks == other.checkpointIntervalChunks
                && progressIntervalTicks == other.progressIntervalTicks
                && stopServerWhenComplete == other.stopServerWhenComplete;
    }

    private static void requireRange(String name, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be in [" + minimum + ", "
                    + maximum + "]: " + value);
        }
    }
}
