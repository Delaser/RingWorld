package dev.ringworld.server;

/** Whether loading-ticket propagation must settle before nonessential location checks. */
public interface RingChunkGraphAccess {
    boolean ringworld$graphPending();
}
