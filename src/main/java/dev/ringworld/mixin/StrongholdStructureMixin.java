package dev.ringworld.mixin;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingStrongholdPlacement;
import dev.ringworld.world.RingWorldGeneratorAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
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

        java.util.List<StructurePiece> pieces = builder.build().pieces();
        BoundingBox bounds = builder.getBoundingBox()
                .inflatedBy(RingStrongholdPlacement.TERRAIN_ADJUSTMENT_MARGIN_BLOCKS);
        BoundingBox portalBounds = pieces.stream()
                .filter(StrongholdPieces.PortalRoom.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "guaranteed stronghold generated without a portal room"))
                .getBoundingBox()
                .inflatedBy(RingStrongholdPlacement.TERRAIN_ADJUSTMENT_MARGIN_BLOCKS);
        RingStrongholdPlacement.FitPlan plan = RingStrongholdPlacement.fitRequiredPiece(
                bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ(),
                portalBounds.minX(), portalBounds.maxX(), portalBounds.minZ(), portalBounds.maxZ(),
                geometry);
        RingStrongholdPlacement.BlockShift shift = plan.shift();
        if (shift.x() == 0 && shift.z() == 0) return;
        pieces.forEach(piece -> piece.move(shift.x(), 0, shift.z()));
        RingWorldMod.LOGGER.info(
                "Shifted guaranteed stronghold by X={}, Z={} to fit the finite ring "
                        + "(optional graph exceeds finite X={}, Z={})",
                shift.x(), shift.z(),
                plan.graphExceedsBoundsX(), plan.graphExceedsBoundsZ());
    }
}
