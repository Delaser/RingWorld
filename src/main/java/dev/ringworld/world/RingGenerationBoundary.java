package dev.ringworld.world;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/** Boundary operations performed during chunk generation, never live ticks. */
public final class RingGenerationBoundary {
    public static final int RIM_THICKNESS = 5;
    /** Increment when rim placement/material semantics change. */
    public static final int RIM_STYLE_VERSION = 1;

    private RingGenerationBoundary() { }

    public static boolean isExterior(Chunk chunk, RingGeometry geometry) {
        ChunkPos pos = chunk.getPos();
        return pos.getEndZ() < geometry.minWidthZ() || pos.getStartZ() > geometry.maxWidthZ();
    }

    public static boolean containsRim(Chunk chunk, RingGeometry geometry) {
        ChunkPos pos = chunk.getPos();
        return containsZ(pos, geometry.minWidthZ()) || containsZ(pos, geometry.maxWidthZ());
    }

    private static boolean containsZ(ChunkPos pos, int z) {
        return z >= pos.getStartZ() && z <= pos.getEndZ();
    }

    /** Removes only cross-chunk feature spillover; noise generation is skipped entirely. */
    public static void clearExterior(Chunk chunk) {
        ChunkPos pos = chunk.getPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        ChunkSection[] sections = chunk.getSectionArray();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section.isEmpty()) continue;
            int sectionBottomY = chunk.getBottomY() + sectionIndex * 16;
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        if (section.getBlockState(localX, localY, localZ).isAir()) continue;
                        mutable.set(pos.getStartX() + localX, sectionBottomY + localY, pos.getStartZ() + localZ);
                        chunk.removeBlockEntity(mutable);
                        chunk.setBlockState(mutable, Blocks.AIR.getDefaultState(), 0);
                    }
                }
            }
        }
    }

    /** Installs the finite, breakable rim after features have finished. */
    public static void installRim(Chunk chunk, RingGeometry geometry, int wallHeightBlocks) {
        if (wallHeightBlocks <= 0 || !containsRim(chunk, geometry)) return;
        ChunkPos pos = chunk.getPos();
        int wallTopExclusive = Math.min(chunk.getBottomY() + wallHeightBlocks,
                chunk.getBottomY() + chunk.getHeight());
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int[] rimZs = {geometry.minWidthZ(), geometry.maxWidthZ()};
        for (int rimZ : rimZs) {
            if (!containsZ(pos, rimZ)) continue;
            int inwardDirection = rimZ == geometry.minWidthZ() ? 1 : -1;
            for (int x = pos.getStartX(); x <= pos.getEndX(); x++) {
                for (int y = chunk.getBottomY(); y < wallTopExclusive; y++) {
                    for (int depth = 0; depth < RIM_THICKNESS; depth++) {
                        int z = rimZ + inwardDirection * depth;
                        mutable.set(x, y, z);
                        chunk.removeBlockEntity(mutable);
                        chunk.setBlockState(mutable, texturedRimBlock(x, y, z), 0);
                    }
                }
            }
        }
    }

    /**
     * One-time, content-detected migration for already generated stone-brick
     * rims. Once converted there are no legacy blocks above the new top, so a
     * deliberately broken textured wall is not rebuilt on later chunk loads.
     */
    public static boolean migrateLegacyRim(WorldChunk chunk, RingGeometry geometry,
                                           int wallHeightBlocks) {
        if (!containsRim(chunk, geometry)) return false;
        ChunkPos pos = chunk.getPos();
        int wallTopExclusive = Math.min(chunk.getBottomY() + wallHeightBlocks,
                chunk.getBottomY() + chunk.getHeight());
        int chunkTopExclusive = chunk.getBottomY() + chunk.getHeight();
        int[] rimZs = {geometry.minWidthZ(), geometry.maxWidthZ()};
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        boolean legacy = false;
        for (int rimZ : rimZs) {
            if (!containsZ(pos, rimZ)) continue;
            for (int x = pos.getStartX(); x <= pos.getEndX() && !legacy; x++) {
                for (int y = wallTopExclusive; y < chunkTopExclusive; y++) {
                    if (chunk.getBlockState(mutable.set(x, y, rimZ)).isOf(Blocks.STONE_BRICKS)) {
                        legacy = true;
                        break;
                    }
                }
            }
        }
        if (!legacy) return false;

        for (int rimZ : rimZs) {
            if (!containsZ(pos, rimZ)) continue;
            int inwardDirection = rimZ == geometry.minWidthZ() ? 1 : -1;
            for (int x = pos.getStartX(); x <= pos.getEndX(); x++) {
                for (int depth = 0; depth < RIM_THICKNESS; depth++) {
                    int z = rimZ + inwardDirection * depth;
                    for (int y = chunk.getBottomY(); y < chunkTopExclusive; y++) {
                        mutable.set(x, y, z);
                        if (y < wallTopExclusive) {
                            chunk.removeBlockEntity(mutable);
                            chunk.setBlockState(mutable, texturedRimBlock(x, y, z), 0);
                        } else if (chunk.getBlockState(mutable).isOf(Blocks.STONE_BRICKS)) {
                            chunk.removeBlockEntity(mutable);
                            chunk.setBlockState(mutable, Blocks.AIR.getDefaultState(), 0);
                        }
                    }
                }
            }
        }
        chunk.markNeedsSaving();
        return true;
    }

    public static boolean isRimMaterial(net.minecraft.block.BlockState state) {
        return state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.MOSSY_COBBLESTONE);
    }

    private static net.minecraft.block.BlockState texturedRimBlock(int x, int y, int z) {
        long hash = (long)x * 0x9E3779B97F4A7C15L
                ^ (long)y * 0xC2B2AE3D27D4EB4FL
                ^ (long)z * 0x165667B19E3779F9L;
        hash ^= hash >>> 30;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 27;
        // Roughly 30% mossy blocks, stable across reloads and generation order.
        return Long.remainderUnsigned(hash, 10L) < 3L
                ? Blocks.MOSSY_COBBLESTONE.getDefaultState()
                : Blocks.COBBLESTONE.getDefaultState();
    }
}
