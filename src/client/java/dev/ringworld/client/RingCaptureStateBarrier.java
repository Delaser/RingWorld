package dev.ringworld.client;

import dev.ringworld.world.AtlasPregenerationState;
import java.util.Objects;

/** Opt-in capture barrier: allow real frames after a server state is acknowledged. */
public final class RingCaptureStateBarrier {
    private final int settleFrames;
    private AtlasPregenerationState observedExpected;
    private long firstObservedFrame = -1;

    public RingCaptureStateBarrier(int settleFrames) {
        if (settleFrames <= 0) throw new IllegalArgumentException("positive frame delay required");
        this.settleFrames = settleFrames;
    }

    public boolean ready(AtlasPregenerationState actual, AtlasPregenerationState expected, long renderedFrame) {
        Objects.requireNonNull(expected, "expected");
        if (renderedFrame < 0) throw new IllegalArgumentException("negative rendered-frame count");
        if (actual != expected) { reset(); return false; }
        if (observedExpected != expected) {
            observedExpected = expected;
            firstObservedFrame = renderedFrame;
            return false;
        }
        if (renderedFrame < firstObservedFrame) throw new IllegalStateException("rendered frames moved backwards");
        return renderedFrame - firstObservedFrame >= settleFrames;
    }

    public long firstObservedFrame() { return firstObservedFrame; }

    public void reset() {
        observedExpected = null;
        firstObservedFrame = -1;
    }
}
