package dev.ringworld.mixin;

import dev.ringworld.world.RingCoordinateDensityFunction;
import org.spongepowered.asm.mixin.Mixin;

/** Tags coordinate-consuming density leaves without exposing protected vanilla types. */
@Mixin(targets = {
        "net.minecraft.world.gen.densityfunction.DensityFunctionTypes$Noise",
        "net.minecraft.world.gen.densityfunction.DensityFunctionTypes$Shift",
        "net.minecraft.world.gen.densityfunction.DensityFunctionTypes$ShiftA",
        "net.minecraft.world.gen.densityfunction.DensityFunctionTypes$ShiftB",
        "net.minecraft.world.gen.densityfunction.DensityFunctionTypes$ShiftedNoise",
        "net.minecraft.world.gen.densityfunction.DensityFunctionTypes$WeirdScaledSampler",
        "net.minecraft.world.gen.densityfunction.DensityFunctionTypes$EndIslands"
})
abstract class DensityCoordinateConsumerMixin implements RingCoordinateDensityFunction { }
