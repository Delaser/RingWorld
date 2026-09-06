package dev.ringworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Runs only during decoration, touching the supplied chunk and no neighbours. */
public final class RingIndustrialElementPlacement {
    private RingIndustrialElementPlacement() { }
    @FunctionalInterface public interface TerrainHeight { int at(int canonicalX, int z); }

    public static void install(ChunkAccess chunk, RingGeometry geometry, int wallHeight,
                               RingWallStyle style, long seed, TerrainHeight terrain) {
        if (!RingIndustrialElements.enabled(style) || style.thicknessBlocks() < 2 || wallHeight <= 0) return;
        int top = RingGenerationBoundary.wallTopExclusive(chunk.getMinY(), chunk.getHeight(), wallHeight);
        var cp = chunk.getPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int side = 0; side < 2; side++) {
            int inward = side == 0 ? 1 : -1;
            int edge = side == 0 ? geometry.minWidthZ() : geometry.maxWidthZ();
            int face = edge + inward * (style.thicknessBlocks() - 1);
            int extentA = face - inward * Math.min(3, style.thicknessBlocks() - 1);
            int extentB = face + inward * 3;
            if (cp.getMaxBlockZ() < Math.min(extentA, extentB)
                    || cp.getMinBlockZ() > Math.max(extentA, extentB)) continue;
            int previousCenter = Integer.MIN_VALUE, base = 0;
            for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++) {
                var feature = RingIndustrialElements.feature(x, geometry.circumferenceBlocks(), seed, side);
                if (Math.abs(feature.offsetX()) > RingIndustrialElements.HALF_WIDTH) continue;
                if (feature.centerX() != previousCenter) {
                    // Same three deterministic noise-height queries in every participating chunk.
                    // Using the highest anchor prevents carving into an uphill slope.
                    base = Math.max(chunk.getMinY(), Math.max(terrain.at(feature.centerX(), face + inward * 4),
                            Math.max(terrain.at(Math.floorMod(feature.centerX() - 18, geometry.circumferenceBlocks()), face + inward * 4),
                                     terrain.at(Math.floorMod(feature.centerX() + 18, geometry.circumferenceBlocks()), face + inward * 4))));
                    previousCenter = feature.centerX();
                }
                int height = top - base;
                if (height < 24) continue;
                for (int y = base; y < Math.min(top, base + 33); y++) {
                    for (int relief = -Math.min(3, style.thicknessBlocks() - 1); relief <= 3; relief++) {
                        int z = face + inward * relief;
                        if (z < cp.getMinBlockZ() || z > cp.getMaxBlockZ()) continue;
                        int roll = RingIndustrialElements.roll(feature, y - base, height, relief, style.thicknessBlocks());
                        if (roll == RingIndustrialElements.UNCHANGED) continue;
                        int depth = Math.max(0, Math.min(style.thicknessBlocks() - 1, style.thicknessBlocks() - 1 + relief));
                        if (!RingWallPattern.blockPresent(style, x, y, depth, top, geometry.circumferenceBlocks(), seed)) continue;
                        pos.set(x, y, z);
                        var existing = chunk.getBlockState(pos);
                        // Projections may occupy air only: preserve terrain, foliage, fluids and structures.
                        if (relief > 0 && !existing.isAir()) continue;
                        if (relief <= 0 && !RingGenerationBoundary.isRimMaterial(existing)) continue;
                        var block = roll == RingIndustrialElements.AIR ? Blocks.AIR.defaultBlockState()
                                : RingGenerationBoundary.styledRimBlockForRoll(style, roll);
                        chunk.setBlockState(pos, block, 0);
                    }
                }
            }
        }
    }
}
