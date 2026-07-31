package dev.ringworld.mixin;

import dev.ringworld.world.RingCoordinateDensityFunction;
import org.spongepowered.asm.mixin.Mixin;

/** Tags coordinate-consuming density leaves without exposing protected vanilla types. */
@Mixin(targets = {
        "net.minecraft.world.level.levelgen.DensityFunctions$Noise",
        "net.minecraft.world.level.levelgen.DensityFunctions$Shift",
        "net.minecraft.world.level.levelgen.DensityFunctions$ShiftA",
        "net.minecraft.world.level.levelgen.DensityFunctions$ShiftB",
        "net.minecraft.world.level.levelgen.DensityFunctions$ShiftedNoise",
        "net.minecraft.world.level.levelgen.DensityFunctions$WeirdScaledSampler",
        "net.minecraft.world.level.levelgen.DensityFunctions$EndIslandDensityFunction"
})
abstract class DensityCoordinateConsumerMixin implements RingCoordinateDensityFunction { }
