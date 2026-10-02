package dev.ringworld.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.ringworld.client.mixin.SpriteContentsAccessor;
import dev.ringworld.world.RingMaterialColorAverage;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Render-thread palette resolution; workers receive immutable RGB values. */
final class RingWallMaterialColors {
    private static BakedModel missingModel;
    private static final Map<BlockState, Integer> COLORS = new IdentityHashMap<>();
    private static final Map<SpriteContents, Integer> SPRITES = new IdentityHashMap<>();

    static int color(BlockState state, Level world) {
        Minecraft client = Minecraft.getInstance();
        BakedModel current = client.getModelManager().getMissingModel();
        if (missingModel != current) {
            missingModel = current;
            COLORS.clear();
            SPRITES.clear();
        }
        int fallback = state.getMapColor(world, BlockPos.ZERO).col & 0xFFFFFF;
        return COLORS.computeIfAbsent(state, ignored -> {
            BakedModel model = client.getBlockRenderer().getBlockModel(state);
            if (model == null || model == missingModel) return fallback;
            var average = new RingMaterialColorAverage();
            for (Direction face : new Direction[]{Direction.NORTH, Direction.SOUTH, null}) {
                for (BakedQuad quad : model.getQuads(state, face, RandomSource.create(0))) {
                    if (quad.isTinted() || (quad.getDirection() != Direction.NORTH
                            && quad.getDirection() != Direction.SOUTH)) continue;
                    int rgb = SPRITES.computeIfAbsent(quad.getSprite().contents(), RingWallMaterialColors::spriteColor);
                    if (rgb >= 0) average.add(0xFF000000 | rgb);
                }
            }
            return average.rgbOr(fallback);
        });
    }

    private static int spriteColor(SpriteContents sprite) {
        NativeImage[] levels = ((SpriteContentsAccessor) (Object) sprite).ringworld$mipImages();
        if (levels == null || levels.length == 0) return -1;
        NativeImage image = levels[levels.length - 1];
        var average = new RingMaterialColorAverage();
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++)
                average.add(FastColor.ABGR32.fromArgb32(image.getPixelRGBA(x, y)));
        return average.rgbOr(-1);
    }
}
