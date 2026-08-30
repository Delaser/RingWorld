package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingClimateSampler;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingNoiseRouter;
import dev.ringworld.world.RingNoiseSamplingContext;
import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingWorldGeneratorAccess;
import dev.ringworld.world.RingWallStyle;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.core.Holder;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Supplies a periodic density router only to the Overworld's registered generator. */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseChunkGeneratorMixin implements RingWorldGeneratorAccess {
    @Shadow @Final private Holder<NoiseGeneratorSettings> settings;
    @Unique private @Nullable RingGeometry ringworld$geometry;
    @Unique private int ringworld$terrainNoiseMapping = RingTerrainNoiseMapping.CURRENT;
    @Unique private int ringworld$wallHeight;
    @Unique private RingWallStyle ringworld$wallStyle = RingWallStyle.LEGACY;
    @Unique private volatile boolean ringworld$guaranteeStronghold;
    @Unique private @Nullable RandomState ringworld$cachedNoiseConfig;
    @Unique private @Nullable NoiseRouter ringworld$cachedRouter;
    @Unique private @Nullable RandomState ringworld$cachedClimateNoiseConfig;
    @Unique private @Nullable Climate.Sampler ringworld$cachedClimateSampler;

    @Override
    public void ringworld$setGeometry(RingGeometry geometry) {
        this.ringworld$geometry = geometry;
        this.ringworld$cachedNoiseConfig = null;
        this.ringworld$cachedRouter = null;
        this.ringworld$cachedClimateNoiseConfig = null;
        this.ringworld$cachedClimateSampler = null;
    }

    @Override
    public @Nullable RingGeometry ringworld$getGeometry() {
        return ringworld$geometry;
    }

    @Override
    public void ringworld$setTerrainNoiseMapping(int mappingVersion) {
        int supported = RingTerrainNoiseMapping.requireSupported(mappingVersion);
        if (ringworld$terrainNoiseMapping == supported) return;
        ringworld$terrainNoiseMapping = supported;
        ringworld$cachedNoiseConfig = null;
        ringworld$cachedRouter = null;
        ringworld$cachedClimateNoiseConfig = null;
        ringworld$cachedClimateSampler = null;
    }

    @Override
    public int ringworld$getTerrainNoiseMapping() {
        return ringworld$terrainNoiseMapping;
    }

    @Override
    public void ringworld$setWallHeight(int wallHeightBlocks) {
        ringworld$wallHeight = wallHeightBlocks;
    }

    @Override
    public int ringworld$getWallHeight() {
        return ringworld$wallHeight;
    }

    @Override
    public void ringworld$setWallStyle(RingWallStyle wallStyle) {
        this.ringworld$wallStyle = java.util.Objects.requireNonNull(wallStyle, "wallStyle");
    }

    @Override
    public RingWallStyle ringworld$getWallStyle() {
        return ringworld$wallStyle;
    }

    @Override
    public void ringworld$setGuaranteeStronghold(boolean guaranteeStronghold) {
        ringworld$guaranteeStronghold = guaranteeStronghold;
    }

    @Override
    public boolean ringworld$guaranteesStronghold() {
        return ringworld$guaranteeStronghold;
    }

    @Override
    public synchronized Climate.Sampler ringworld$getPeriodicClimateSampler(RandomState noiseConfig) {
        if (ringworld$geometry == null) return noiseConfig.sampler();
        NoiseRouter router = ringworld$getOrCreatePeriodicRouter(noiseConfig, noiseConfig.router());
        if (ringworld$cachedClimateNoiseConfig != noiseConfig || ringworld$cachedClimateSampler == null) {
            ringworld$cachedClimateNoiseConfig = noiseConfig;
            ringworld$cachedClimateSampler = RingClimateSampler.create(router, settings.value().spawnTarget());
        }
        return ringworld$cachedClimateSampler;
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
            method = "applyCarvers",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"))
    private void ringworld$periodicCarverSeed(WorldgenRandom random, long seed,
                                               int sourceChunkX, int sourceChunkZ) {
        int seedChunkX = ringworld$geometry == null ? sourceChunkX
                : RingTerrainNoiseMapping.carverSeedChunkX(
                        ringworld$geometry, ringworld$terrainNoiseMapping, sourceChunkX);
        random.setLargeFeatureSeed(seed, seedChunkX, sourceChunkZ);
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
    private synchronized NoiseRouter ringworld$getOrCreatePeriodicRouter(RandomState noiseConfig, NoiseRouter vanilla) {
        if (ringworld$cachedNoiseConfig != noiseConfig || ringworld$cachedRouter == null) {
            ringworld$cachedNoiseConfig = noiseConfig;
            ringworld$cachedRouter = RingNoiseRouter.wrap(
                    vanilla, ringworld$geometry, ringworld$terrainNoiseMapping);
            ringworld$cachedClimateNoiseConfig = null;
            ringworld$cachedClimateSampler = null;
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

    /**
     * Height queries used to anchor structures take a separate vanilla path:
     * {@code getBaseHeight} and {@code getBaseColumn} share
     * {@code iterateNoiseColumn}, which constructs its sampler directly
     * instead of calling {@code createNoiseChunk}. Canonicalize X at this
     * private ownership boundary before vanilla derives its cell/cache and
     * interpolation coordinates, then keep the sampler in the same
     * Overworld-only router context as real chunk terrain. Otherwise a
     * village can choose its Y from flat or alias-chart noise while the
     * terrain beneath it is generated from cylindrical noise.
     */
    @ModifyVariable(method = "iterateNoiseColumn", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private int ringworld$canonicalizeHeightQueryX(int blockX) {
        return ringworld$geometry == null ? blockX : ringworld$geometry.wrapBlockX(blockX);
    }

    @Redirect(
            method = "iterateNoiseColumn",
            at = @At(value = "NEW", target = "(ILnet/minecraft/world/level/levelgen/RandomState;IILnet/minecraft/world/level/levelgen/NoiseSettings;Lnet/minecraft/world/level/levelgen/DensityFunctions$BeardifierOrMarker;Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;Lnet/minecraft/world/level/levelgen/blending/Blender;)Lnet/minecraft/world/level/levelgen/NoiseChunk;"))
    private NoiseChunk ringworld$createPeriodicHeightSampler(
            int cellCountXZ, RandomState noiseConfig, int firstBlockX, int firstBlockZ,
            NoiseSettings noiseSettings, DensityFunctions.BeardifierOrMarker beardifying,
            NoiseGeneratorSettings settings, Aquifer.FluidPicker fluidLevelSampler,
            Blender blender) {
        NoiseRouter override = ringworld$geometry == null ? null
                : ringworld$getOrCreatePeriodicRouter(noiseConfig, noiseConfig.router());
        return RingNoiseSamplingContext.withRouter(override,
                () -> new NoiseChunk(cellCountXZ, noiseConfig, firstBlockX, firstBlockZ,
                        noiseSettings, beardifying, settings, fluidLevelSampler, blender));
    }
}
