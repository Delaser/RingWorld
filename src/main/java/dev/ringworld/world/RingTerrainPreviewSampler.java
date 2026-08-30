package dev.ringworld.world;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Locale;

/**
 * Chunk-free terrain/biome sampler shared by the creation preview and the
 * in-world pre-Atlas placeholder.
 */
public final class RingTerrainPreviewSampler {
    private static final int WATER = 0x315C78;
    private static final int GRASS = 0x526B3B;
    private static final int FOREST = 0x3F6136;
    private static final int JUNGLE = 0x315E2B;
    private static final int TAIGA = 0x486052;
    private static final int SWAMP = 0x4B5D3A;
    private static final int SAND = 0xB7A66A;
    private static final int BADLANDS = 0xA45E3B;
    private static final int SAVANNA = 0x7D7B3E;
    private static final int SNOW = 0xD9E3DF;
    private static final int STONE = 0x72736D;
    private static final int MYCELIUM = RingSurfaceLod.VANILLA_MYCELIUM_TOP_RGB;

    private RingTerrainPreviewSampler() { }

    public static RingTerrainPreview generate(
            long worldHash, RingGeometry geometry, RingTerrainPreviewStage stage,
            ChunkGenerator generator, RandomState randomState,
            LevelHeightAccessor heightAccessor) {
        if (!(generator instanceof RingWorldGeneratorAccess access)) return null;
        BiomeSource biomeSource = generator.getBiomeSource();
        var sampler = access.ringworld$getPeriodicClimateSampler(randomState);
        int seaLevel = generator.getSeaLevel();
        int columns = Math.min(geometry.circumferenceBlocks(), stage.colorColumns());
        int rows = Math.min(geometry.widthBlocks(), stage.colorRows());
        int terrainColumns = Math.min(columns, stage.terrainColumns());
        int terrainRows = Math.min(rows, stage.terrainRows());
        int[] terrainHeights = sampleTerrainHeights(
                generator, randomState, heightAccessor, geometry, terrainColumns, terrainRows);
        int cells = Math.multiplyExact(columns, rows);
        int[] colors = new int[cells];
        short[] heights = new short[cells];
        for (int row = 0; row < rows; row++) {
            checkCancelled();
            int z = geometry.minWidthZ() + (int)(((long)row * 2L + 1L)
                    * geometry.widthBlocks() / (rows * 2L));
            for (int column = 0; column < columns; column++) {
                int x = (int)(((long)column * 2L + 1L)
                        * geometry.circumferenceBlocks() / (columns * 2L));
                int terrainHeight = terrainHeights[
                        sampleIndex(row, rows, terrainRows) * terrainColumns
                                + sampleIndex(column, columns, terrainColumns)];
                Holder<Biome> holder = biomeSource.getNoiseBiome(
                        Math.floorDiv(x, 4), Math.floorDiv(Math.max(terrainHeight, seaLevel), 4),
                        Math.floorDiv(z, 4), sampler);
                PreviewSample sample = sample(holder, x, z, terrainHeight, seaLevel);
                int index = row * columns + column;
                colors[index] = sample.color();
                heights[index] = (short)sample.height();
            }
        }
        return new RingTerrainPreview(worldHash, columns, rows, colors, heights);
    }

    private static int[] sampleTerrainHeights(
            ChunkGenerator generator, RandomState randomState,
            LevelHeightAccessor heightAccessor, RingGeometry geometry,
            int columns, int rows) {
        int[] heights = new int[Math.multiplyExact(columns, rows)];
        for (int row = 0; row < rows; row++) {
            checkCancelled();
            int z = geometry.minWidthZ() + (int)(((long)row * 2L + 1L)
                    * geometry.widthBlocks() / (rows * 2L));
            for (int column = 0; column < columns; column++) {
                int x = (int)(((long)column * 2L + 1L)
                        * geometry.circumferenceBlocks() / (columns * 2L));
                heights[row * columns + column] = generator.getFirstOccupiedHeight(
                        x, z, Heightmap.Types.OCEAN_FLOOR_WG, heightAccessor, randomState);
            }
        }
        return heights;
    }

    private static PreviewSample sample(Holder<Biome> holder, int x, int z,
                                        int terrainHeight, int seaLevel) {
        String path = holder.unwrapKey().map(key -> key.identifier().getPath())
                .orElse("").toLowerCase(Locale.ROOT);
        int color;
        if (terrainHeight < seaLevel || holder.is(BiomeTags.IS_RIVER)) {
            color = validColor(holder.value().getWaterColor(), WATER);
        } else if (holder.is(BiomeTags.IS_BEACH)) {
            color = path.contains("snow") ? SNOW : SAND;
        } else if (holder.is(BiomeTags.IS_BADLANDS)) {
            color = BADLANDS;
        } else if (path.contains("mushroom")) {
            color = MYCELIUM;
        } else if (path.contains("snow") || path.contains("frozen")
                || path.contains("ice") || path.contains("grove")) {
            color = SNOW;
        } else if (holder.is(BiomeTags.IS_MOUNTAIN) || path.contains("peak")) {
            color = path.contains("stony") || path.contains("jagged") ? STONE : GRASS;
        } else if (holder.is(BiomeTags.IS_JUNGLE)) {
            color = JUNGLE;
        } else if (holder.is(BiomeTags.IS_TAIGA)) {
            color = TAIGA;
        } else if (holder.is(BiomeTags.IS_FOREST)) {
            color = FOREST;
        } else if (holder.is(BiomeTags.IS_SAVANNA)) {
            color = SAVANNA;
        } else if (path.contains("desert")) {
            color = SAND;
        } else if (path.contains("swamp") || path.contains("mangrove")) {
            color = SWAMP;
        } else {
            color = validColor(holder.value().getGrassColor(x, z), GRASS);
        }
        return new PreviewSample(color, Math.max(1, terrainHeight));
    }

    private static int validColor(int color, int fallback) {
        return (color & 0xFFFFFF) == 0 ? fallback : color & 0xFFFFFF;
    }

    private static int sampleIndex(int targetIndex, int targetSize, int sourceSize) {
        return Math.min(sourceSize - 1,
                (int)(((long)targetIndex * 2L + 1L) * sourceSize / (targetSize * 2L)));
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("terrain preview cancelled");
        }
    }

    private record PreviewSample(int color, int height) { }
}
