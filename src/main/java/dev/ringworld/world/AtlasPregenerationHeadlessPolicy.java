package dev.ringworld.world;

import java.util.Objects;

/** Pure policy seam for adopting the disabled-background handle in headless mode. */
public final class AtlasPregenerationHeadlessPolicy {
    private AtlasPregenerationHeadlessPolicy() { }

    public static boolean suppressesBackgroundAutostart(boolean requested) { return requested; }

    public static boolean mayReplaceIdleHandle(AtlasPregenerationState existing,
                                               AtlasPregenerationOptions replacement) {
        return existing == AtlasPregenerationState.IDLE
                && Objects.requireNonNull(replacement, "replacement").mode()
                == AtlasPregenerationMode.HEADLESS_PREWARM;
    }

    /** A completed selected future must be consumed before an interrupt checkpoint. */
    public static boolean mustConsumeCompletedFutureBeforeCheckpoint(boolean selectedFutureDone) {
        return selectedFutureDone;
    }
}
