package dev.ringworld.mixin;

import dev.ringworld.world.RingNoiseSamplingContext;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Installs the ring router in the sampler that owns final terrain density. */
@Mixin(NoiseChunk.class)
abstract class ChunkNoiseSamplerMixin {
    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/RandomState;router()Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private NoiseRouter ringworld$usePeriodicRouter(RandomState noiseConfig) {
        NoiseRouter override = RingNoiseSamplingContext.currentRouter();
        return override != null ? override : noiseConfig.router();
    }
}
