package dev.ringworld.mixin;

import dev.ringworld.world.RingSurfaceSamplingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Maps both cached 2D and 3D threshold-noise suppliers through ring space. */
@Mixin(targets = {
        "net.minecraft.world.level.levelgen.SurfaceRules$Context$1",
        "net.minecraft.world.level.levelgen.SurfaceRules$Context$2"
})
abstract class SurfaceNoiseThresholdMixin {
    @ModifyArgs(method = "getAsDouble",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D"))
    private void ringworld$mapThresholdNoise(Args args) {
        RingSurfaceSamplingContext.Coordinates mapped =
                RingSurfaceSamplingContext.mapScaled(args.get(0), args.get(2), 1.0);
        args.set(0, mapped.x());
        args.set(2, mapped.z());
    }
}
