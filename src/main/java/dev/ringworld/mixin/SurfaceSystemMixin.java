package dev.ringworld.mixin;

import dev.ringworld.world.RingSurfaceSamplingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import net.minecraft.world.level.levelgen.SurfaceSystem;

/** Redirects vanilla surface-only noise/random coordinates through the saved ring mapping. */
@Mixin(SurfaceSystem.class)
abstract class SurfaceSystemMixin {
    @ModifyArgs(method = {"getSurfaceDepth", "getSurfaceSecondary", "getBand"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D"))
    private void ringworld$mapUnitNoise(Args args) { mapNoise(args, 1.0); }

    @ModifyArgs(method = "erodedBadlandsExtension",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D", ordinal = 0))
    private void ringworld$mapBadlandsSurface(Args args) { mapNoise(args, 1.0); }

    @ModifyArgs(method = "erodedBadlandsExtension",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D", ordinal = 1))
    private void ringworld$mapBadlandsPillar(Args args) { mapNoise(args, 0.2); }

    @ModifyArgs(method = "erodedBadlandsExtension",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D", ordinal = 2))
    private void ringworld$mapBadlandsRoof(Args args) { mapNoise(args, 0.75); }

    @ModifyArgs(method = "frozenOceanExtension",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D", ordinal = 0))
    private void ringworld$mapIcebergSurface(Args args) { mapNoise(args, 1.0); }

    @ModifyArgs(method = "frozenOceanExtension",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D", ordinal = 1))
    private void ringworld$mapIcebergPillar(Args args) { mapNoise(args, 1.28); }

    @ModifyArgs(method = "frozenOceanExtension",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D", ordinal = 2))
    private void ringworld$mapIcebergRoof(Args args) { mapNoise(args, 1.17); }

    @ModifyArgs(method = {"getSurfaceDepth", "frozenOceanExtension"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;at(III)Lnet/minecraft/util/RandomSource;"))
    private void ringworld$mapSurfaceRandom(Args args) {
        RingSurfaceSamplingContext.BlockCoordinates mapped =
                RingSurfaceSamplingContext.mapBlock(args.get(0), args.get(2));
        args.set(0, mapped.x());
        args.set(2, mapped.z());
    }

    private static void mapNoise(Args args, double scale) {
        RingSurfaceSamplingContext.Coordinates mapped =
                RingSurfaceSamplingContext.mapScaled(args.get(0), args.get(2), scale);
        args.set(0, mapped.x());
        args.set(2, mapped.z());
    }
}
