package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingMapCompassSupport;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Points spawn, lodestone, and recovery compass needles at the nearest periodic target image. */
@Mixin(CompassAngleState.class)
abstract class CompassAngleStateMixin {
    @Redirect(method = "calculate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/properties/numeric/CompassAngleState;isValidCompassTargetPos(Lnet/minecraft/world/entity/ItemOwner;Lnet/minecraft/core/GlobalPos;)Z"))
    private static boolean ringworld$periodicTargetValidity(ItemOwner owner, GlobalPos target) {
        if (target == null || target.dimension() != owner.level().dimension()) return false;
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || owner.level().dimension() != Level.OVERWORLD) {
            return !(target.pos().distToCenterSqr(owner.position()) < 1.0E-5F);
        }
        Vec3 targetPosition = Vec3.atCenterOf(target.pos());
        Vec3 ownerPosition = owner.position();
        return RingMapCompassSupport.isCompassTargetDistinct(
                geometry,
                targetPosition.x, targetPosition.y, targetPosition.z,
                ownerPosition.x, ownerPosition.y, ownerPosition.z);
    }

    @Redirect(method = "getRotationTowardsCompassTarget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/properties/numeric/CompassAngleState;getAngleFromEntityToPos(Lnet/minecraft/world/entity/ItemOwner;Lnet/minecraft/core/BlockPos;)D"))
    private static double ringworld$nearestCompassTarget(ItemOwner owner, BlockPos target) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || owner.level().dimension() != Level.OVERWORLD) {
            return vanillaAngle(owner, target);
        }
        Vec3 ownerPosition = owner.position();
        double targetX = RingMapCompassSupport.nearestCompassTargetX(
                geometry, target.getX() + 0.5, ownerPosition.x);
        return Math.atan2(target.getZ() + 0.5 - ownerPosition.z, targetX - ownerPosition.x) / (Math.PI * 2.0);
    }

    private static double vanillaAngle(ItemOwner owner, BlockPos target) {
        Vec3 targetPosition = Vec3.atCenterOf(target);
        Vec3 ownerPosition = owner.position();
        return Math.atan2(targetPosition.z - ownerPosition.z, targetPosition.x - ownerPosition.x) / (Math.PI * 2.0);
    }
}
