package dev.ringworld.client;

import dev.ringworld.client.render.RingSurfaceTextureRenderer;

/** Loader-neutral teardown for all state owned by one RingWorld client session. */
public final class RingWorldClientSession {
    private RingWorldClientSession() { }

    public static void clear() {
        RingSurfaceTextureRenderer.clear();
        AtlasPregenerationClientState.clear();
        ClientRingState.clear();
    }

    public static boolean isCleared() {
        return RingSurfaceTextureRenderer.sessionCleared()
                && AtlasPregenerationClientState.sessionCleared()
                && ClientRingState.sessionCleared();
    }
}
