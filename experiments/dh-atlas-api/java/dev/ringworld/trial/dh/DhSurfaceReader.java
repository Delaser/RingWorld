package dev.ringworld.trial.dh;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;

import dev.ringworld.world.RingSurfaceLod;
import dev.ringworld.world.RingTerrainAtlas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.BitSet;

final class DhSurfaceReader extends Thread {
    private final IDhApiLevelWrapper level;
    private final RingTerrainAtlas building;
    volatile RingTerrainAtlas ready;

    DhSurfaceReader(IDhApiLevelWrapper level, RingTerrainAtlas source) {
        super("RingWorld DH surface reader");
        setDaemon(true);
        this.level = level;
        building = new RingTerrainAtlas(source.geometry(), source.worldHash(), source.sampleStep());
    }

    public void run() {
        var geometry = building.geometry();
        int across = geometry.widthChunks();
        int total = across * geometry.circumferenceChunks();
        var complete = new BitSet(total);
        try {
            while (!isInterrupted()) {
                int before = complete.cardinality();
                // A new cache per pass lets a subsequent pass see newly generated sections.
                try (var cache = DhApi.Delayed.terrainRepo.createSoftCache()) {
                    for (int i = complete.nextClearBit(0);
                            i < total && !isInterrupted();
                            i = complete.nextClearBit(i + 1)) {
                        int cx = i / across, cz = geometry.minChunkZ() + i % across;
                        var result =
                                DhApi.Delayed.terrainRepo.getAllTerrainDataAtChunkPos(
                                        level, cx, cz, cache);
                        if (result.success && readChunk(cx, cz, result.payload)) complete.set(i);
                        if ((i & 31) == 31) Thread.sleep(2);
                    }
                } catch (RuntimeException failure) {
                    if (isInterrupted()) return;
                    System.err.println("DH API read failed, retrying in 8s: " + failure);
                    Thread.sleep(8000);
                    continue;
                }
                if (isInterrupted()) return;
                int count = complete.cardinality();
                System.out.printf(
                        "DH API coverage %d/%d chunks; %d/%d surface samples%n",
                        count, total, building.presentCount(), building.cellCount());
                if (count == total && building.isComplete()) {
                    ready = building;
                    return;
                }
                Thread.sleep(count == before ? 8000 : 2000);
            }
        } catch (InterruptedException ignored) {
            interrupt();
        }
    }

    private boolean readChunk(int cx, int cz, DhApiTerrainDataPoint[][][] chunk) {
        if (chunk == null || chunk.length != 16) return false;
        int step = building.sampleStep();
        for (int x = 0; x < 16; x++) {
            if (chunk[x] == null || chunk[x].length != 16) return false;
            for (int z = 0; z < 16; z++) {
                var spans = chunk[x][z];
                if (spans == null || spans.length == 0) return false;
                DhApiTerrainDataPoint roof = null;
                for (var span : spans) {
                    if (span != null
                            && span.blockStateWrapper != null
                            && !span.blockStateWrapper.isAir()
                            && (roof == null || span.topYBlockPos > roof.topYBlockPos)) roof = span;
                }
                if (roof == null) return false;
                if (x % step != step / 2 || z % step != step / 2) continue;
                if (!(roof.blockStateWrapper.getWrappedMcObject() instanceof BlockState state))
                    return false;
                int bx = cx * 16 + x, bz = cz * 16 + z;
                boolean water = state.getFluidState().is(FluidTags.WATER);
                int rgb = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
                Object wrapped =
                        roof.biomeWrapper == null ? null : roof.biomeWrapper.getWrappedMcObject();
                if (wrapped instanceof Holder<?> holder) wrapped = holder.value();
                if (wrapped instanceof Biome biome) {
                    if (water)
                        rgb = RingSurfaceLod.applyTextureLuminance(biome.getWaterColor(), .58);
                    else if (state.is(BlockTags.LEAVES))
                        rgb = RingSurfaceLod.applyTextureLuminance(biome.getFoliageColor(), .55);
                    else if (state.is(Blocks.GRASS_BLOCK))
                        rgb =
                                RingSurfaceLod.applyTextureLuminance(
                                        biome.getGrassColor(bx, bz), .58);
                }
                building.putBlockSample(
                        bx, bz, roof.topYBlockPos, rgb, roof.blockLightLevel, rgb, water ? 15 : 0);
            }
        }
        return true;
    }
}
