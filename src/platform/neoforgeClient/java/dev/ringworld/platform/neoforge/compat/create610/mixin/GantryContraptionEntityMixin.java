package dev.ringworld.platform.neoforge.compat.create610.mixin;

import com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610ClientDiagnostics;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610GantryCoordinates;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps Create's client-only gantry smoothing accumulator in one X chart. */
@Mixin(value = GantryContraptionEntity.class, remap = false)
abstract class GantryContraptionEntityMixin {
    @Shadow Direction movementAxis;
    @Shadow double clientOffsetDiff;
    @Shadow public abstract double getAxisCoord();

    @Inject(
            method = "tickContraption()V",
            at = @At(value = "FIELD",
                    target = "Lcom/simibubi/create/content/contraptions/gantry/"
                            + "GantryContraptionEntity;clientOffsetDiff:D",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0,
                    shift = At.Shift.BEFORE),
            require = 1,
            expect = 1,
            allow = 1)
    private void ringworld$projectClientOffsetBeforeNativeDecay(CallbackInfo ci) {
        GantryContraptionEntity entity = (GantryContraptionEntity) (Object) this;
        Minecraft client = Minecraft.getInstance();
        Level level = entity.level();
        if (level == null || level != client.level || !level.isClientSide
                || level.dimension() != Level.OVERWORLD) {
            return;
        }
        double originalOffset = clientOffsetDiff;
        clientOffsetDiff = RingCreate610GantryCoordinates.nearestClientOffset(
                getAxisCoord(), clientOffsetDiff, ClientRingState.geometry(),
                movementAxis != null && movementAxis.getAxis() == Direction.Axis.X);
        RingCreate610ClientDiagnostics.recordGantryOffsetProjection(
                entity.getId(), getAxisCoord(), originalOffset, clientOffsetDiff);
    }
}
