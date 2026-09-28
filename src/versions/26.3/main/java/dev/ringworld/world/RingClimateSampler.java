package dev.ringworld.world;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
/** The configured RandomState owns all 26.3 periodic compiled samplers. */
public final class RingClimateSampler {
    private RingClimateSampler() { }
    public static Climate.Sampler create(RandomState state) {
        return state.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
    }
}
