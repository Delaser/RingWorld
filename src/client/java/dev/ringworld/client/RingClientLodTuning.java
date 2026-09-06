package dev.ringworld.client;

import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.world.*;

/** Client-thread-owned, session-local LOD choice. No server or saved settings are mutated. */
public final class RingClientLodTuning {
    private static RingLodQuality quality;
    private RingClientLodTuning() { }
    public static RingLodQuality quality() { return quality; }
    public static String select(RingLodQuality value) {
        quality = value;
        RingSurfaceTextureRenderer.clear();
        return summary();
    }
    public static void clearSession() { quality = null; }
    public static RingRenderProfile profile(RingGeometry geometry, double distance, RingAtlasFidelity fallback) {
        int limit = RingMinecraftClientAccess.maxTextureSize();
        return quality == null ? RingRenderProfile.create(geometry, distance,
                Math.min(fallback.maxTextureColumns(), limit), Math.min(fallback.maxTextureRows(), limit), fallback.meshStepBlocks())
                : quality.profile(geometry, distance, limit);
    }
    public static String summary() {
        var atlas = ClientRingState.terrainAtlas();
        return "Ring LOD: " + (quality == null ? "server default" : quality.command()
                + " (source target " + quality.sampleStep() + ", mesh " + quality.meshStep() + " blocks)")
                + (atlas == null ? "" : "; server source " + atlas.sampleStep() + " blocks")
                + (quality != null && atlas != null && atlas.sampleStep() > quality.sampleStep()
                    ? "; finer source detail unavailable" : "");
    }
}
