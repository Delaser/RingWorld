package dev.ringworld.client;

import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.world.*;

/** Client-thread-owned, session-local LOD choice. No server or saved settings are mutated. */
public final class RingClientLodTuning {
    private static RingLodQuality quality = RingLodQuality.MEDIUM;
    private RingClientLodTuning() { }
    public static RingLodQuality quality() { return quality; }
    public static String select(RingLodQuality value) {
        if (value == null) value = RingLodQuality.MEDIUM; // /ringworld lod reset
        if (quality == value) return summary();
        quality = value;
        RingSurfaceTextureRenderer.clear();
        return summary();
    }
    public static void clearSession() { quality = RingLodQuality.MEDIUM; }
    public static RingRenderProfile profile(RingGeometry geometry, double distance) {
        int limit = RingMinecraftClientAccess.maxTextureSize();
        return quality.profile(geometry, distance, limit);
    }
    public static String summary() {
        var atlas = ClientRingState.terrainAtlas();
        return "Ring LOD: " + quality.command()
                + " (source target " + quality.sampleStep() + ", mesh " + quality.meshStep() + " blocks)"
                + (atlas == null ? "" : "; server source " + atlas.sampleStep() + " blocks")
                + (atlas != null && atlas.sampleStep() > quality.sampleStep()
                    ? "; finer source detail unavailable" : "");
    }
}
