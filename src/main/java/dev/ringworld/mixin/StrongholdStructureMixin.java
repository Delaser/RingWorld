package dev.ringworld.mixin;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingStrongholdPlacement;
import dev.ringworld.world.RingWorldGeneratorAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fits the complete guaranteed stronghold graph inside the finite ring band. */
@Mixin(StrongholdStructure.class)
abstract class StrongholdStructureMixin {
    @Inject(method = "generatePieces", at = @At("RETURN"))
    private static void ringworld$fitGuaranteedStronghold(
            StructurePiecesBuilder builder, Structure.GenerationContext context, CallbackInfo ci) {
        if (!(context.chunkGenerator() instanceof RingWorldGeneratorAccess access)) return;
        if (!access.ringworld$guaranteesStronghold()) return;
        RingGeometry geometry = access.ringworld$getGeometry();
        if (geometry == null || builder.isEmpty()) return;

        BoundingBox bounds = builder.getBoundingBox()
                .inflatedBy(RingStrongholdPlacement.TERRAIN_ADJUSTMENT_MARGIN_BLOCKS);
        RingStrongholdPlacement.BlockShift shift = RingStrongholdPlacement.fitShift(
                bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ(), geometry);
        if (shift.x() == 0 && shift.z() == 0) return;
        builder.build().pieces().forEach(piece -> piece.move(shift.x(), 0, shift.z()));
        RingWorldMod.LOGGER.info("Shifted guaranteed stronghold by X={}, Z={} to fit the finite ring",
                shift.x(), shift.z());
    }
}
