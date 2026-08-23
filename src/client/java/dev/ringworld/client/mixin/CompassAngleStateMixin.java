package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingMapCompassSupport;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Points spawn, lodestone, and recovery compass needles at the nearest periodic target image. */
@Mixin(CompassItemPropertyFunction.class)
abstract class CompassAngleStateMixin {
    @Inject(method = "isValidCompassTargetPos", at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicTargetValidity(
            Entity owner, GlobalPos target, CallbackInfoReturnable<Boolean> cir) {
        if (target == null || target.dimension() != owner.level().dimension()) return;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || owner.level().dimension() != Level.OVERWORLD) return;
        Vec3 targetPosition = Vec3.atCenterOf(target.pos());
        Vec3 ownerPosition = owner.position();
        cir.setReturnValue(RingMapCompassSupport.isCompassTargetDistinct(
                geometry,
                targetPosition.x, targetPosition.y, targetPosition.z,
                ownerPosition.x, ownerPosition.y, ownerPosition.z));
    }

    @Inject(method = "getAngleFromEntityToPos", at = @At("HEAD"), cancellable = true)
    private void ringworld$nearestCompassTarget(
            Entity owner, BlockPos target, CallbackInfoReturnable<Double> cir) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || owner.level().dimension() != Level.OVERWORLD) return;
        Vec3 ownerPosition = owner.position();
        double targetX = RingMapCompassSupport.nearestCompassTargetX(
                geometry, target.getX() + 0.5, ownerPosition.x);
        cir.setReturnValue(Math.atan2(target.getZ() + 0.5 - ownerPosition.z,
                targetX - ownerPosition.x) / (Math.PI * 2.0));
    }
}
