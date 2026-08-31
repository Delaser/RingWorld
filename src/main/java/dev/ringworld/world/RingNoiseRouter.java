package dev.ringworld.world;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;

/** Applies cylindrical coordinates only at noise-consuming density leaves. */
public final class RingNoiseRouter {
    private RingNoiseRouter() { }

    public static NoiseRouter wrap(NoiseRouter router, RingGeometry geometry) {
        return wrap(router, geometry, RingTerrainNoiseMapping.CURRENT);
    }

    public static NoiseRouter wrap(NoiseRouter router, RingGeometry geometry, int mappingVersion) {
        return router.mapAll(new CylindricalVisitor(
                RingNoiseCoordinates.forGeometry(geometry, mappingVersion), mappingVersion));
    }

    public static NoiseRouter wrap(NoiseRouter router, RingGeometry geometry, int mappingVersion,
                                   RingWorldGenerationSettings settings, long seed, int seaLevel) {
        NoiseRouter periodic = wrap(router, geometry, mappingVersion);
        RingMacroTerrain macro = new RingMacroTerrain(geometry, seed, settings);
        if (!macro.active()) return periodic;
        return new NoiseRouter(
                periodic.barrierNoise(), periodic.fluidLevelFloodednessNoise(),
                periodic.fluidLevelSpreadNoise(), periodic.lavaNoise(),
                periodic.temperature(), periodic.vegetation(),
                settings.layout() == RingWorldLayout.ARCHIPELAGO
                        ? new MacroDensity(periodic.continents(), macro, Mode.CONTINENTS, seaLevel)
                        : periodic.continents(),
                periodic.erosion(), periodic.depth(), periodic.ridges(),
                new MacroDensity(periodic.preliminarySurfaceLevel(), macro, Mode.SURFACE, seaLevel),
                new MacroDensity(periodic.finalDensity(), macro, Mode.FINAL_DENSITY, seaLevel),
                periodic.veinToggle(), periodic.veinRidged(), periodic.veinGap());
    }

    /**
     * Cache and interpolation wrappers must continue receiving the real
     * NoiseChunk object: several vanilla optimizations and the aquifer
     * grid rely on that identity and its local coordinates. Only functions
     * which actually consume horizontal coordinates are wrapped.
     */
    private record CylindricalVisitor(RingNoiseCoordinates coordinates, int mappingVersion)
            implements DensityFunction.Visitor {
        @Override
        public DensityFunction apply(DensityFunction function) {
            if (isCoordinateConsumer(function)) {
                return new CylindricalDensityFunction(function, coordinates);
            }
            return function;
        }

        private boolean isCoordinateConsumer(DensityFunction function) {
            return function instanceof RingCoordinateDensityFunction
                    || (mappingVersion >= RingTerrainNoiseMapping.ANNULAR_COMPLETE_V2
                    && function instanceof BlendedNoise);
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

        // 26.2's recursive visitor owns traversal; this method maps only the
        // immediate child. Older ABIs use mapAll above and never call it.
        public DensityFunction mapChildren(Visitor visitor) {
            return new CylindricalDensityFunction(visitor.apply(delegate), coordinates);
        }

        @Override public double minValue() { return delegate.minValue(); }
        @Override public double maxValue() { return delegate.maxValue(); }
        @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return delegate.codec(); }

        private FunctionContext transform(FunctionContext source) {
            if (RingDensityContexts.isTransformed(source)) return source;
            int sourceX = source.blockX();
            int sourceZ = source.blockZ();
            return RingDensityContexts.transformed(source, coordinates.noiseX(sourceX, sourceZ),
                    coordinates.noiseZ(sourceX, sourceZ));
        }
    }

    private record TransformingApplier(DensityFunction.ContextProvider delegate, RingNoiseCoordinates coordinates)
            implements DensityFunction.ContextProvider {
        @Override
        public DensityFunction.FunctionContext forIndex(int index) {
            DensityFunction.FunctionContext source = delegate.forIndex(index);
            if (RingDensityContexts.isTransformed(source)) return source;
            int sourceX = source.blockX();
            int sourceZ = source.blockZ();
            return RingDensityContexts.transformed(source, coordinates.noiseX(sourceX, sourceZ),
                    coordinates.noiseZ(sourceX, sourceZ));
        }

        @Override
        public void fillAllDirectly(double[] densities, DensityFunction function) {
            delegate.fillAllDirectly(densities, new CylindricalDensityFunction(function, coordinates));
        }
    }

    private enum Mode { CONTINENTS, SURFACE, FINAL_DENSITY }

    /** Runtime-only outer policy; the saved generator codec remains vanilla. */
    private record MacroDensity(DensityFunction delegate, RingMacroTerrain macro, Mode mode, int seaLevel)
            implements DensityFunction {
        @Override
        public double compute(FunctionContext pos) {
            return computeWithBase(pos, delegate.compute(pos));
        }

        @Override
        public void fillArray(double[] values, ContextProvider provider) {
            delegate.fillArray(values, provider);
            for (int index = 0; index < values.length; index++) values[index] = computeWithBase(
                    provider.forIndex(index), values[index]);
        }

        private double computeWithBase(FunctionContext pos, double base) {
            double land = macro.landBias(pos.blockX(), pos.blockZ());
            double river = macro.riverInfluence(pos.blockX(), pos.blockZ());
            return switch (mode) {
                case CONTINENTS -> Math.max(-1.0, Math.min(1.0, base + land * 0.62));
                case SURFACE -> Math.min(base + land * 13.0,
                        river > 0.0 ? seaLevel - 1.0 + (1.0 - river) * 4.0 : Double.POSITIVE_INFINITY);
                case FINAL_DENSITY -> {
                    double surfaceBand = Math.max(0.0,
                            1.0 - Math.abs(pos.blockY() - seaLevel) / 64.0);
                    double density = base + land * 0.36 * surfaceBand;
                    // An absolute channel floor survives vanilla density-scale
                    // changes between Minecraft versions. The core cuts seven
                    // blocks below sea level; the smooth influence raises that
                    // floor into natural banks before returning to untouched
                    // terrain. Aquifers and surface rules still own the water,
                    // bed material, decoration, and local cave interaction.
                    double channelFloor = seaLevel - 7.0 + (1.0 - river) * 9.0;
                    if (river > 0.0 && pos.blockY() >= channelFloor) {
                        density = Math.min(density, -0.35 - river * 0.65);
                    }
                    yield density;
                }
            };
        }

        @Override public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new MacroDensity(delegate.mapAll(visitor), macro, mode, seaLevel));
        }
        public DensityFunction mapChildren(Visitor visitor) {
            return new MacroDensity(visitor.apply(delegate), macro, mode, seaLevel);
        }
        @Override public double minValue() { return Double.NEGATIVE_INFINITY; }
        @Override public double maxValue() { return Double.POSITIVE_INFINITY; }
        @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return delegate.codec(); }
    }

}
