package dev.ringworld.client.compat;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingTerrainAtlas;

/** Keeps optional DH API types out of the native renderer and dedicated server. */
public final class DhAtlasSupport {
    private static boolean enabled;

    private DhAtlasSupport() {}

    public static void initialize(boolean dhLoaded) {
        enabled = dhLoaded && DhTerrainSource.register();
    }

    public static RingTerrainAtlas select() {
        var atlas = ClientRingState.terrainAtlas();
        return enabled ? DhTerrainSource.select(atlas) : atlas;
    }

    public static RingTerrainAtlas displayedSource() {
        var atlas = ClientRingState.terrainAtlas();
        return enabled ? DhTerrainSource.displayedSource(atlas) : atlas;
    }

    public static int revision() {
        int revision = ClientRingState.terrainAtlasRevision();
        return enabled ? DhTerrainSource.revision(revision) : revision;
    }

    public static void clear() {
        if (enabled) DhTerrainSource.clear();
    }
}
