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
    private static final ThreadLocal<LoadContext> ACTIVE_CONTEXT = new ThreadLocal<>();

    private RingBlockEntityLoadContext() { }

    public static void withGeometry(RingGeometry geometry, Runnable action) {
        withGeometry(geometry, null, action);
    }

    public static void withGeometry(RingGeometry geometry, ChunkPos owner, Runnable action) {
        LoadContext previous = ACTIVE_CONTEXT.get();
        ACTIVE_CONTEXT.set(new LoadContext(geometry, owner));
        try {
            action.run();
        } finally {
            if (previous == null) ACTIVE_CONTEXT.remove();
            else ACTIVE_CONTEXT.set(previous);
        }
    }

    public static BlockPos restoreSavedAlias(ChunkPos owner, CompoundTag tag, BlockPos vanillaPosition) {
        LoadContext context = ACTIVE_CONTEXT.get();
        return context == null
                ? vanillaPosition
                : restoreSavedAlias(context.geometry(), owner, tag, vanillaPosition);
    }

    public static BlockPos restoreSavedAlias(CompoundTag tag, BlockPos vanillaPosition) {
        LoadContext context = ACTIVE_CONTEXT.get();
        return context == null || context.owner() == null
                ? vanillaPosition
                : restoreSavedAlias(context.geometry(), context.owner(), tag, vanillaPosition);
    }

    public static boolean isActive() {
        return ACTIVE_CONTEXT.get() != null;
    }

    /**
     * Returns the geometry owned by the current thread's active load callback,
     * or {@code null} outside {@link #withGeometry}.
     *
     * <p>This narrow accessor exists for ownership repair during block-entity
     * deserialization before {@code BlockEntity.level} is attached. The value
     * is valid only while the current thread remains inside that callback and
     * must never be cached beyond it.</p>
     */
    public static RingGeometry activeGeometryOrNull() {
        LoadContext context = ACTIVE_CONTEXT.get();
        return context == null ? null : context.geometry();
    }

    static BlockPos restoreSavedAlias(RingGeometry geometry, ChunkPos owner,
                                      CompoundTag tag, BlockPos vanillaPosition) {
        return restoreSavedAlias(geometry, owner.z, tag, vanillaPosition);
    }

    static BlockPos restoreSavedAlias(RingGeometry geometry, int ownerChunkZ,
                                      CompoundTag tag, BlockPos vanillaPosition) {
        int rawX = tag.contains("x") ? tag.getInt("x") : 0;
        int rawY = tag.contains("y") ? tag.getInt("y") : 0;
        int rawZ = tag.contains("z") ? tag.getInt("z") : 0;
        if (rawX >= 0 && rawX < geometry.circumferenceBlocks()) return vanillaPosition;
        if (SectionPos.blockToSectionCoord(rawZ) != ownerChunkZ) return vanillaPosition;
        if (rawY != vanillaPosition.getY() || rawZ != vanillaPosition.getZ()) return vanillaPosition;
        if (geometry.wrapBlockX(rawX) != vanillaPosition.getX()) return vanillaPosition;
        return new BlockPos(rawX, rawY, rawZ);
    }

    private record LoadContext(RingGeometry geometry, ChunkPos owner) { }
}
