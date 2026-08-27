package dev.ringworld.world;

import net.minecraft.world.level.levelgen.DensityFunction.FunctionContext;

/** 26.2 removes blending state from the density-function context. */
final class RingDensityContexts {
    private RingDensityContexts() { }
    static boolean isTransformed(FunctionContext context) { return context instanceof Transformed; }
    static FunctionContext transformed(FunctionContext source, int x, int z) {
        return new Transformed(x, source.blockY(), z);
    }
    private record Transformed(int blockX, int blockY, int blockZ) implements FunctionContext { }
}
