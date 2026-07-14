package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTickSchedulerAccess;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.tick.MultiTickScheduler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Canonicalizes positions committed by a seam-crossing worldgen region. */
@Mixin(ChunkRegion.class)
abstract class ChunkRegionMixin {
    @Shadow @Final private ServerWorld world;
    @Shadow @Final private Chunk centerPos;
    @Shadow @Final private MultiTickScheduler<?> blockTickScheduler;
    @Shadow @Final private MultiTickScheduler<?> fluidTickScheduler;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ringworld$attachGenerationSchedulers(CallbackInfo ci) {
        RingGeometry geometry = geometry();
        if (geometry == null) return;
        ((RingTickSchedulerAccess) blockTickScheduler).ringworld$setGeometry(geometry);
        ((RingTickSchedulerAccess) fluidTickScheduler).ringworld$setGeometry(geometry);
    }

    @ModifyVariable(
            method = "getBlockEntity",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/ChunkRegion;getChunk(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/chunk/Chunk;",
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
            method = "setBlockState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/ChunkRegion;getChunk(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/chunk/Chunk;",
                    shift = At.Shift.AFTER),
            argsOnly = true)
    private BlockPos ringworld$canonicalWritePosition(BlockPos pos) {
        return canonical(pos);
    }

    /** Select the holder through its nearby alias while retaining canonical NBT coordinates. */
    @Redirect(
            method = "markBlockForPostProcessing",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/ChunkRegion;getChunk(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/chunk/Chunk;"))
    private Chunk ringworld$getPostProcessingChunkThroughLocalAlias(ChunkRegion region, BlockPos canonicalPos) {
        RingGeometry geometry = geometry();
        if (geometry == null) return region.getChunk(canonicalPos);
        double referenceX = centerPos.getPos().getCenterX();
        int aliasX = (int) Math.floor(geometry.nearestImageX(canonicalPos.getX(), referenceX));
        return region.getChunk(new BlockPos(aliasX, canonicalPos.getY(), canonicalPos.getZ()));
    }

    @ModifyVariable(method = "spawnEntity", at = @At("HEAD"), argsOnly = true)
    private Entity ringworld$canonicalGeneratedEntity(Entity entity) {
        RingGeometry geometry = geometry();
        if (geometry != null) {
            entity.setPosition(geometry.wrapX(entity.getX()), entity.getY(), entity.getZ());
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
        return world.getRegistryKey() == World.OVERWORLD ? RingWorldServer.geometryFor(world) : null;
    }
}
