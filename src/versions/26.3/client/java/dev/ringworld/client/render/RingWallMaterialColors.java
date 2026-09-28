package dev.ringworld.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.ringworld.client.mixin.SpriteContentsAccessor;
import dev.ringworld.world.RingMaterialColorAverage;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Small render-thread palette cache; workers receive only the resulting RGB array. */
final class RingWallMaterialColors {
    private static BlockStateModelSet models;
    private static final Map<BlockState, Integer> COLORS = new IdentityHashMap<>();
    private static final Map<SpriteContents, Integer> SPRITES = new IdentityHashMap<>();

    static int color(BlockState state, Level world) {
        Minecraft client = Minecraft.getInstance();
        BlockStateModelSet current = client.getModelManager().getBlockStateModelSet();
        // The model set is replaced on resource reload, even when the manager survives.
        if (models != current) {
            models = current;
            COLORS.clear();
            SPRITES.clear();
        }
        int fallback = state.getMapColor(world, BlockPos.ZERO).col & 0xFFFFFF;
        if (current == null) return fallback;
        return COLORS.computeIfAbsent(state, ignored -> resolve(state, fallback));
    }

    private static int resolve(BlockState state, int fallback) {
        var model = models.get(state);
        if (model == null || model == models.missingModel()) return fallback;
        var average = new RingMaterialColorAverage();
        var parts = new ArrayList<BlockStateModelPart>();
        model.collectParts(RandomSource.create(0), parts);
        for (var part : parts) {
            for (Direction face : new Direction[]{Direction.NORTH, Direction.SOUTH}) {
                for (var quad : part.getQuads(face)) addSide(average, quad);
            }
            for (var quad : part.getQuads(null)) addSide(average, quad);
        }
        return average.rgbOr(fallback);
    }

    private static void addSide(RingMaterialColorAverage average, BakedQuad quad) {
        if (quad.direction() != Direction.NORTH && quad.direction() != Direction.SOUTH) return;
        // Unknown biome tint cannot be reconstructed from a palette-only input.
        if (quad.materialInfo().isTinted()) return;
        int rgb = SPRITES.computeIfAbsent(quad.materialInfo().sprite().contents(),
                RingWallMaterialColors::spriteColor);
        if (rgb >= 0) average.add(0xFF000000 | rgb);
    }

    private static int spriteColor(SpriteContents sprite) {
        NativeImage[] levels = ((SpriteContentsAccessor)(Object)sprite).ringworld$mipImages();
        if (levels == null || levels.length == 0) return -1;
        // Use Minecraft's filtered mip pixels, retaining its colour filtering.
        // Animated materials contribute all frames without reading a GPU texture.
        NativeImage image = levels[levels.length - 1];
        var average = new RingMaterialColorAverage();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) average.add(image.getPixel(x, y));
        }
        return average.rgbOr(-1);
    }
}
