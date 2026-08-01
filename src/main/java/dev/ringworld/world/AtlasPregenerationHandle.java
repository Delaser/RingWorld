package dev.ringworld.world;

import java.util.concurrent.CompletionStage;

/** Loader-neutral control and observation contract for one pregeneration job. */
public interface AtlasPregenerationHandle {
    AtlasPregenerationProgress progress();

    void pause();

    void resume();

    void cancel();

    CompletionStage<AtlasPregenerationResult> completion();
}
