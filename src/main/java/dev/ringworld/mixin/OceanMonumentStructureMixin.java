package dev.ringworld.mixin;

import dev.ringworld.world.RingWorldGeneratorAccess;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes monument biome checks use the same periodic climate sampler as RingWorld chunks. */
@Mixin(OceanMonumentStructure.class)
abstract class OceanMonumentStructureMixin {
    @Redirect(method = "findGenerationPoint", require = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/RandomState;sampler()Lnet/minecraft/world/level/biome/Climate$Sampler;"))
    private Climate.Sampler ringworld$periodicMonumentSampler(
            RandomState randomState, Structure.GenerationContext context) {
        if (context.chunkGenerator() instanceof RingWorldGeneratorAccess access
                && access.ringworld$getGeometry() != null) {
            return access.ringworld$getPeriodicClimateSampler(randomState);
        }
        return randomState.sampler();
    }
}
