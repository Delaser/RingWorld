package dev.ringworld.mixin;
import dev.ringworld.world.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
/** 26.3 routes terrain, climate, aquifer, ore and material functions here. */
@Mixin(RandomState.class)
abstract class ChunkNoiseSamplerMixin implements RingRandomStateAccess {
    @Unique private volatile RingNoiseRouter ringworld$mapper;
    @Override public synchronized void ringworld$configureNoise(RingGeometry geometry, int mapping,
            RingWorldGenerationSettings settings, long seed, int seaLevel, NoiseRouter router) {
        if (ringworld$mapper != null && ringworld$mapper.matches(geometry, mapping, settings, seed, seaLevel, router)) return;
        ringworld$mapper = new RingNoiseRouter(geometry, mapping, settings, seed, seaLevel, router);
    }
    @ModifyVariable(method = "getSampler", at = @At("HEAD"), argsOnly = true)
    private DensityFunction ringworld$periodicFunction(DensityFunction function) {
        RingNoiseRouter mapper = ringworld$mapper;
        return mapper == null ? function : mapper.rewrite(function);
    }
}
