package dev.ringworld.platform.neoforge.compat.create610.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610ServerCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Narrows periodic projection to the vertical multiblock bounds comparison. */
@Pseudo
@Mixin(targets = "com.simibubi.create.api.connectivity.ConnectivityHandler", remap = false)
abstract class ConnectivityHandlerMixin {
    @WrapOperation(
            method = "tryToFormNewMultiOfWidth(Lnet/minecraft/world/level/block/entity/BlockEntity;"
                    + "ILcom/simibubi/create/api/connectivity/ConnectivityHandler$SearchCache;Z)I",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BlockEntity;getBlockPos()"
                            + "Lnet/minecraft/core/BlockPos;",
                    ordinal = 1),
            require = 1, allow = 1)
    private static BlockPos ringworld$nearestVerticalControllerPosition(
            BlockEntity controller, Operation<BlockPos> original,
            @Local(name = "origin") BlockPos origin,
            @Local(name = "axis") Direction.Axis axis) {
        BlockPos controllerPosition = original.call(controller);
        if (axis != Direction.Axis.Y || controller.getLevel() == null) {
            return controllerPosition;
        }
        return RingCreate610ServerCoordinates.nearestRelationshipPosition(
                controller.getLevel(), controllerPosition, origin.getX());
    }
}
