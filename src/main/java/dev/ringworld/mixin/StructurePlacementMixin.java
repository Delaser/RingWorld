package dev.ringworld.mixin;

import dev.ringworld.world.RingStructureStateAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructurePlacement.class)
abstract class StructurePlacementMixin {
    @Inject(method = "isStructureChunk", at = @At("RETURN"), cancellable = true)
    private void ringworld$allowSavedMonument(ChunkGeneratorStructureState state, int x, int z,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (!(state instanceof RingStructureStateAccess access)
                || !access.ringworld$isGuaranteedOceanMonumentCandidate(this, x, z)) return;
        StructurePlacement self = (StructurePlacement)(Object)this;
        cir.setReturnValue(self.applyAdditionalChunkRestrictions(x, z, state.getLevelSeed())
                && self.applyInteractionsWithOtherStructures(state, x, z));
    }
}
