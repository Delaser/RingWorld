package dev.ringworld.server;

import dev.ringworld.world.RingAtlasColumnSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Shared by initial Atlas capture and dirty-cell recapture. Never loads neighbours. */
final class RingAtlasSurfaceSampler {
    private RingAtlasSurfaceSampler() { }

    static int surfaceY(ChunkAccess chunk, int localX, int localZ) {
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
        var position = new BlockPos.MutableBlockPos(chunk.getPos().getMinBlockX() + localX,
                top, chunk.getPos().getMinBlockZ() + localZ);
        return RingAtlasColumnSurface.select(top, chunk.getMinY(), y -> {
            position.setY(y);
            return material(chunk.getBlockState(position));
        });
    }

    /** One sample per 2x2 Medium footprint, using only this already loaded chunk. */
    static int layer(ChunkAccess chunk, BlockPos selected) {
        if ((selected.getX() & 1) == 0 || (selected.getZ() & 1) == 0) return 0;
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, selected.getX() & 15, selected.getZ() & 15);
        if (top < chunk.getMinY() || top - selected.getY() > 47) return 0;
        var pos = selected.mutable();
        pos.setY(top);
        var roof = chunk.getBlockState(pos);
        if (!roof.is(BlockTags.LEAVES) && !roof.is(BlockTags.LOGS)
                && material(roof) != RingAtlasColumnSurface.Material.BUILT) return 0;
        int y = top;
        int minimum = chunk.getMinY();
        while (y >= minimum && top - y < 17 && !chunk.getBlockState(pos.setY(y)).isAir()) y--;
        int thickness = top - y;
        if (thickness < 1 || thickness > 16) return 0;
        int firstAir = y;
        while (y >= minimum && firstAir - y < 32 && chunk.getBlockState(pos.setY(y)).isAir()) y--;
        int gap = firstAir - y;
        if (y < minimum || gap < 2 || gap > 31 || (selected.getY() != top && selected.getY() != y)) return 0;
        boolean storedFloor = selected.getY() == y;
        var other = storedFloor ? roof : chunk.getBlockState(pos);
        int palette = other.getMapColor(chunk, pos.setY(storedFloor ? top : y)).id;
        return gap | ((thickness - 1) << 5) | (palette << 9) | (storedFloor ? 0x8000 : 0);
    }

    /** Cheap invalidation check only: the full scan stays in the queued writer. */
    static boolean hasManufacturedTop(LevelChunk chunk, int localX, int localZ) {
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
        if (top < chunk.getMinY()) return false;
        return material(chunk.getBlockState(new BlockPos(chunk.getPos().getMinBlockX() + localX,
                top, chunk.getPos().getMinBlockZ() + localZ))) == RingAtlasColumnSurface.Material.BUILT;
    }

    private static RingAtlasColumnSurface.Material material(BlockState state) {
        if (state.isAir()) return RingAtlasColumnSurface.Material.AIR;
        if (!state.getFluidState().isEmpty()) return RingAtlasColumnSurface.Material.TERRAIN;
        // Positive manufactured-material selection protects natural roofs, vegetation,
        // ores and unknown modded materials. This is deliberately not connectivity analysis.
        if (state.is(BlockTags.PLANKS) || state.is(BlockTags.WOOL)
                || state.is(BlockTags.STONE_BRICKS) || state.is(BlockTags.SLABS)
                || state.is(BlockTags.STAIRS) || state.is(BlockTags.WALLS))
            return RingAtlasColumnSurface.Material.BUILT;
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals("minecraft")) return RingAtlasColumnSurface.Material.TERRAIN;
        String name = id.getPath();
        boolean built = name.endsWith("_concrete") || name.endsWith("_stained_glass")
                || switch (name) {
                    case "bricks", "quartz_block", "quartz_pillar", "quartz_bricks",
                         "chiseled_quartz_block", "smooth_quartz", "glass", "tinted_glass",
                         "iron_block", "gold_block", "diamond_block", "emerald_block",
                         "netherite_block", "copper_block", "cut_copper", "sea_lantern",
                         "polished_deepslate", "deepslate_bricks", "deepslate_tiles",
                         "cracked_deepslate_bricks", "cracked_deepslate_tiles", "polished_andesite",
                         "polished_diorite", "polished_granite", "polished_blackstone",
                         "polished_blackstone_bricks", "chiseled_polished_blackstone" -> true;
                    default -> false;
                };
        return built ? RingAtlasColumnSurface.Material.BUILT : RingAtlasColumnSurface.Material.TERRAIN;
    }
}
