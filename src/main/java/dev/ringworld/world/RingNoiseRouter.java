package dev.ringworld.world;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Applies cylindrical coordinates only at noise-consuming density leaves. */
public final class RingNoiseRouter {
    private RingNoiseRouter() { }

    public static NoiseRouter wrap(NoiseRouter router, RingGeometry geometry) {
        return wrap(router, geometry, RingTerrainNoiseMapping.CURRENT);
    }

    public static NoiseRouter wrap(NoiseRouter router, RingGeometry geometry, int mappingVersion) {
        return router.mapAll(new CylindricalVisitor(
                RingNoiseCoordinates.forGeometry(geometry, mappingVersion)));
    }

    /**
     * Cache and interpolation wrappers must continue receiving the real
     * NoiseChunk object: several vanilla optimizations and the aquifer
     * grid rely on that identity and its local coordinates. Only functions
     * which actually consume horizontal coordinates are wrapped.
     */
    private record CylindricalVisitor(RingNoiseCoordinates coordinates)
            implements DensityFunction.Visitor {
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
        public double compute(FunctionContext pos) {
            return delegate.compute(transform(pos));
        }

        @Override
        public void fillArray(double[] values, ContextProvider applier) {
            delegate.fillArray(values, new TransformingApplier(applier, coordinates));
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new CylindricalDensityFunction(delegate.mapAll(visitor), coordinates));
        }

        @Override public double minValue() { return delegate.minValue(); }
        @Override public double maxValue() { return delegate.maxValue(); }
        @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return delegate.codec(); }

        private FunctionContext transform(FunctionContext source) {
            if (source instanceof CylindricalNoisePos) return source;
            int sourceX = source.blockX();
            int sourceZ = source.blockZ();
            return new CylindricalNoisePos(coordinates.noiseX(sourceX, sourceZ), source.blockY(),
                    coordinates.noiseZ(sourceX, sourceZ), source.getBlender());
        }
    }

    private record TransformingApplier(DensityFunction.ContextProvider delegate, RingNoiseCoordinates coordinates)
            implements DensityFunction.ContextProvider {
        @Override
        public DensityFunction.FunctionContext forIndex(int index) {
            DensityFunction.FunctionContext source = delegate.forIndex(index);
            if (source instanceof CylindricalNoisePos) return source;
            int sourceX = source.blockX();
            int sourceZ = source.blockZ();
            return new CylindricalNoisePos(coordinates.noiseX(sourceX, sourceZ), source.blockY(),
                    coordinates.noiseZ(sourceX, sourceZ), source.getBlender());
        }

        @Override
        public void fillAllDirectly(double[] densities, DensityFunction function) {
            delegate.fillAllDirectly(densities, new CylindricalDensityFunction(function, coordinates));
        }
    }

    private record CylindricalNoisePos(int blockX, int blockY, int blockZ, Blender getBlender)
            implements DensityFunction.FunctionContext { }
}
