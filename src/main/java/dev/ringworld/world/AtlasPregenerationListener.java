package dev.ringworld.world;

/** Receives loader-neutral pregeneration state updates from a platform adapter. */
@FunctionalInterface
public interface AtlasPregenerationListener {
    void onProgress(AtlasPregenerationProgress progress);

    default void onComplete(AtlasPregenerationResult result) { }

    default void onFailure(Throwable error) { }
}
