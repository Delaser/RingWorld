package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTickSchedulerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.ticks.WorldGenTickAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Canonicalizes positions committed by a seam-crossing worldgen region. */
@Mixin(WorldGenRegion.class)
abstract class ChunkRegionMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private ChunkAccess center;
    @Shadow @Final private WorldGenTickAccess<?> blockTicks;
    @Shadow @Final private WorldGenTickAccess<?> fluidTicks;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ringworld$attachGenerationSchedulers(CallbackInfo ci) {
        RingGeometry geometry = geometry();
        if (geometry == null) return;
        ((RingTickSchedulerAccess) blockTicks).ringworld$setGeometry(geometry);
        ((RingTickSchedulerAccess) fluidTicks).ringworld$setGeometry(geometry);
    }

    @ModifyVariable(
            method = "getBlockEntity",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/WorldGenRegion;getChunk(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/chunk/ChunkAccess;",
                    shift = At.Shift.AFTER),
            argsOnly = true)
    private BlockPos ringworld$canonicalReadPosition(BlockPos pos) {
        return canonical(pos);
    }

    /**
     * Preserve vanilla's local write-radius validation, then canonicalize the
     * argument before it is committed to the periodic holder. This keeps
     * pending block-entity NBT coordinates consistent with chunk 99 when a
     * feature writes through the -1 alias, for example.
     */
    @ModifyVariable(
            method = "setBlock",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/WorldGenRegion;getChunk(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/chunk/ChunkAccess;",
                    shift = At.Shift.AFTER),
            argsOnly = true)
    private BlockPos ringworld$canonicalWritePosition(BlockPos pos) {
        return canonical(pos);
    }

    /** Select the holder through its nearby alias while retaining canonical NBT coordinates. */
    @Redirect(
            method = "markPosForPostprocessing",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/WorldGenRegion;getChunk(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/chunk/ChunkAccess;"))
    private ChunkAccess ringworld$getPostProcessingChunkThroughLocalAlias(WorldGenRegion region, BlockPos canonicalPos) {
        RingGeometry geometry = geometry();
        if (geometry == null) return region.getChunk(canonicalPos);
        double referenceX = center.getPos().getMiddleBlockX();
        int aliasX = (int) Math.floor(geometry.nearestImageX(canonicalPos.getX(), referenceX));
        return region.getChunk(new BlockPos(aliasX, canonicalPos.getY(), canonicalPos.getZ()));
    }

    @ModifyVariable(method = "addFreshEntity", at = @At("HEAD"), argsOnly = true)
    private Entity ringworld$canonicalGeneratedEntity(Entity entity) {
        RingGeometry geometry = geometry();
        if (geometry != null) {
            entity.setPos(geometry.wrapX(entity.getX()), entity.getY(), entity.getZ());
        }
        return entity;
    }

    private BlockPos canonical(BlockPos pos) {
        RingGeometry geometry = geometry();
        if (geometry == null) return pos;
        int x = geometry.wrapBlockX(pos.getX());
        return x == pos.getX() ? pos : new BlockPos(x, pos.getY(), pos.getZ());
    }

    private RingGeometry geometry() {
        return level.dimension() == Level.OVERWORLD ? RingWorldServer.geometryFor(level) : null;
    }
}
