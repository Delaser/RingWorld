package dev.ringworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Boundary operations performed during chunk generation, never live ticks. */
public final class RingGenerationBoundary {
    public static final int RIM_THICKNESS = 5;
    /** Increment when rim placement/material semantics change. */
    public static final int RIM_STYLE_VERSION = 1;

    private RingGenerationBoundary() { }

    public static boolean isExterior(ChunkAccess chunk, RingGeometry geometry) {
        ChunkPos pos = chunk.getPos();
        return pos.getMaxBlockZ() < geometry.minWidthZ() || pos.getMinBlockZ() > geometry.maxWidthZ();
    }

    public static boolean containsRim(ChunkAccess chunk, RingGeometry geometry) {
        ChunkPos pos = chunk.getPos();
        return containsZ(pos, geometry.minWidthZ()) || containsZ(pos, geometry.maxWidthZ());
    }

    private static boolean containsZ(ChunkPos pos, int z) {
        return z >= pos.getMinBlockZ() && z <= pos.getMaxBlockZ();
    }

    /** Removes only cross-chunk feature spillover; noise generation is skipped entirely. */
    public static void clearExterior(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section.hasOnlyAir()) continue;
            int sectionBottomY = chunk.getMinY() + sectionIndex * 16;
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        if (section.getBlockState(localX, localY, localZ).isAir()) continue;
                        mutable.set(pos.getMinBlockX() + localX, sectionBottomY + localY, pos.getMinBlockZ() + localZ);
                        chunk.removeBlockEntity(mutable);
                        chunk.setBlockState(mutable, Blocks.AIR.defaultBlockState(), 0);
                    }
                }
            }
        }
    }

    /** Installs the finite, breakable rim after features have finished. */
    public static void installRim(ChunkAccess chunk, RingGeometry geometry, int wallHeightBlocks) {
        if (wallHeightBlocks <= 0 || !containsRim(chunk, geometry)) return;
        ChunkPos pos = chunk.getPos();
        int wallTopExclusive = Math.min(chunk.getMinY() + wallHeightBlocks,
                chunk.getMinY() + chunk.getHeight());
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int[] rimZs = {geometry.minWidthZ(), geometry.maxWidthZ()};
        for (int rimZ : rimZs) {
            if (!containsZ(pos, rimZ)) continue;
            int inwardDirection = rimZ == geometry.minWidthZ() ? 1 : -1;
            for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
                for (int y = chunk.getMinY(); y < wallTopExclusive; y++) {
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
    public static boolean migrateLegacyRim(LevelChunk chunk, RingGeometry geometry,
                                           int wallHeightBlocks) {
        if (!containsRim(chunk, geometry)) return false;
        ChunkPos pos = chunk.getPos();
        int wallTopExclusive = Math.min(chunk.getMinY() + wallHeightBlocks,
                chunk.getMinY() + chunk.getHeight());
        int chunkTopExclusive = chunk.getMinY() + chunk.getHeight();
        int[] rimZs = {geometry.minWidthZ(), geometry.maxWidthZ()};
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        boolean legacy = false;
        for (int rimZ : rimZs) {
            if (!containsZ(pos, rimZ)) continue;
            for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX() && !legacy; x++) {
                for (int y = wallTopExclusive; y < chunkTopExclusive; y++) {
                    if (chunk.getBlockState(mutable.set(x, y, rimZ)).is(Blocks.STONE_BRICKS)) {
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
            for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
                for (int depth = 0; depth < RIM_THICKNESS; depth++) {
                    int z = rimZ + inwardDirection * depth;
                    for (int y = chunk.getMinY(); y < chunkTopExclusive; y++) {
                        mutable.set(x, y, z);
                        if (y < wallTopExclusive) {
                            chunk.removeBlockEntity(mutable);
                            chunk.setBlockState(mutable, texturedRimBlock(x, y, z), 0);
                        } else if (chunk.getBlockState(mutable).is(Blocks.STONE_BRICKS)) {
                            chunk.removeBlockEntity(mutable);
                            chunk.setBlockState(mutable, Blocks.AIR.defaultBlockState(), 0);
                        }
                    }
                }
            }
        }
        chunk.markUnsaved();
        return true;
    }

    public static boolean isRimMaterial(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE);
    }

    private static net.minecraft.world.level.block.state.BlockState texturedRimBlock(int x, int y, int z) {
        long hash = (long)x * 0x9E3779B97F4A7C15L
                ^ (long)y * 0xC2B2AE3D27D4EB4FL
                ^ (long)z * 0x165667B19E3779F9L;
        hash ^= hash >>> 30;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 27;
        // Roughly 30% mossy blocks, stable across reloads and generation order.
        return Long.remainderUnsigned(hash, 10L) < 3L
                ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                : Blocks.COBBLESTONE.defaultBlockState();
    }
}
