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

/** Chunk-free, approximate terrain/biome sampling for staged previews. */
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

    public static RingTerrainPreview generate(long worldHash, RingGeometry geometry,
                                               RingTerrainPreviewStage stage,
                                               Source source) {
        int columns = Math.min(geometry.circumferenceBlocks(), stage.colorColumns());
        int rows = Math.min(geometry.widthBlocks(), stage.colorRows());
        int terrainColumns = Math.min(columns, stage.terrainColumns());
        int terrainRows = Math.min(rows, stage.terrainRows());
        int[] terrainHeights = sampleTerrainHeights(
                geometry, terrainColumns, terrainRows, source);
        int cells = Math.multiplyExact(columns, rows);
        int[] colors = new int[cells];
        short[] heights = new short[cells];
        for (int row = 0; row < rows; row++) {
            checkCancelled();
            int z = sampleZ(geometry, row, rows);
            for (int column = 0; column < columns; column++) {
                int x = sampleX(geometry, column, columns);
                int terrainHeight = terrainHeights[
                        sampleIndex(row, rows, terrainRows) * terrainColumns
                                + sampleIndex(column, columns, terrainColumns)];
                PreviewSample sample = source.surface(x, z, terrainHeight);
                int index = row * columns + column;
                colors[index] = sample.color();
                heights[index] = (short)Math.max(1, sample.height());
            }
        }
        return new RingTerrainPreview(worldHash, columns, rows, colors, heights);
    }

    public static Source generatorSource(ChunkGenerator generator, RandomState randomState,
                                         LevelHeightAccessor heightAccessor) {
        if (!(generator instanceof RingWorldGeneratorAccess access)) return null;
        BiomeSource biomeSource = generator.getBiomeSource();
        var climate = access.ringworld$getPeriodicClimateSampler(randomState);
        int seaLevel = generator.getSeaLevel();
        return new Source() {
            @Override
            public int terrainHeight(int x, int z) {
                return generator.getFirstOccupiedHeight(
                        x, z, Heightmap.Types.OCEAN_FLOOR_WG, heightAccessor, randomState);
            }

            @Override
            public PreviewSample surface(int x, int z, int terrainHeight) {
                Holder<Biome> holder = biomeSource.getNoiseBiome(
                        Math.floorDiv(x, 4),
                        Math.floorDiv(Math.max(terrainHeight, seaLevel), 4),
                        Math.floorDiv(z, 4), climate);
                return sample(holder, x, z, terrainHeight, seaLevel);
            }
        };
    }

    private static int[] sampleTerrainHeights(RingGeometry geometry, int columns,
                                              int rows, Source source) {
        int[] heights = new int[Math.multiplyExact(columns, rows)];
        for (int row = 0; row < rows; row++) {
            checkCancelled();
            int z = sampleZ(geometry, row, rows);
            for (int column = 0; column < columns; column++) {
                heights[row * columns + column] = source.terrainHeight(
                        sampleX(geometry, column, columns), z);
            }
        }
        return heights;
    }

    private static PreviewSample sample(Holder<Biome> holder, int x, int z,
                                        int terrainHeight, int seaLevel) {
        String path = holder.unwrapKey().map(key -> key.location().getPath())
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
        return new PreviewSample(color, terrainHeight);
    }

    private static int validColor(int color, int fallback) {
        return (color & 0xFFFFFF) == 0 ? fallback : color & 0xFFFFFF;
    }

    private static int sampleX(RingGeometry geometry, int index, int count) {
        return (int)(((long)index * 2L + 1L)
                * geometry.circumferenceBlocks() / (count * 2L));
    }

    private static int sampleZ(RingGeometry geometry, int index, int count) {
        return geometry.minWidthZ() + (int)(((long)index * 2L + 1L)
                * geometry.widthBlocks() / (count * 2L));
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

    public interface Source {
        int terrainHeight(int x, int z);
        PreviewSample surface(int x, int z, int terrainHeight);
    }

    public record PreviewSample(int color, int height) { }
}
