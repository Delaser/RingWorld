package dev.ringworld.mixin;

import dev.ringworld.RingWorldMod;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTickSchedulerAccess;
import dev.ringworld.world.RingEntityManagerAccess;
import net.minecraft.block.Block;
import net.minecraft.fluid.Fluid;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Position;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.tick.WorldTickScheduler;

/** Canonicalizes world-facing loaded-chunk checks such as spawn preparation. */
@Mixin(ServerWorld.class)
abstract class ServerWorldMixin {
    @Shadow @Final private WorldTickScheduler<Block> blockTickScheduler;
    @Shadow @Final private WorldTickScheduler<Fluid> fluidTickScheduler;
    @Shadow @Final private ServerEntityManager<Entity> entityManager;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ringworld$attachTickSchedulerGeometry(CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (world.getRegistryKey() != World.OVERWORLD) return;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        ((RingEntityManagerAccess) entityManager).ringworld$setGeometry(geometry);
        ((RingTickSchedulerAccess) blockTickScheduler).ringworld$setGeometry(geometry);
        ((RingTickSchedulerAccess) fluidTickScheduler).ringworld$setGeometry(geometry);
    }

    @ModifyVariable(method = "isChunkLoaded", at = @At("HEAD"), argsOnly = true)
    private long ringworld$canonicalLoadedChunkKey(long packedPos) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (world.getRegistryKey() != World.OVERWORLD) return packedPos;
        ChunkPos pos = new ChunkPos(packedPos);
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        return ChunkPos.toLong(RingChunkCoordinates.wrapChunkX(pos.x, geometry), pos.z);
    }

    @Inject(method = "loadChunks", at = @At("HEAD"))
    private void ringworld$preparePeriodicEntityRegion(ChunkPos center, int radius, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (world.getRegistryKey() != World.OVERWORLD) return;

        RingEntityManagerAccess access = (RingEntityManagerAccess) entityManager;
        ChunkPos.stream(center, radius).forEach(access::ringworld$ensureLoaded);

        if (Boolean.getBoolean("ringworld.multiplayerTest")) {
            RingWorldMod.LOGGER.info("[multiplayer] waiting for entity chunks around {},{} radius={}",
                    center.x, center.z, radius);
        }
    }

    /**
     * The primary entity tick loop checks an entity's live chunk directly
     * against the finite chunk-level graph. Use its canonical storage chunk
     * for that eligibility lookup so moving entities do not freeze at x=C.
     */
    @Redirect(
            method = "method_31420",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/ChunkPos;toLong()J"))
    private long ringworld$canonicalEntityTickChunk(ChunkPos pos) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (world.getRegistryKey() != World.OVERWORLD) return pos.toLong();
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        return ChunkPos.toLong(RingChunkCoordinates.wrapChunkX(pos.x, geometry), pos.z);
    }

    @ModifyVariable(
            method = {"shouldTickTestAt", "shouldTickChunkAt", "canSpawnEntitiesAt"},
            at = @At("HEAD"), argsOnly = true)
    private ChunkPos ringworld$canonicalTickQuery(ChunkPos pos) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (world.getRegistryKey() != World.OVERWORLD) return pos;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        return new ChunkPos(RingChunkCoordinates.wrapChunkX(pos.x, geometry), pos.z);
    }

    @ModifyVariable(method = "shouldTickEntityAt", at = @At("HEAD"), argsOnly = true)
    private BlockPos ringworld$canonicalEntityTickQuery(BlockPos pos) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (world.getRegistryKey() != World.OVERWORLD) return pos;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        int x = geometry.wrapBlockX(pos.getX());
        return x == pos.getX() ? pos : new BlockPos(x, pos.getY(), pos.getZ());
    }

    /** Particle and other proximity packets must cross the joined edge too. */
    @Redirect(
            method = "sendToPlayerIfNearby",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;isWithinDistance(Lnet/minecraft/util/math/Position;D)Z"))
    private boolean ringworld$periodicPacketDistance(BlockPos playerPos, Position eventPos, double range) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (world.getRegistryKey() != World.OVERWORLD) return playerPos.isWithinDistance(eventPos, range);
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        double dx = geometry.shortestCircumferenceDelta(playerPos.getX(), eventPos.getX());
        double dy = playerPos.getY() - eventPos.getY();
        double dz = playerPos.getZ() - eventPos.getZ();
        return dx * dx + dy * dy + dz * dz < range * range;
    }
}
