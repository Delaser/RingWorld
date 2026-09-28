package dev.ringworld.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.densityfunction.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingCompiledNoiseTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapVanillaCodecs() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void compiledLeafKeepsScalarAndStridedVolumePeriodicAndTransformsOnce() {
        RingGeometry geometry = new RingGeometry(128, 2048);
        DensityFunction leaf = new CoordinateLeaf();
        NoiseRouter router = new NoiseRouter(leaf, leaf, leaf, leaf, leaf, leaf, leaf, leaf);
        RingNoiseRouter rewrite = new RingNoiseRouter(geometry, RingTerrainNoiseMapping.CURRENT,
                RingWorldGenerationSettings.DEFAULT, 123, 63, router);
        DensityFunction wrapped = rewrite.rewrite(leaf);
        assertSame(wrapped, rewrite.rewrite(leaf));
        assertEquals(DensityFunction.ALL_AXES, wrapped.domainAxes());
        DensitySampler sampler = wrapped.compileSampler(null);
        DensityVolume volume = new DensityVolume(9, 5, 7, -4, -16, -48, 3, 4, 8);
        DensityBuffer values = DensityBuffer.createUnpooled(volume.size());
        sampler.sampleVolume(SamplerContext.EMPTY_UNCACHED, values, volume);
        RingNoiseCoordinates coordinates = RingNoiseCoordinates.forGeometry(geometry);
        for (int z = 0; z < volume.sizeZ(); z++) for (int x = 0; x < volume.sizeX(); x++)
            for (int y = 0; y < volume.sizeY(); y++) {
                int bx = volume.blockX(x), by = volume.blockY(y), bz = volume.blockZ(z);
                float expected = coordinates.noiseX(bx, bz) * 3f + by;
                assertEquals(expected, values.get(volume.indexUnchecked(x, y, z)));
                assertEquals(expected, sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, bx, by, bz));
                assertEquals(expected, sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, bx + 2048, by, bz));
                assertEquals(expected, sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, bx - 4096, by, bz));
            }
    }

    private record CoordinateLeaf() implements DensityFunction, RingCoordinateDensityFunction {
        @Override public DensitySampler compileSampler(CompileContext compiler) {
            return new DensitySampler() {
                @Override public float sampleValue(SamplerContext context, int x, int y, int z) { return x * 3f + y; }
                @Override public void sampleVolume(SamplerContext context, DensityBuffer values, DensityVolume volume) {
                    throw new AssertionError("The cylindrical wrapper samples transformed coordinates");
                }
            };
        }
        @Override public DensityFunction rewriteChildren(DfRewriteRule rule) { return this; }
        @Override public Interval range() { return Interval.INFINITE; }
        @Override public int domainAxes() { return AXIS_X | AXIS_Y; }
        @Override public MapCodec<? extends DensityFunction> codec() { throw new UnsupportedOperationException(); }
    }
}
