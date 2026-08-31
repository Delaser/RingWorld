package dev.ringworld.mixin;

import dev.ringworld.world.RingStructureStateAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the optional second candidate grid before vanilla applies frequency and exclusion rules. */
@Mixin(RandomSpreadStructurePlacement.class)
abstract class RandomSpreadStructurePlacementMixin {
    @Inject(method = "isPlacementChunk", at = @At("RETURN"), cancellable = true)
    private void ringworld$additionalCandidate(ChunkGeneratorStructureState state, int chunkX, int chunkZ,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && state instanceof RingStructureStateAccess access
                && access.ringworld$isAdditionalStructureCandidate(this, chunkX, chunkZ)) {
            cir.setReturnValue(true);
        }
    }
}
