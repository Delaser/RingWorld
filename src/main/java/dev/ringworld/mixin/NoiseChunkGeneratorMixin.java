package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingNoiseRouter;
import dev.ringworld.world.RingNoiseSamplingContext;
import dev.ringworld.world.RingWorldGeneratorAccess;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Supplies a periodic density router only to the Overworld's registered generator. */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseChunkGeneratorMixin implements RingWorldGeneratorAccess {
    @Unique private @Nullable RingGeometry ringworld$geometry;
    @Unique private int ringworld$wallHeight;
    @Unique private @Nullable RandomState ringworld$cachedNoiseConfig;
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

    @Inject(method = "fillFromNoise", at = @At("HEAD"), cancellable = true)
    private void ringworld$skipExteriorNoise(Blender blender, RandomState noiseConfig,
                                             StructureManager structures, ChunkAccess chunk,
                                             CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (ringworld$geometry != null && RingGenerationBoundary.isExterior(chunk, ringworld$geometry)) {
            cir.setReturnValue(CompletableFuture.completedFuture(chunk));
        }
    }

    @Inject(method = "buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            at = @At("HEAD"), cancellable = true)
    private void ringworld$skipExteriorSurface(WorldGenRegion region, StructureManager structures,
                                               RandomState noiseConfig, ChunkAccess chunk, CallbackInfo ci) {
        if (ringworld$geometry != null && RingGenerationBoundary.isExterior(chunk, ringworld$geometry)) ci.cancel();
    }

    @Inject(method = "applyCarvers", at = @At("HEAD"), cancellable = true)
    private void ringworld$skipExteriorCarvers(WorldGenRegion region, long seed, RandomState noiseConfig,
                                               BiomeManager biomeAccess, StructureManager structures,
                                               ChunkAccess chunk, CallbackInfo ci) {
        if (ringworld$geometry != null && RingGenerationBoundary.isExterior(chunk, ringworld$geometry)) ci.cancel();
    }

    @Redirect(
            method = "doCreateBiomes",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/RandomState;router()Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private NoiseRouter ringworld$periodicBiomeRouter(RandomState noiseConfig) {
        NoiseRouter vanilla = noiseConfig.router();
        if (ringworld$geometry == null) return vanilla;
        return ringworld$getOrCreatePeriodicRouter(noiseConfig, vanilla);
    }

    @Unique
    private NoiseRouter ringworld$getOrCreatePeriodicRouter(RandomState noiseConfig, NoiseRouter vanilla) {
        if (ringworld$cachedNoiseConfig != noiseConfig || ringworld$cachedRouter == null) {
            ringworld$cachedNoiseConfig = noiseConfig;
            ringworld$cachedRouter = RingNoiseRouter.wrap(vanilla, ringworld$geometry);
        }
        return ringworld$cachedRouter;
    }

    /**
     * NoiseChunk, not NoiseBasedChunkGenerator, fetches the router used for
     * final terrain density. Carry the Overworld override across that static
     * factory call so the sampler mixin can select it without affecting the
     * Nether or End, which may share the same NoiseConfig type.
     */
    @Redirect(
            method = "createNoiseChunk",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseChunk;forChunk(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/DensityFunctions$BeardifierOrMarker;Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;Lnet/minecraft/world/level/levelgen/blending/Blender;)Lnet/minecraft/world/level/levelgen/NoiseChunk;"))
    private NoiseChunk ringworld$createPeriodicSampler(
            ChunkAccess chunk, RandomState noiseConfig, DensityFunctions.BeardifierOrMarker beardifying,
            NoiseGeneratorSettings settings, Aquifer.FluidPicker fluidLevelSampler,
            Blender blender) {
        NoiseRouter override = ringworld$geometry == null ? null
                : ringworld$getOrCreatePeriodicRouter(noiseConfig, noiseConfig.router());
        return RingNoiseSamplingContext.withRouter(override,
                () -> NoiseChunk.forChunk(chunk, noiseConfig, beardifying, settings, fluidLevelSampler, blender));
    }
}
