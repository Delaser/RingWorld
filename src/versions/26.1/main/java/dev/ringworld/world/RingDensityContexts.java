package dev.ringworld.world;

import net.minecraft.world.level.levelgen.DensityFunction.FunctionContext;
import net.minecraft.world.level.levelgen.blending.Blender;

/** 26.1 carries legacy blending state in the density context. */
final class RingDensityContexts {
    private RingDensityContexts() { }
    static boolean isTransformed(FunctionContext context) { return context instanceof Transformed; }
    static FunctionContext transformed(FunctionContext source, int x, int z) {
        return new Transformed(x, source.blockY(), z, source.getBlender());
    }
    private record Transformed(int blockX, int blockY, int blockZ, Blender getBlender)
            implements FunctionContext { }
}
