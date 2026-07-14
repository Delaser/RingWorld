package dev.ringworld.mixin;

import dev.ringworld.world.RingNoiseSamplingContext;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.noise.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Installs the ring router in the sampler that owns final terrain density. */
@Mixin(ChunkNoiseSampler.class)
abstract class ChunkNoiseSamplerMixin {
    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/noise/NoiseConfig;getNoiseRouter()Lnet/minecraft/world/gen/noise/NoiseRouter;"))
    private NoiseRouter ringworld$usePeriodicRouter(NoiseConfig noiseConfig) {
        NoiseRouter override = RingNoiseSamplingContext.currentRouter();
        return override != null ? override : noiseConfig.getNoiseRouter();
    }
}
