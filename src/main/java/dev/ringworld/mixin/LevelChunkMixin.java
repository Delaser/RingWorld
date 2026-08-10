package dev.ringworld.mixin;

import dev.ringworld.RingWorldMod;
import dev.ringworld.server.RingBlockEntityLoadContext;
import dev.ringworld.server.RingBlockEntityOwnership;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunk.EntityCreationType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps runtime block-state and block-entity ownership on the one canonical
 * Overworld X plane. A neighbouring lookup may legitimately reach the same
 * chunk through X=-1 or X=C, but those aliases must never become map keys.
 */
@Mixin(LevelChunk.class)
abstract class LevelChunkMixin {
    @Shadow @Final private Level level;

    @ModifyVariable(
            method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"), argsOnly = true)
    private BlockPos ringworld$canonicalBlockStatePosition(BlockPos position) {
        return canonical(position);
    }

    /**
     * Preserve a pre-existing alias entry for explicit recovery rather than
     * hiding or overwriting it. Clean worlds never take this branch.
     */
    @Inject(
            method = "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            at = @At("HEAD"), cancellable = true)
    private void ringworld$readQuarantinedAlias(BlockPos position, EntityCreationType creationType,
                                                 CallbackInfoReturnable<BlockEntity> cir) {
        BlockPos canonical = canonical(position);
        if (!canonical.equals(position)) {
            BlockEntity exact = ((LevelChunk) (Object) this).getBlockEntities().get(position);
            if (exact != null) cir.setReturnValue(exact.isRemoved() ? null : exact);
        }
    }

    @ModifyVariable(
            method = "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            at = @At("HEAD"), argsOnly = true)
    private BlockPos ringworld$canonicalBlockEntityReadPosition(BlockPos position) {
        BlockPos canonical = canonical(position);
        if (!canonical.equals(position)
                && ((LevelChunk) (Object) this).getBlockEntityNbt(position) != null) {
            return position;
        }
        return canonical;
    }

    @Redirect(
            method = "runPostLoad",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;run(Lnet/minecraft/world/level/chunk/LevelChunk;)V"))
    private void ringworld$preserveSavedAliasesDuringPostLoad(
            LevelChunk.PostLoadProcessor processor, LevelChunk chunk) {
        if (level instanceof ServerLevel serverLevel && serverLevel.dimension() == Level.OVERWORLD) {
            RingGeometry geometry = RingWorldServer.geometryFor(serverLevel);
            RingBlockEntityLoadContext.withGeometry(
                    geometry, () -> processor.run(chunk));
            RingBlockEntityOwnership.reconcileLoadedAliases(chunk, geometry);
        } else {
            processor.run(chunk);
        }
    }

    @ModifyVariable(
            method = "getBlockEntityNbtForSaving",
            at = @At("HEAD"), argsOnly = true)
    private BlockPos ringworld$canonicalBlockEntitySavePosition(BlockPos position) {
        return exactAliasOrCanonical(position);
    }

    @ModifyVariable(
            method = "removeBlockEntity",
            at = @At("HEAD"), argsOnly = true)
    private BlockPos ringworld$canonicalBlockEntityRemovalPosition(BlockPos position) {
        return exactAliasOrCanonical(position);
    }

    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    private void ringworld$canonicalBlockEntityRegistration(BlockEntity blockEntity, CallbackInfo ci) {
        BlockPos position = blockEntity.getBlockPos();
        BlockPos canonical = canonical(position);
        if (canonical.equals(position)) return;

        BlockEntity canonicalOwner = ((LevelChunk) (Object) this).getBlockEntities().get(canonical);
        boolean canonicalPending = ((LevelChunk) (Object) this).getBlockEntityNbt(canonical) != null;
        if (RingBlockEntityLoadContext.isActive()) return;
        if (canonicalOwner == null && !canonicalPending) {
            ((BlockEntityPositionAccessor) blockEntity).ringworld$setWorldPosition(canonical);
            return;
        }

        if (canonicalOwner == blockEntity) return;

        RingWorldMod.LOGGER.warn(
                "Preserving conflicting alias block entity {} at {} because canonical {} already owns "
                        + "or has pending saved data for {}; "
                        + "back up the world and recover both inventories manually",
                blockEntity.getType(), position, canonical,
                canonicalOwner == null ? "another block entity" : canonicalOwner.getType());
    }

    private BlockPos exactAliasOrCanonical(BlockPos position) {
        BlockPos canonical = canonical(position);
        if (!canonical.equals(position)
                && ((LevelChunk) (Object) this).getBlockEntities().containsKey(position)) {
            return position;
        }
        return canonical;
    }

    private BlockPos canonical(BlockPos position) {
        if (!(level instanceof ServerLevel serverLevel)
                || serverLevel.dimension() != Level.OVERWORLD) {
            return position;
        }
        RingGeometry geometry = RingWorldServer.geometryFor(serverLevel);
        int canonicalX = geometry.wrapBlockX(position.getX());
        return canonicalX == position.getX()
                ? position
                : new BlockPos(canonicalX, position.getY(), position.getZ());
    }
}
