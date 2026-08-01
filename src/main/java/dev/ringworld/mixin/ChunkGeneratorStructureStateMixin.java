package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingStrongholdPlacement;
import dev.ringworld.world.RingStructureStateAccess;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces vanilla's unbounded stronghold rings with one in-band canonical start. */
@Mixin(ChunkGeneratorStructureState.class)
abstract class ChunkGeneratorStructureStateMixin implements RingStructureStateAccess {
    @Shadow @Final private long levelSeed;
    @Shadow @Final private Map<Structure, List<StructurePlacement>> placementsForStructure;

    @Unique private volatile @Nullable RingGeometry ringworld$geometry;
    @Unique private volatile boolean ringworld$guaranteeStronghold;
    @Unique private volatile @Nullable List<ChunkPos> ringworld$strongholdPositions;

    @Override
    public void ringworld$setStructurePolicy(RingGeometry geometry, boolean guaranteeStronghold) {
        ringworld$geometry = geometry;
        ringworld$guaranteeStronghold = guaranteeStronghold;
        ringworld$strongholdPositions = null;
    }

    @Inject(method = "getRingPositionsFor", at = @At("RETURN"), cancellable = true)
    private void ringworld$guaranteeStronghold(
            ConcentricRingsStructurePlacement placement,
            CallbackInfoReturnable<List<ChunkPos>> cir) {
        RingGeometry geometry = ringworld$geometry;
        if (geometry == null || !ringworld$guaranteeStronghold
                || !ringworld$isStrongholdPlacement(placement)) return;

        List<ChunkPos> positions = ringworld$strongholdPositions;
        if (positions == null) {
            RingStrongholdPlacement.StartChunk start =
                    RingStrongholdPlacement.guaranteedStart(levelSeed, geometry);
            positions = List.of(new ChunkPos(start.chunkX(), start.chunkZ()));
            ringworld$strongholdPositions = positions;
        }
        cir.setReturnValue(positions);
    }

    @Unique
    private boolean ringworld$isStrongholdPlacement(StructurePlacement placement) {
        for (Map.Entry<Structure, List<StructurePlacement>> entry : placementsForStructure.entrySet()) {
            if (entry.getKey() instanceof StrongholdStructure
                    && entry.getValue().contains(placement)) return true;
        }
        return false;
    }
}
