package dev.ringworld.server;

import dev.ringworld.world.RingAtlasColumnSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Shared by initial Atlas capture and dirty-cell recapture. Never loads neighbours. */
final class RingAtlasSurfaceSampler {
    private RingAtlasSurfaceSampler() { }

    static int surfaceY(LevelChunk chunk, int localX, int localZ) {
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
        var position = new BlockPos.MutableBlockPos(chunk.getPos().getMinBlockX() + localX,
                top, chunk.getPos().getMinBlockZ() + localZ);
        return RingAtlasColumnSurface.select(top, chunk.getMinY(), y -> {
            position.setY(y);
            return material(chunk.getBlockState(position));
        });
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
