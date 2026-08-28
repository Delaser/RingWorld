package dev.ringworld.platform.neoforge.compat.create610.mixin;

import dev.ringworld.platform.neoforge.compat.create610.RingCreate610ServerCoordinates;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610TankAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Repairs fluid-tank controller ownership on every server storage boundary. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.tank.FluidTankBlockEntity", remap = false)
abstract class FluidTankBlockEntityMixin implements RingCreate610TankAccess {
    @Shadow private BlockPos controller;
    @Shadow public abstract FluidTank getTankInventory();

    @Override
    public IFluidHandler ringworld$tankInventory() {
        return getTankInventory();
    }

    @ModifyVariable(method = "setController(Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"), argsOnly = true, require = 1)
    private BlockPos ringworld$canonicalControllerAssignment(BlockPos value) {
        return repair(value);
    }

    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;"
            + "Lnet/minecraft/core/HolderLookup$Provider;Z)V",
            at = @At("RETURN"), require = 1)
    private void ringworld$repairControllerAfterRead(
            CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
            CallbackInfo ci) {
        controller = repair(controller);
    }

    @Inject(method = "write(Lnet/minecraft/nbt/CompoundTag;"
            + "Lnet/minecraft/core/HolderLookup$Provider;Z)V",
            at = @At("HEAD"), require = 1)
    private void ringworld$repairControllerBeforeWrite(
            CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
            CallbackInfo ci) {
        controller = repair(controller);
    }

    private BlockPos repair(BlockPos value) {
        return value == null ? null : RingCreate610ServerCoordinates.canonicalController(
                (BlockEntity) (Object) this, value);
    }
}
