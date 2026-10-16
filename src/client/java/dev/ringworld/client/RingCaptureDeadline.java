package dev.ringworld.client;

/** Monotonic deadline for an opt-in capture stage, independent of readiness flicker. */
final class RingCaptureDeadline {
    private final long budgetNanos;
    private long startedNanos;
    private boolean started;

    RingCaptureDeadline(long budgetNanos) {
        if (budgetNanos <= 0 || budgetNanos >= Long.MAX_VALUE / 2) {
            throw new IllegalArgumentException("capture deadline must be positive and bounded");
        }
        this.budgetNanos = budgetNanos;
    }

    void start(long nowNanos) {
        startedNanos = nowNanos;
        started = true;
    }

    boolean expired(long nowNanos) {
        long elapsed = nowNanos - startedNanos;
        return started && (elapsed < 0 || elapsed >= budgetNanos);
    }
}
