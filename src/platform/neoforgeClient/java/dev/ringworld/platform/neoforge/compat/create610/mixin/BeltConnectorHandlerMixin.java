package dev.ringworld.platform.neoforge.compat.create610.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610ClientDiagnostics;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610ClientCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Maps the stored canonical first pulley only while Create draws its preview. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.belt.item.BeltConnectorHandler",
        remap = false)
abstract class BeltConnectorHandlerMixin {
    @ModifyExpressionValue(
            method = "tick()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;get("
                            + "Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"),
            require = 1, allow = 1)
    private static Object ringworld$nearestStoredPreviewEndpoint(
            Object original, @Local Level level, @Local Player player) {
        if (!(original instanceof BlockPos position)) return original;
        BlockPos image = RingCreate610ClientCoordinates.nearestPreviewPosition(
                level, position, player);
        RingCreate610ClientDiagnostics.recordPreviewFirst(image);
        return image;
    }

    @WrapOperation(
            method = "tick()V",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/belt/item/BeltConnectorItem;"
                            + "canConnect(Lnet/minecraft/world/level/Level;"
                            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Z"),
            require = 1, allow = 1)
    private static boolean ringworld$recordLocalPreviewValidation(
            Level level, BlockPos first, BlockPos selected, Operation<Boolean> original) {
        boolean result = original.call(level, first, selected);
        RingCreate610ClientDiagnostics.recordPreviewCanConnect(first, selected, result);
        return result;
    }
}
