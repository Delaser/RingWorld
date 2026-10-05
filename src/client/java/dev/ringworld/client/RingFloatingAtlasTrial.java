package dev.ringworld.client;

import dev.ringworld.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

/** One-shot integrated-world data capture. No multiplayer/cache format claim. */
public final class RingFloatingAtlasTrial {
    private static volatile Capture capture;
    private record Capture(long worldHash, List<RingFloatingSurface> layers) { }
    private RingFloatingAtlasTrial() { }
    public static void clear() { capture = null; }
    static void publish(long hash, List<RingFloatingSurface> layers) {
        capture = new Capture(hash, List.copyOf(layers));
    }
    public static RingTerrainAtlas snapshot(RingTerrainAtlas atlas) {
        var result = atlas.snapshot();
        var current = capture;
        if (current != null && current.worldHash() == atlas.worldHash()) {
            result.applyFloatingTrial(current.layers());
            if (Boolean.getBoolean("ringworld.floatingAtlasIgnore")) result.omitFloatingTrialGeometry();
        }
        return result;
    }
    static void captureChunk(RingIntegratedCaptureControl.Context context, int chunkX, int chunkZ,
                             List<RingFloatingSurface> layers) {
        var world = context.world();
        var chunk = world.getChunk(chunkX, chunkZ);
        var pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int bx = chunkX*16+x, bz = chunkZ*16+z;
            int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            pos.set(bx, top, bz);
            var state = chunk.getBlockState(pos);
            if (state.isAir() || state.is(BlockTags.LEAVES)
                    || !state.getFluidState().isEmpty()) continue;
            boolean ignore = Boolean.getBoolean("ringworld.floatingAtlasIgnore");
            if (ignore && naturalMaterial(state)) continue;
            int bottom = top;
            while (bottom > world.getMinY() && top-bottom < 64
                    && !chunk.getBlockState(pos.set(bx,bottom-1,bz)).isAir()) bottom--;
            if (top-bottom >= 64 || bottom <= world.getMinY()) continue;
            if (ignore && naturalMaterial(chunk.getBlockState(pos.set(bx,bottom,bz)))) continue;
            int ground = bottom-1;
            while (ground >= world.getMinY() && chunk.getBlockState(pos.set(bx,ground,bz)).isAir()) ground--;
            if (ground < world.getMinY() || bottom-ground-1 < 8) continue;
            var groundState = chunk.getBlockState(pos.set(bx,ground,bz));
            int color = dev.ringworld.server.RingAtlasPregenerationService.surfaceColor(world,pos,groundState);
            int water = groundState.getFluidState().is(FluidTags.WATER) ? 15 : 0;
            layers.add(new RingFloatingSurface(bx,bz,bottom,top+1,ground+1,color,
                    dev.ringworld.server.RingAtlasPregenerationService.surfaceColor(world,pos.set(bx,top,bz),state),water));
        }
    }

    /** Conservative trial filter: do not remove natural cave roofs, terrain or tree trunks. */
    private static boolean naturalMaterial(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.LOGS)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE);
    }
}
