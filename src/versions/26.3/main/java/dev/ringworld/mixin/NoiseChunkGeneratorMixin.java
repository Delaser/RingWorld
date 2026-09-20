package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingClimateSampler;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingWorldGeneratorAccess;
import dev.ringworld.world.RingWorldGenerationSettings;
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

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.core.Holder;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import dev.ringworld.world.RingRandomStateAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
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
    @Unique private RingWorldGenerationSettings ringworld$generationSettings = RingWorldGenerationSettings.DEFAULT;
    @Unique private long ringworld$generatorSeed;

    @Override
    public void ringworld$setGeometry(RingGeometry geometry) {
        this.ringworld$geometry = geometry;
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
    public void ringworld$setGenerationSettings(RingWorldGenerationSettings settings, long generatorSeed) {
        if (this.ringworld$generationSettings.equals(settings) && this.ringworld$generatorSeed == generatorSeed) return;
        this.ringworld$generationSettings = java.util.Objects.requireNonNull(settings, "settings");
        this.ringworld$generatorSeed = generatorSeed;
    }

    @Override
    public RingWorldGenerationSettings ringworld$getGenerationSettings() {
        return ringworld$generationSettings;
    }

    @Override
    public synchronized Climate.Sampler ringworld$getPeriodicClimateSampler(RandomState state) {
        if (ringworld$geometry != null) ((RingRandomStateAccess)(Object)state).ringworld$configureNoise(
                ringworld$geometry, ringworld$terrainNoiseMapping, ringworld$generationSettings,
                ringworld$generatorSeed, settings.value().seaLevel(), settings.value().noiseRouter());
        return RingClimateSampler.create(state);
    }
    @Inject(method = "buildTerrain", at = @At("HEAD"), cancellable = true)
    private void ringworld$skipExteriorTerrain(ChunkAccess chunk, Blender blender, RandomState state,
            StructureManager structures, BiomeManager biomes, WorldGenRegion region,
            java.util.Set<Holder<net.minecraft.world.level.biome.Biome>> possibleBiomes,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (ringworld$geometry != null && RingGenerationBoundary.isExterior(chunk, ringworld$geometry))
            cir.setReturnValue(CompletableFuture.completedFuture(chunk));
    }
    @Redirect(method = "generateCarvers", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"))
    private void ringworld$periodicCarverSeed(WorldgenRandom random, long seed, int x, int z) {
        random.setLargeFeatureSeed(seed, ringworld$geometry == null ? x
                : RingTerrainNoiseMapping.carverSeedChunkX(ringworld$geometry, ringworld$terrainNoiseMapping, x), z);
    }
    @ModifyVariable(method = "iterateNoiseColumn", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ringworld$canonicalizeHeightQueryX(int x) {
        return ringworld$geometry == null ? x : ringworld$geometry.wrapBlockX(x);
    }
    @Inject(method = "doFill", at = @At("TAIL"))
    private void ringworld$captureFixtureNoiseHeights(net.minecraft.world.level.levelgen.NoiseChunk noise,
            ChunkAccess chunk, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (ringworld$geometry != null && Boolean.getBoolean("ringworld.strongholdTest")) {
            dev.ringworld.world.RingMinecraftFixtureAccess.captureNoiseHeights(chunk);
        }
    }
}
