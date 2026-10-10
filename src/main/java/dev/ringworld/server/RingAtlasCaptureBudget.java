package dev.ringworld.server;

/** Bound explicit ready-chunk capture; a single chunk is never split across ticks. */
final class RingAtlasCaptureBudget {
    static final int MAX_CHUNKS = 4;
    static final long NANOS = 2_000_000;

    private RingAtlasCaptureBudget() { }

    static boolean allows(int captured, int requestLimit, long elapsedNanos) {
        return captured < Math.min(MAX_CHUNKS, requestLimit)
                && (captured == 0 || elapsedNanos < NANOS);
    }
}
