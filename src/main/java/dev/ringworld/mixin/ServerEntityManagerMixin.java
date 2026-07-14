package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingEntityManagerAccess;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingStatus;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stores every entity in the ring's one canonical circumference plane. */
@Mixin(ServerEntityManager.class)
abstract class ServerEntityManagerMixin<T extends EntityLike> implements RingEntityManagerAccess {
    @Shadow @Final private Long2ObjectMap<?> managedStatuses;
    @Shadow public abstract void updateTrackingStatus(ChunkPos pos, EntityTrackingStatus trackingStatus);
    private RingGeometry ringworld$geometry;

    @Override
    public void ringworld$setGeometry(RingGeometry geometry) {
        this.ringworld$geometry = geometry;
    }

    @Override
    public void ringworld$ensureLoaded(ChunkPos pos) {
        if (ringworld$geometry == null) return;
        int x = Math.floorMod(pos.x, ringworld$geometry.circumferenceBlocks() / 16);
        ChunkPos canonical = x == pos.x ? pos : new ChunkPos(x, pos.z);
        // FRESH is represented by the map's default value. Avoid changing the
        // tracking level of chunks whose read is already pending or complete.
        if (!managedStatuses.containsKey(canonical.toLong())) {
            updateTrackingStatus(canonical, EntityTrackingStatus.TRACKED);
        }
    }

    /** Canonicalize newly spawned and disk-loaded entities before indexing. */
    @Inject(
            method = "addEntity(Lnet/minecraft/world/entity/EntityLike;Z)Z",
            at = @At("HEAD"))
    private void ringworld$canonicalEntityPosition(T entity, boolean existing,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (ringworld$geometry != null && entity instanceof Entity minecraftEntity) {
            RingWorldServer.canonicalizeEntityPosition(minecraftEntity, ringworld$geometry);
        }
    }

    /**
     * Chunk status notifications can retain the logical ticket coordinate
     * even though the holder itself was folded into the finite ring graph.
     * Entity IO and ServerWorld.loadChunks must use the same canonical key or
     * a dedicated-server player join waits forever for an already-loaded
     * seam chunk under another client presentation image.
     */
    @ModifyVariable(
            method = "updateTrackingStatus(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/entity/EntityTrackingStatus;)V",
            at = @At("HEAD"), argsOnly = true)
    private ChunkPos ringworld$canonicalTrackingStatus(ChunkPos pos) {
        if (ringworld$geometry == null) return pos;
        int x = Math.floorMod(pos.x, ringworld$geometry.circumferenceBlocks() / 16);
        return x == pos.x ? pos : new ChunkPos(x, pos.z);
    }

    @ModifyVariable(method = "isLoaded", at = @At("HEAD"), argsOnly = true)
    private long ringworld$canonicalLoadedStatus(long packedPos) {
        if (ringworld$geometry == null) return packedPos;
        ChunkPos pos = new ChunkPos(packedPos);
        int x = Math.floorMod(pos.x, ringworld$geometry.circumferenceBlocks() / 16);
        return x == pos.x ? packedPos : ChunkPos.toLong(x, pos.z);
    }

    @ModifyVariable(method = {
            "shouldTick(Lnet/minecraft/util/math/ChunkPos;)Z",
            "shouldTickTest(Lnet/minecraft/util/math/ChunkPos;)Z"
    }, at = @At("HEAD"), argsOnly = true)
    private ChunkPos ringworld$canonicalTickStatus(ChunkPos pos) {
        if (ringworld$geometry == null) return pos;
        int x = Math.floorMod(pos.x, ringworld$geometry.circumferenceBlocks() / 16);
        return x == pos.x ? pos : new ChunkPos(x, pos.z);
    }

    @Redirect(
            method = "addEntity(Lnet/minecraft/world/entity/EntityLike;Z)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/ChunkSectionPos;toLong(Lnet/minecraft/util/math/BlockPos;)J"))
    private long ringworld$canonicalInitialSection(BlockPos pos, T entity, boolean existing) {
        if (!(entity instanceof Entity minecraftEntity)
                || !(minecraftEntity.getEntityWorld() instanceof ServerWorld world)
                || world.getRegistryKey() != World.OVERWORLD) {
            return ChunkSectionPos.toLong(pos);
        }
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        return ChunkSectionPos.asLong(
                Math.floorDiv(geometry.wrapBlockX(pos.getX()), 16),
                ChunkSectionPos.getSectionCoord(pos.getY()),
                ChunkSectionPos.getSectionCoord(pos.getZ()));
    }
}
