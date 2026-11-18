package dev.ringworld.server;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.ringworld.world.RingSurfaceLod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.IdentityHashMap;
import java.util.Map;

/** Top-face texture means and vanilla tint sources. The biome cache belongs to one world. */
final class RingAtlasColours {
    private final Map<Biome, ClimateSample> climates = new IdentityHashMap<>();

    int surface(ServerLevel world, ChunkAccess chunk, BlockPos pos, BlockState state) {
        if (state.getFluidState().is(FluidTags.WATER)) {
            return RingSurfaceLod.applyTextureLuminance(world.getBiome(pos).value().getWaterColor(), 0.58);
        }
        if (state.is(Blocks.SPRUCE_LEAVES)) return tint(FoliageColor.FOLIAGE_EVERGREEN, 126);
        if (state.is(Blocks.BIRCH_LEAVES)) return tint(FoliageColor.FOLIAGE_BIRCH, 130);
        if (state.is(Blocks.CHERRY_LEAVES)) return 0xe5adc2;
        if (state.is(Blocks.PALE_OAK_LEAVES)) return 0x757a73;
        if (state.is(Blocks.AZALEA_LEAVES)) return 0x5a732c;
        if (state.is(Blocks.FLOWERING_AZALEA_LEAVES)) return 0x646f3d;
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) return 0xf9fefe;
        if (state.is(Blocks.POWDER_SNOW)) return 0xf8fdfd;
        if (state.is(Blocks.STONE)) return 0x7e7e7e;
        if (state.is(Blocks.GRAVEL)) return 0x847f7f;
        if (state.is(Blocks.DIRT)) return 0x866043;
        if (state.is(Blocks.PODZOL)) return 0x5c3f18;
        if (state.is(Blocks.SAND)) return RingSurfaceLod.VANILLA_SAND_TOP_RGB;
        if (state.is(Blocks.MYCELIUM)) return RingSurfaceLod.VANILLA_MYCELIUM_TOP_RGB;

        boolean grass = state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN);
        boolean litter = state.is(Blocks.LEAF_LITTER);
        if (!grass && !litter && !state.is(BlockTags.LEAVES) && !state.is(Blocks.VINE)) {
            return state.getMapColor(chunk, pos).col;
        }
        // Use the saved quart biome for both live and disk-only samples; this never requests a neighbouring chunk.
        Biome biome = chunk.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2).value();
        var effects = biome.getSpecialEffects();
        if (grass) {
            int rgb = biome.getGrassColor(pos.getX(), pos.getZ());
            if (world.getServer().isDedicatedServer() && effects.grassColorOverride().isEmpty()) {
                rgb = effects.grassColorModifier().modifyColor(pos.getX(), pos.getZ(), climate(world, biome).grass);
            }
            if (state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)) return tint(rgb, 125);
            if (state.is(Blocks.SHORT_GRASS)) return tint(rgb, 146);
            return tint(rgb, state.is(Blocks.TALL_GRASS) ? 150 : 147);
        }
        if (litter) {
            int rgb = biome.getDryFoliageColor();
            if (world.getServer().isDedicatedServer() && effects.dryFoliageColorOverride().isEmpty()) rgb = climate(world, biome).litter;
            return tint(rgb, 171);
        }
        int rgb = biome.getFoliageColor();
        if (world.getServer().isDedicatedServer() && effects.foliageColorOverride().isEmpty()) rgb = climate(world, biome).foliage;
        if (state.is(Blocks.VINE)) return tint(rgb, 116);
        if (state.is(Blocks.ACACIA_LEAVES)) return tint(rgb, 149);
        if (state.is(Blocks.DARK_OAK_LEAVES)) return tint(rgb, 151);
        if (state.is(Blocks.JUNGLE_LEAVES)) return tint(rgb, 154);
        return tint(rgb, state.is(Blocks.MANGROVE_LEAVES) ? 129 : 144);
    }

    private static int tint(int rgb, int mean) {
        return RingSurfaceLod.applyTextureLuminance(rgb, mean / 255.0);
    }

    private ClimateSample climate(ServerLevel world, Biome biome) {
        return climates.computeIfAbsent(biome, key -> {
            // The network codec exposes vanilla's climate without a version-specific private-field accessor.
            var encoded = Biome.NETWORK_CODEC.encodeStart(world.registryAccess().createSerializationContext(JsonOps.INSTANCE), key);
            float rain = encoded.result().filter(e -> e instanceof JsonObject && e.getAsJsonObject().has("downfall"))
                    .map(e -> e.getAsJsonObject().get("downfall").getAsFloat()).orElse(0.5F);
            return climateSample(key.getBaseTemperature(), rain);
        });
    }

    static ClimateSample climateSample(float temperature, float rainfall) {
        double t = Math.clamp(temperature, 0F, 1F);
        int x = (int)((1 - t) * 255), y = (int)((1 - t * Math.clamp(rainfall, 0F, 1F)) * 255);
        ClimateSample best = VANILLA_CLIMATES[0];
        int distance = Integer.MAX_VALUE;
        for (ClimateSample sample : VANILLA_CLIMATES) {
            int dx = x - sample.x, dy = y - sample.y;
            int candidate = dx * dx + dy * dy;
            if (candidate < distance) {
                distance = candidate;
                best = sample;
            }
        }
        return best;
    }

    record ClimateSample(int x, int y, int grass, int foliage, int litter) { }

    // Colormap coordinates and grass, foliage and litter RGB at vanilla biome climates.
    private static final ClimateSample[] VANILLA_CLIMATES = {
            new ClimateSample(0, 178, 0x9abe4b, 0x82ac1e, 0xa37946),
            new ClimateSample(0, 255, 0xbfb755, 0xaea42a, 0xa38046),
            new ClimateSample(12, 36, 0x59c93c, 0x30bb0b, 0xa36346),
            new ClimateSample(12, 61, 0x64c73f, 0x3eb80f, 0xa36646),
            new ClimateSample(25, 25, 0x55c93f, 0x2bbb0f, 0xa36246),
            new ClimateSample(50, 71, 0x6ac44e, 0x45b421, 0xa36946),
            new ClimateSample(50, 173, 0x91bd59, 0x77ab2f, 0xa37546),
            new ClimateSample(76, 112, 0x79c05a, 0x59ae30, 0xa36d46),
            new ClimateSample(101, 163, 0x88bb66, 0x6ba940, 0xa37246),
            new ClimateSample(127, 152, 0x83bb6d, 0x63a948, 0xa17148),
            new ClimateSample(127, 191, 0x8eb971, 0x71a74d, 0xa17448),
            new ClimateSample(178, 193, 0x86b87f, 0x68a55f, 0x9c754d),
            new ClimateSample(191, 203, 0x86b783, 0x68a464, 0x9a764f),
            new ClimateSample(203, 239, 0x8ab689, 0x6da36b, 0x967753),
            new ClimateSample(242, 251, 0x83b593, 0x64a278, 0x917958),
            new ClimateSample(255, 255, 0x80b497, 0x60a17b, 0x8f7a5a)
    };
}
