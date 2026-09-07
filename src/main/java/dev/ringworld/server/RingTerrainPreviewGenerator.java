package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingTerrainPreview;
import dev.ringworld.world.RingTerrainPreviewSampler;
import dev.ringworld.world.RingTerrainPreviewStage;
import dev.ringworld.world.RingWallStyle;
import dev.ringworld.world.RingWorldGeneratorAccess;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/** Builds previews from a detached generator and random state without creating chunks. */
final class RingTerrainPreviewGenerator {
    private RingTerrainPreviewGenerator() { }

    /** Captures only immutable registry/settings inputs on the server thread. */
    static Input capture(ServerLevel world, long worldHash, RingGeometry geometry) {
        ChunkGenerator retained = world.getChunkSource().getGenerator();
        if (!(retained instanceof NoiseBasedChunkGenerator generator)
                || !(retained instanceof RingWorldGeneratorAccess)) {
            return null;
        }
        RingWorldSettings settings = RingWorldSettings.get(world);
        if (!settings.geometry().equals(geometry)
                || RingTerrainAtlas.worldHash(settings) != worldHash) return null;
        return new Input(worldHash, geometry, generator.getBiomeSource(),
                generator.generatorSettings(),
                world.registryAccess().lookupOrThrow(Registries.NOISE),
                settings.generatorSeed(), settings.terrainNoiseMapping(),
                settings.wallHeightBlocks(), settings.wallStyle(), settings.generationSettings(),
                world.getMinBuildHeight(), world.getHeight());
    }

    /** Materializes all mutable generator/noise caches inside the preview worker. */
    static IsolatedInput isolate(Input input) {
        ChunkGenerator generator = new NoiseBasedChunkGenerator(
                input.biomeSource(), input.generatorSettings());
        if (!(generator instanceof RingWorldGeneratorAccess access)) return null;
        access.ringworld$setGeometry(input.geometry());
        access.ringworld$setTerrainNoiseMapping(input.terrainNoiseMapping());
        access.ringworld$setWallHeight(input.wallHeight());
        access.ringworld$setWallStyle(input.wallStyle());
        access.ringworld$setGenerationSettings(input.generationSettings(), input.seed());
        RandomState randomState = RandomState.create(input.generatorSettings().value(),
                input.noiseParameters(), input.seed());
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(
                input.minBuildHeight(), input.buildHeight());
        RingTerrainPreviewSampler.Source source = RingTerrainPreviewSampler.generatorSource(
                generator, randomState, heightAccessor);
        return source == null ? null : new IsolatedInput(input, source);
    }

    static RingTerrainPreview generate(IsolatedInput input, RingTerrainPreviewStage stage) {
        long started = System.nanoTime();
        RingTerrainPreview preview = RingTerrainPreviewSampler.generate(
                input.input().worldHash(), input.input().geometry(), stage, input.source());
        RingWorldMod.LOGGER.info(
                "Generated {} RingWorld terrain preview: {}x{} colour / {}x{} terrain in {} ms",
                stage.logLabel(), preview.columns(), preview.rows(),
                Math.min(preview.columns(), stage.terrainColumns()),
                Math.min(preview.rows(), stage.terrainRows()),
                Math.round((System.nanoTime() - started) / 1_000_000.0));
        return preview;
    }

    record Input(long worldHash, RingGeometry geometry, BiomeSource biomeSource,
                 Holder<NoiseGeneratorSettings> generatorSettings,
                 HolderLookup.RegistryLookup<NormalNoise.NoiseParameters> noiseParameters,
                 long seed, int terrainNoiseMapping, int wallHeight,
                 RingWallStyle wallStyle, dev.ringworld.world.RingWorldGenerationSettings generationSettings,
                 int minBuildHeight, int buildHeight) { }

    record IsolatedInput(Input input, RingTerrainPreviewSampler.Source source) { }
}
