package dev.ringworld.server;

import dev.ringworld.world.RingGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

/**
 * Preserves a saved periodic-X alias long enough for {@code LevelChunk} to
 * detect an old canonical/alias block-entity collision without losing NBT.
 */
public final class RingBlockEntityLoadContext {
    private static final ThreadLocal<RingGeometry> ACTIVE_GEOMETRY = new ThreadLocal<>();

    private RingBlockEntityLoadContext() { }

    public static void withGeometry(RingGeometry geometry, Runnable action) {
        RingGeometry previous = ACTIVE_GEOMETRY.get();
        ACTIVE_GEOMETRY.set(geometry);
        try {
            action.run();
        } finally {
            if (previous == null) ACTIVE_GEOMETRY.remove();
            else ACTIVE_GEOMETRY.set(previous);
        }
    }

    public static BlockPos restoreSavedAlias(ChunkPos owner, CompoundTag tag, BlockPos vanillaPosition) {
        RingGeometry geometry = ACTIVE_GEOMETRY.get();
        return geometry == null
                ? vanillaPosition
                : restoreSavedAlias(geometry, owner, tag, vanillaPosition);
    }

    public static boolean isActive() {
        return ACTIVE_GEOMETRY.get() != null;
    }

    static BlockPos restoreSavedAlias(RingGeometry geometry, ChunkPos owner,
                                      CompoundTag tag, BlockPos vanillaPosition) {
        return restoreSavedAlias(geometry, owner.z(), tag, vanillaPosition);
    }

    static BlockPos restoreSavedAlias(RingGeometry geometry, int ownerChunkZ,
                                      CompoundTag tag, BlockPos vanillaPosition) {
        int rawX = tag.getIntOr("x", 0);
        int rawY = tag.getIntOr("y", 0);
        int rawZ = tag.getIntOr("z", 0);
        if (rawX >= 0 && rawX < geometry.circumferenceBlocks()) return vanillaPosition;
        if (SectionPos.blockToSectionCoord(rawZ) != ownerChunkZ) return vanillaPosition;
        if (rawY != vanillaPosition.getY() || rawZ != vanillaPosition.getZ()) return vanillaPosition;
        if (geometry.wrapBlockX(rawX) != vanillaPosition.getX()) return vanillaPosition;
        return new BlockPos(rawX, rawY, rawZ);
    }
}
