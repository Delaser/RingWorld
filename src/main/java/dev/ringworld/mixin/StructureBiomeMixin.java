package dev.ringworld.mixin;

import dev.ringworld.world.RingWorldGeneratorAccess;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes every structure's final anchor-biome check match generated RingWorld biomes. */
@Mixin(Structure.class)
abstract class StructureBiomeMixin {
    @Redirect(method = "isValidBiome", require = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/RandomState;sampler()Lnet/minecraft/world/level/biome/Climate$Sampler;"))
    private static Climate.Sampler ringworld$periodicAnchorBiomeSampler(
            RandomState randomState, Structure.GenerationStub stub,
            Structure.GenerationContext context) {
        if (context.chunkGenerator() instanceof RingWorldGeneratorAccess access
                && access.ringworld$getGeometry() != null) {
            return access.ringworld$getPeriodicClimateSampler(randomState);
        }
        return randomState.sampler();
    }
}
