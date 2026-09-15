package dev.ringworld.world;

import com.mojang.serialization.MapCodec;
import java.util.IdentityHashMap;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;

/** Rewrites noise leaves before 26.3 compiles and caches their samplers. */
public final class RingNoiseRouter implements DfRewriteRule {
    private final RingGeometry geometry;
    private final int mapping;
    private final RingWorldGenerationSettings settings;
    private final long seed;
    private final int seaLevel;
    private final NoiseRouter router;
    private final RingNoiseCoordinates coordinates;
    private final RingMacroTerrain macro;
    private final IdentityHashMap<DensityFunction, DensityFunction> rewritten = new IdentityHashMap<>();

    public RingNoiseRouter(RingGeometry geometry, int mapping, RingWorldGenerationSettings settings,
                           long seed, int seaLevel, NoiseRouter router) {
        this.geometry = geometry; this.mapping = mapping; this.settings = settings;
        this.seed = seed; this.seaLevel = seaLevel; this.router = router;
        coordinates = RingNoiseCoordinates.forGeometry(geometry, mapping);
        macro = new RingMacroTerrain(geometry, seed, settings);
    }
    public boolean matches(RingGeometry geometry, int mapping, RingWorldGenerationSettings settings,
                           long seed, int seaLevel, NoiseRouter router) {
        return this.geometry.equals(geometry) && this.mapping == mapping && this.settings.equals(settings)
                && this.seed == seed && this.seaLevel == seaLevel && this.router == router;
    }
    @Override public synchronized DensityFunction rewrite(DensityFunction original) {
        DensityFunction cached = rewritten.get(original);
        if (cached != null) return cached;
        DensityFunction input = DfRewriteRule.INLINE_REFERENCE.rewrite(original);
        DensityFunction result;
        if (input != original) result = rewrite(input);
        else if (input instanceof RingCoordinateDensityFunction
                || (mapping >= RingTerrainNoiseMapping.ANNULAR_COMPLETE_V2 && input instanceof BlendedNoise)) {
            // The whole noise leaf sees one transform, including its shift children.
            result = new Cylindrical(input, coordinates);
        } else result = input.rewriteChildren(this);
        if (macro.active()) {
            if (original == router.finalDensity()) result = new Macro(result, macro, Mode.FINAL_DENSITY, seaLevel);
            else if (original == router.chunkSurfaceLevel()) result = new Macro(result, macro, Mode.SURFACE, seaLevel);
            else if (original == router.continents() && settings.layout() == RingWorldLayout.ARCHIPELAGO)
                result = new Macro(result, macro, Mode.CONTINENTS, seaLevel);
        }
        rewritten.put(original, result);
        return result;
    }

    private record Cylindrical(DensityFunction input, RingNoiseCoordinates coordinates) implements DensityFunction {
        @Override public DensitySampler compileSampler(CompileContext compiler) {
            DensitySampler delegate = input.compileSampler(compiler);
            return new DensitySampler() {
                @Override public float sampleValue(SamplerContext context, int x, int y, int z) {
                    return delegate.sampleValue(context, coordinates.noiseX(x, z), y, coordinates.noiseZ(x, z));
                }
                @Override public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
                    int index = 0;
                    for (int z = 0; z < volume.sizeZ(); z++) for (int x = 0; x < volume.sizeX(); x++) {
                        int sourceX = volume.blockX(x), sourceZ = volume.blockZ(z);
                        int mappedX = coordinates.noiseX(sourceX, sourceZ), mappedZ = coordinates.noiseZ(sourceX, sourceZ);
                        for (int y = 0; y < volume.sizeY(); y++)
                            output.set(index++, delegate.sampleValue(context, mappedX, volume.blockY(y), mappedZ));
                    }
                }
            };
        }
        @Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
            return new Cylindrical(input.rewriteChildren(rule), coordinates);
        }
        @Override public Interval range() { return input.range(); }
        @Override public int domainAxes() {
            int axes = input.domainAxes();
            // Cylindrical X and Z both depend on the two intrinsic coordinates.
            return (axes & (AXIS_X | AXIS_Z)) == 0 ? axes : axes | AXIS_X | AXIS_Z;
        }
        @Override public MapCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("Ring samplers are runtime-only");
        }
    }
    private enum Mode { CONTINENTS, SURFACE, FINAL_DENSITY }
    private record Macro(DensityFunction input, RingMacroTerrain macro, Mode mode, int seaLevel) implements DensityFunction {
        @Override public DensitySampler compileSampler(CompileContext compiler) {
            DensitySampler delegate = input.compileSampler(compiler);
            return new DensitySampler() {
                @Override public float sampleValue(SamplerContext context, int x, int y, int z) {
                    return adjust(x, y, z, delegate.sampleValue(context, x, y, z));
                }
                @Override public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
                    delegate.sampleVolume(context, output, volume);
                    int index = 0;
                    for (int z = 0; z < volume.sizeZ(); z++) for (int x = 0; x < volume.sizeX(); x++)
                        for (int y = 0; y < volume.sizeY(); y++, index++)
                            output.set(index, adjust(volume.blockX(x), volume.blockY(y), volume.blockZ(z), output.get(index)));
                }
            };
        }
        private float adjust(int x, int y, int z, float base) {
            double land = macro.landBias(x, z), river = macro.riverInfluence(x, z);
            return (float)switch (mode) {
                case CONTINENTS -> Math.max(-1.0, Math.min(1.0, base + land * 0.62));
                case SURFACE -> Math.min(base + land * 13.0,
                        river > 0 ? seaLevel - 1.0 + (1.0 - river) * 4.0 : Double.POSITIVE_INFINITY);
                case FINAL_DENSITY -> {
                    double band = Math.max(0.0, 1.0 - Math.abs(y - seaLevel) / 64.0);
                    double density = base + land * 0.36 * band;
                    if (river > 0 && y >= seaLevel - 7.0 + (1.0 - river) * 9.0)
                        density = Math.min(density, -0.35 - river * 0.65);
                    yield density;
                }
            };
        }
        @Override public DensityFunction rewriteChildren(DfRewriteRule rule) { return new Macro(rule.rewrite(input), macro, mode, seaLevel); }
        @Override public Interval range() { return Interval.INFINITE; }
        @Override public int domainAxes() { return ALL_AXES; }
        @Override public MapCodec<? extends DensityFunction> codec() { throw new UnsupportedOperationException("Ring samplers are runtime-only"); }
    }
}
