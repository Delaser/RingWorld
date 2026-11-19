package dev.ringworld.client.render;

import dev.ringworld.world.RingGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Keeps the local Atlas visible behind a cold destination until real terrain can cover it. */
final class RingTeleportCoverage {
    private Vec3 previous;
    private boolean waiting;
    private long nextCheck, readySince;
    private static final long CHECK_NS = 100_000_000L;
    private static final long FADE_NS = 350_000_000L;

    float opacity(Minecraft client, RingGeometry geometry, Vec3 camera) {
        long now = System.nanoTime();
        if (previous == null || previous.distanceToSqr(camera) >= 16 * 16) {
            waiting = true;
            readySince = 0;
            nextCheck = now;
        }
        previous = camera;
        if (!waiting || client.level == null) return 0;
        if (now >= nextCheck) {
            nextCheck = now + CHECK_NS;
            if (!client.levelRenderer.hasRenderedAllSections() || !chunksArrived(client, geometry, camera)) {
                readySince = 0;
            } else if (readySince == 0) {
                readySince = now;
            }
        }
        if (readySince == 0) return 1;
        float fade = (float)(now - readySince) / FADE_NS;
        if (fade >= 1) waiting = false;
        return Math.max(0, 1 - fade);
    }

    private static boolean chunksArrived(Minecraft client, RingGeometry geometry, Vec3 camera) {
        int x = (int)Math.floor(camera.x) >> 4, z = (int)Math.floor(camera.z) >> 4;
        // At exactly half a lap the two chart images address one canonical chunk.
        // A server need only send one image, so never wait for both antipodes.
        int radius = Math.min(client.options.getEffectiveRenderDistance(), (geometry.circumferenceChunks() - 1) / 2);
        var chunks = client.level.getChunkSource();
        for (int dz = Math.max(-radius, geometry.minChunkZ() - z);
                dz <= Math.min(radius, geometry.maxChunkZ() - z); dz++) {
            int extent = (int)Math.sqrt(radius * radius - dz * dz);
            for (int dx = -extent; dx <= extent; dx++) {
                if (!chunks.hasChunk(x + dx, z + dz)) return false;
            }
        }
        return true;
    }

    void clear() {
        previous = null;
        waiting = false;
    }
}
