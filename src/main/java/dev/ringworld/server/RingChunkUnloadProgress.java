package dev.ringworld.server;

/** Let owner work progress between retrying callbacks in an unbounded save drain. */
public final class RingChunkUnloadProgress {
    private RingChunkUnloadProgress() { }

    public static void run(Runnable callback, Runnable ownerStep) {
        callback.run();
        ownerStep.run();
    }
}
