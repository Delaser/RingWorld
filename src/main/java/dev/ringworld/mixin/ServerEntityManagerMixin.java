package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingEntityManagerAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stores every entity in the ring's one canonical circumference plane. */
@Mixin(PersistentEntitySectionManager.class)
abstract class ServerEntityManagerMixin<T extends EntityAccess> implements RingEntityManagerAccess {
    @Shadow @Final private Long2ObjectMap<?> chunkLoadStatuses;
    @Invoker("ensureChunkQueuedForLoad")
    protected abstract void ringworld$queueChunkEntityLoad(long packedPos);
    private RingGeometry ringworld$geometry;

    @Override
    public void ringworld$setGeometry(RingGeometry geometry) {
        this.ringworld$geometry = geometry;
    }

    @Override
    public void ringworld$ensureLoaded(ChunkPos pos) {
        if (ringworld$geometry == null) return;
        int x = Math.floorMod(pos.x, ringworld$geometry.circumferenceChunks());
        ChunkPos canonical = x == pos.x ? pos : new ChunkPos(x, pos.z);
        // FRESH is represented by the load map's default value. Queue its
        // entity read directly: updateChunkStatus also changes visibility and
        // can downgrade an already-ticking seam chunk to merely TRACKED.
        long key = canonical.toLong();
        if (!chunkLoadStatuses.containsKey(key)) {
            ringworld$queueChunkEntityLoad(key);
        }
    }

    /** Canonicalize newly spawned and disk-loaded entities before indexing. */
    @Inject(
            method = "addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z",
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
     * Entity IO and ServerLevel.loadChunks must use the same canonical key or
     * a dedicated-server player join waits forever for an already-loaded
     * seam chunk under another client presentation image.
     */
    @ModifyVariable(
            method = "updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/entity/Visibility;)V",
            at = @At("HEAD"), argsOnly = true)
    private ChunkPos ringworld$canonicalTrackingStatus(ChunkPos pos) {
        if (ringworld$geometry == null) return pos;
        int x = Math.floorMod(pos.x, ringworld$geometry.circumferenceChunks());
        return x == pos.x ? pos : new ChunkPos(x, pos.z);
    }

    @ModifyVariable(method = "areEntitiesLoaded", at = @At("HEAD"), argsOnly = true)
    private long ringworld$canonicalLoadedStatus(long packedPos) {
        if (ringworld$geometry == null) return packedPos;
        ChunkPos pos = new ChunkPos(packedPos);
        int x = Math.floorMod(pos.x, ringworld$geometry.circumferenceChunks());
        return x == pos.x ? packedPos : ChunkPos.asLong(x, pos.z);
    }

    @ModifyVariable(method = "canPositionTick(Lnet/minecraft/world/level/ChunkPos;)Z",
            at = @At("HEAD"), argsOnly = true)
    private ChunkPos ringworld$canonicalTickStatus(ChunkPos pos) {
        if (ringworld$geometry == null) return pos;
        int x = Math.floorMod(pos.x, ringworld$geometry.circumferenceChunks());
        return x == pos.x ? pos : new ChunkPos(x, pos.z);
    }

    //// TODO make this work lol
//    @Redirect(
//            method = "addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z",
//            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;asLong(Lnet/minecraft/core/BlockPos;)J"))
//    private long ringworld$canonicalInitialSection(BlockPos pos, T entity, boolean existing) {
//        if (!(entity instanceof Entity minecraftEntity)
//                || !(minecraftEntity.level() instanceof ServerLevel world)
//                || world.dimension() != Level.OVERWORLD) {
//            return SectionPos.asLong(pos);
//        }
//        RingGeometry geometry = RingWorldServer.geometryFor(world);
//        return SectionPos.asLong(
//                Math.floorDiv(geometry.wrapBlockX(pos.getX()), 16),
//                SectionPos.blockToSectionCoord(pos.getY()),
//                SectionPos.blockToSectionCoord(pos.getZ()));
//    }
}
