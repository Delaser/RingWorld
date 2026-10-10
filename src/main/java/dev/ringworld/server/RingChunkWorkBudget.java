package dev.ringworld.server;

/** Owner-thread work accounting, shared by all graph polls in one server tick. */
public final class RingChunkWorkBudget {
    public static final long NANOS = 2_000_000L;
    public static final int GRAPH_NODES = 2048;
    private int tick = Integer.MIN_VALUE;
    private int nodes;
    private long nanos;

    public void begin(int currentTick) {
        if (tick == currentTick) return;
        tick = currentTick;
        nodes = 0;
        nanos = 0;
    }

    public int nextBatch() {
        if (nodes >= GRAPH_NODES || (nodes > 0 && nanos >= NANOS)) return 0;
        return Math.min(256, GRAPH_NODES - nodes);
    }

    public void record(int count, long elapsedNanos) {
        nodes += count;
        nanos += Math.max(0, elapsedNanos);
    }

    public int processedNodes() { return nodes; }
    public long processingNanos() { return nanos; }
}
