package dev.ringworld.mixin;

import dev.ringworld.world.RingWorldGeneratorAccess;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** StructureCheck is constructed before RingWorld configures the density router. */
@Mixin(StructureCheck.class)
abstract class StructureBiomeMixin {
    @Shadow @Final private ChunkGenerator chunkGenerator;
    @Shadow @Final private RandomState randomState;
    @Shadow @Final private Climate.Sampler climateSampler;

    @Redirect(method = "canCreateStructure", require = 1,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/level/levelgen/structure/StructureCheck;climateSampler:Lnet/minecraft/world/level/biome/Climate$Sampler;"))
    private Climate.Sampler ringworld$periodicStructureCheckSampler(StructureCheck check) {
        if (chunkGenerator instanceof RingWorldGeneratorAccess access
                && access.ringworld$getGeometry() != null) {
            return access.ringworld$getPeriodicClimateSampler(randomState);
        }
        return climateSampler;
    }
}
