package dev.ringworld.world;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.function.Consumer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;

/** A vanilla-shaped view window whose X axis is a finite periodic graph. */
public record RingChunkFilter(ChunkPos center, int logicalCenterX,
                              int viewDistance, int circumferenceChunks,
                              int minChunkZ, int maxChunkZ) implements ChunkTrackingView {
    public RingChunkFilter(ChunkPos logicalCenter, int viewDistance, RingGeometry geometry) {
        this(logicalCenter, viewDistance, geometry.circumferenceChunks(),
                geometry.minChunkZ(), geometry.maxChunkZ());
    }

    public RingChunkFilter(ChunkPos logicalCenter, int viewDistance, int circumferenceChunks,
                           int minChunkZ, int maxChunkZ) {
        this(new ChunkPos(Math.floorMod(logicalCenter.x, circumferenceChunks), logicalCenter.z),
                logicalCenter.x, viewDistance, circumferenceChunks, minChunkZ, maxChunkZ);
    }

    public RingChunkFilter {
        if (circumferenceChunks <= 0) throw new IllegalArgumentException("circumferenceChunks must be positive");
        if (minChunkZ > maxChunkZ) throw new IllegalArgumentException("invalid width chunk bounds");
        center = new ChunkPos(Math.floorMod(center.x, circumferenceChunks), center.z);
    }

    @Override
    public boolean contains(int x, int z, boolean includeEdge) {
        return isWithinRingDistance(circumferenceChunks, center.x, center.z,
                viewDistance, minChunkZ, maxChunkZ, x, z, includeEdge);
    }

    static boolean isWithinRingDistance(int circumferenceChunks, int centerX, int centerZ,
                                        int viewDistance, int minChunkZ, int maxChunkZ,
                                        int x, int z, boolean includeEdge) {
        if (z < minChunkZ || z > maxChunkZ) return false;
        return RingChunkTopology.isWithinVanillaDistance(
                circumferenceChunks, centerX, centerZ, viewDistance, x, z, includeEdge);
    }

    @Override
    public void forEach(Consumer<ChunkPos> consumer) {
        LongSet emitted = new LongOpenHashSet();
        int extent = viewDistance + 1;
        for (int dx = -extent; dx <= extent; dx++) {
            int x = Math.floorMod(center.x + dx, circumferenceChunks);
            int firstZ = Math.max(minChunkZ, center.z - extent);
            int lastZ = Math.min(maxChunkZ, center.z + extent);
            for (int z = firstZ; z <= lastZ; z++) {
                if (!contains(x, z)) continue;
                long packed = ChunkPos.asLong(x, z);
                if (emitted.add(packed)) consumer.accept(new ChunkPos(packed));
            }
        }
    }

    /** Diffs any two filters without relying on vanilla's flat-cylinder fast path. */
    public static void forEachChanged(ChunkTrackingView oldFilter, ChunkTrackingView newFilter,
                                      Consumer<ChunkPos> newlyIncluded, Consumer<ChunkPos> justRemoved) {
        if (oldFilter.equals(newFilter)) return;
        if (oldFilter instanceof RingChunkFilter oldRing && newFilter instanceof RingChunkFilter newRing) {
            if (requiresFullRekey(oldRing.center.x, newRing.center.x,
                    oldRing.viewDistance, newRing.viewDistance,
                    oldRing.circumferenceChunks, newRing.circumferenceChunks,
                    oldRing.minChunkZ, oldRing.maxChunkZ,
                    newRing.minChunkZ, newRing.maxChunkZ)) {
                // An intentional long teleport needs a full chart re-key.
                // A natural C-1 -> 0 seam step has periodic distance one and
                // must remain an incremental window update.
                oldFilter.forEach(justRemoved);
                newFilter.forEach(newlyIncluded);
                return;
            }
        }
        LongSet oldChunks = collect(oldFilter);
        LongSet newChunks = collect(newFilter);
        for (long packed : oldChunks) {
            if (!newChunks.contains(packed)) justRemoved.accept(new ChunkPos(packed));
        }
        for (long packed : newChunks) {
            if (!oldChunks.contains(packed)) newlyIncluded.accept(new ChunkPos(packed));
        }
    }

    static boolean requiresFullRekey(int oldCenterX, int newCenterX,
                                     int oldViewDistance, int newViewDistance,
                                     int oldCircumference, int newCircumference,
                                     int oldMinChunkZ, int oldMaxChunkZ,
                                     int newMinChunkZ, int newMaxChunkZ) {
        if (oldCircumference != newCircumference) return true;
        if (oldMinChunkZ != newMinChunkZ || oldMaxChunkZ != newMaxChunkZ) return true;
        int rawDistance = Math.abs(newCenterX - oldCenterX);
        int periodicDistance = Math.min(rawDistance, oldCircumference - rawDistance);
        int overlapDiameter = Math.max(oldViewDistance, newViewDistance) * 2 + 2;
        return periodicDistance > overlapDiameter;
    }

    private static LongSet collect(ChunkTrackingView filter) {
        LongSet chunks = new LongOpenHashSet();
        filter.forEach(pos -> chunks.add(pos.toLong()));
        return chunks;
    }
}
