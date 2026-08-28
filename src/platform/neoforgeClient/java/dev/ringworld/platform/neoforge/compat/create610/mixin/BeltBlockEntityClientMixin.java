package dev.ringworld.platform.neoforge.compat.create610.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610ClientCoordinates;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610ClientDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps a belt controller in the owner's transient client presentation chart. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.belt.BeltBlockEntity", remap = false)
abstract class BeltBlockEntityClientMixin {
    @Shadow private BlockPos controller;
    @Unique private boolean ringworld$clientControllerRepairPending;

    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;"
            + "Lnet/minecraft/core/HolderLookup$Provider;Z)V",
            at = @At("RETURN"), require = 1)
    private void ringworld$presentControllerAfterRead(
            CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
            CallbackInfo ci) {
        if (!clientPacket || controller == null) return;
        BlockEntity owner = (BlockEntity) (Object) this;
        RingCreate610ClientDiagnostics.recordControllerRead(owner);
        BlockPos presentation = RingCreate610ClientCoordinates.nearestControllerOrNull(
                owner, controller);
        ringworld$clientControllerRepairPending = presentation == null;
        if (presentation != null) controller = presentation;
    }

    @Inject(method = "tick()V", at = @At("HEAD"), require = 1)
    private void ringworld$finishDeferredClientControllerRepair(CallbackInfo ci) {
        if (!ringworld$clientControllerRepairPending || controller == null) return;
        BlockPos presentation = RingCreate610ClientCoordinates.nearestControllerOrNull(
                (BlockEntity) (Object) this, controller);
        if (presentation == null) return;
        controller = presentation;
        ringworld$clientControllerRepairPending = false;
        RingCreate610ClientDiagnostics.recordDeferredControllerRepair();
    }

    @ModifyExpressionValue(
            method = "write(Lnet/minecraft/nbt/CompoundTag;"
                    + "Lnet/minecraft/core/HolderLookup$Provider;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtUtils;writeBlockPos("
                            + "Lnet/minecraft/core/BlockPos;)Lnet/minecraft/nbt/Tag;"),
            require = 1, allow = 1)
    private Tag ringworld$canonicalControllerTag(Tag original) {
        BlockEntity owner = (BlockEntity) (Object) this;
        if (!RingCreate610ClientCoordinates.isOwningClientLevel(owner)) return original;
        BlockPos canonical = RingCreate610ClientCoordinates.canonicalController(owner, controller);
        return canonical == controller
                ? original
                : net.minecraft.nbt.NbtUtils.writeBlockPos(canonical);
    }
}
