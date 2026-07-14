package dev.ringworld.world;

import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.noise.NoiseRouter;

/** Applies cylindrical coordinates only at noise-consuming density leaves. */
public final class RingNoiseRouter {
    private RingNoiseRouter() { }

    public static NoiseRouter wrap(NoiseRouter router, RingGeometry geometry) {
        return router.apply(new CylindricalVisitor(RingNoiseCoordinates.forGeometry(geometry)));
    }

    /**
     * Cache and interpolation wrappers must continue receiving the real
     * ChunkNoiseSampler object: several vanilla optimizations and the aquifer
     * grid rely on that identity and its local coordinates. Only functions
     * which actually consume horizontal coordinates are wrapped.
     */
    private record CylindricalVisitor(RingNoiseCoordinates coordinates)
            implements DensityFunction.DensityFunctionVisitor {
        @Override
        public DensityFunction apply(DensityFunction function) {
            if (isCoordinateConsumer(function)) {
                return new CylindricalDensityFunction(function, coordinates);
            }
            return function;
        }

        private static boolean isCoordinateConsumer(DensityFunction function) {
            return function instanceof RingCoordinateDensityFunction;
        }
    }

    private record CylindricalDensityFunction(DensityFunction delegate, RingNoiseCoordinates coordinates)
            implements DensityFunction {
        @Override
        public double sample(NoisePos pos) {
            return delegate.sample(transform(pos));
        }

        @Override
        public void fill(double[] values, EachApplier applier) {
            delegate.fill(values, new TransformingApplier(applier, coordinates));
        }

        @Override
        public DensityFunction apply(DensityFunctionVisitor visitor) {
            return visitor.apply(new CylindricalDensityFunction(delegate.apply(visitor), coordinates));
        }

        @Override public double minValue() { return delegate.minValue(); }
        @Override public double maxValue() { return delegate.maxValue(); }
        @Override public CodecHolder<? extends DensityFunction> getCodecHolder() { return delegate.getCodecHolder(); }

        private NoisePos transform(NoisePos source) {
            if (source instanceof CylindricalNoisePos) return source;
            int sourceX = source.blockX();
            return new CylindricalNoisePos(coordinates.ringX(sourceX), source.blockY(),
                    coordinates.ringZ(sourceX, source.blockZ()), source.getBlender());
        }
    }

    private record TransformingApplier(DensityFunction.EachApplier delegate, RingNoiseCoordinates coordinates)
            implements DensityFunction.EachApplier {
        @Override
        public DensityFunction.NoisePos at(int index) {
            DensityFunction.NoisePos source = delegate.at(index);
            if (source instanceof CylindricalNoisePos) return source;
            int sourceX = source.blockX();
            return new CylindricalNoisePos(coordinates.ringX(sourceX), source.blockY(),
                    coordinates.ringZ(sourceX, source.blockZ()), source.getBlender());
        }

        @Override
        public void fill(double[] densities, DensityFunction function) {
            delegate.fill(densities, new CylindricalDensityFunction(function, coordinates));
        }
    }

    private record CylindricalNoisePos(int blockX, int blockY, int blockZ, Blender getBlender)
            implements DensityFunction.NoisePos { }
}
