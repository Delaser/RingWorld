package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingNoiseRouter;
import dev.ringworld.world.RingNoiseSamplingContext;
import dev.ringworld.world.RingWorldGeneratorAccess;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.AquiferSampler;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.noise.NoiseRouter;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.biome.source.BiomeAccess;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

/** Supplies a periodic density router only to the Overworld's registered generator. */
@Mixin(NoiseChunkGenerator.class)
abstract class NoiseChunkGeneratorMixin implements RingWorldGeneratorAccess {
    @Unique private @Nullable RingGeometry ringworld$geometry;
    @Unique private int ringworld$wallHeight;
    @Unique private @Nullable NoiseConfig ringworld$cachedNoiseConfig;
    @Unique private @Nullable NoiseRouter ringworld$cachedRouter;

    @Override
    public void ringworld$setGeometry(RingGeometry geometry) {
        this.ringworld$geometry = geometry;
        this.ringworld$cachedNoiseConfig = null;
        this.ringworld$cachedRouter = null;
    }

    @Override
    public @Nullable RingGeometry ringworld$getGeometry() {
        return ringworld$geometry;
    }

    @Override
    public void ringworld$setWallHeight(int wallHeightBlocks) {
        ringworld$wallHeight = wallHeightBlocks;
    }

    @Override
    public int ringworld$getWallHeight() {
        return ringworld$wallHeight;
    }

    @Inject(method = "populateNoise", at = @At("HEAD"), cancellable = true)
    private void ringworld$skipExteriorNoise(Blender blender, NoiseConfig noiseConfig,
                                             StructureAccessor structures, Chunk chunk,
                                             CallbackInfoReturnable<CompletableFuture<Chunk>> cir) {
        if (ringworld$geometry != null && RingGenerationBoundary.isExterior(chunk, ringworld$geometry)) {
            cir.setReturnValue(CompletableFuture.completedFuture(chunk));
        }
    }

    @Inject(method = "buildSurface(Lnet/minecraft/world/ChunkRegion;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;)V",
            at = @At("HEAD"), cancellable = true)
    private void ringworld$skipExteriorSurface(ChunkRegion region, StructureAccessor structures,
                                               NoiseConfig noiseConfig, Chunk chunk, CallbackInfo ci) {
        if (ringworld$geometry != null && RingGenerationBoundary.isExterior(chunk, ringworld$geometry)) ci.cancel();
    }

    @Inject(method = "carve", at = @At("HEAD"), cancellable = true)
    private void ringworld$skipExteriorCarvers(ChunkRegion region, long seed, NoiseConfig noiseConfig,
                                               BiomeAccess biomeAccess, StructureAccessor structures,
                                               Chunk chunk, CallbackInfo ci) {
        if (ringworld$geometry != null && RingGenerationBoundary.isExterior(chunk, ringworld$geometry)) ci.cancel();
    }

    @Redirect(
            method = "*",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/noise/NoiseConfig;getNoiseRouter()Lnet/minecraft/world/gen/noise/NoiseRouter;"))
    private NoiseRouter ringworld$periodicRouter(NoiseConfig noiseConfig) {
        NoiseRouter vanilla = noiseConfig.getNoiseRouter();
        if (ringworld$geometry == null) return vanilla;
        return ringworld$getOrCreatePeriodicRouter(noiseConfig, vanilla);
    }

    @Unique
    private NoiseRouter ringworld$getOrCreatePeriodicRouter(NoiseConfig noiseConfig, NoiseRouter vanilla) {
        if (ringworld$cachedNoiseConfig != noiseConfig || ringworld$cachedRouter == null) {
            ringworld$cachedNoiseConfig = noiseConfig;
            ringworld$cachedRouter = RingNoiseRouter.wrap(vanilla, ringworld$geometry);
        }
        return ringworld$cachedRouter;
    }

    /**
     * ChunkNoiseSampler, not NoiseChunkGenerator, fetches the router used for
     * final terrain density. Carry the Overworld override across that static
     * factory call so the sampler mixin can select it without affecting the
     * Nether or End, which may share the same NoiseConfig type.
     */
    @Redirect(
            method = "createChunkNoiseSampler",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/chunk/ChunkNoiseSampler;create(Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/densityfunction/DensityFunctionTypes$Beardifying;Lnet/minecraft/world/gen/chunk/ChunkGeneratorSettings;Lnet/minecraft/world/gen/chunk/AquiferSampler$FluidLevelSampler;Lnet/minecraft/world/gen/chunk/Blender;)Lnet/minecraft/world/gen/chunk/ChunkNoiseSampler;"))
    private ChunkNoiseSampler ringworld$createPeriodicSampler(
            Chunk chunk, NoiseConfig noiseConfig, DensityFunctionTypes.Beardifying beardifying,
            ChunkGeneratorSettings settings, AquiferSampler.FluidLevelSampler fluidLevelSampler,
            Blender blender) {
        NoiseRouter override = ringworld$geometry == null ? null
                : ringworld$getOrCreatePeriodicRouter(noiseConfig, noiseConfig.getNoiseRouter());
        return RingNoiseSamplingContext.withRouter(override,
                () -> ChunkNoiseSampler.create(chunk, noiseConfig, beardifying, settings, fluidLevelSampler, blender));
    }
}
