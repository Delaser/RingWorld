package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRaidSupport;
import dev.ringworld.world.RingTickSchedulerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelTicks;
import dev.ringworld.world.RingEntityManagerAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Canonicalizes world-facing loaded-chunk checks such as spawn preparation. */
@Mixin(ServerLevel.class)
abstract class ServerWorldMixin {
    @Shadow @Final private LevelTicks<Block> blockTicks;
    @Shadow @Final private LevelTicks<Fluid> fluidTicks;
    @Shadow @Final private PersistentEntitySectionManager<Entity> entityManager;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ringworld$attachTickSchedulerGeometry(CallbackInfo ci) {
        ServerLevel world = (ServerLevel) (Object) this;
        if (world.dimension() != Level.OVERWORLD) return;
        RingGeometry geometry = RingWorldServer.attachTickSchedulerGeometry(world);
        ((RingEntityManagerAccess) entityManager).ringworld$setGeometry(geometry);
        ((RingTickSchedulerAccess) blockTicks).ringworld$setGeometry(geometry);
        ((RingTickSchedulerAccess) fluidTicks).ringworld$setGeometry(geometry);
    }

    @ModifyVariable(method = "areEntitiesLoaded", at = @At("HEAD"), argsOnly = true)
    private long ringworld$canonicalLoadedChunkKey(long packedPos) {
        ServerLevel world = (ServerLevel) (Object) this;
        if (world.dimension() != Level.OVERWORLD) return packedPos;
        ChunkPos pos = new ChunkPos(packedPos);
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        return ChunkPos.asLong(RingChunkCoordinates.wrapChunkX(pos.x, geometry), pos.z);
    }

    // 1.21.1: waitForEntities does not exist; periodic entity loading is handled by the entity/chunk manager mixins.

    // 1.21.1: newer ServerLevel entity-tick lambda hook is not present.

    // 1.21.1: newer areEntitiesActuallyLoadedAndTicking/anyPlayerCloseEnoughForSpawning/canSpawnEntitiesInChunk methods are absent.

    @ModifyVariable(method = "isPositionEntityTicking", at = @At("HEAD"), argsOnly = true)
    private BlockPos ringworld$canonicalEntityTickQuery(BlockPos pos) {
        ServerLevel world = (ServerLevel) (Object) this;
        if (world.dimension() != Level.OVERWORLD) return pos;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        int x = geometry.wrapBlockX(pos.getX());
        return x == pos.getX() ? pos : new BlockPos(x, pos.getY(), pos.getZ());
    }

    /**
     * Vanilla chooses an active raid with flat BlockPos distance. Select from
     * the same saved collection using the shortest periodic X delta so omen,
     * bossbar, villager-state, and reconnect lookups cross the joined edge.
     */
    @Inject(method = "getRaidAt", at = @At("HEAD"), cancellable = true)
    private void ringworld$nearestPeriodicRaid(BlockPos pos, CallbackInfoReturnable<Raid> cir) {
        ServerLevel world = (ServerLevel) (Object) this;
        if (world.dimension() != Level.OVERWORLD) return;

        RingGeometry geometry = RingWorldServer.geometryFor(world);
        Raid nearest = null;
        double nearestDistance = 9_216.0;
        for (Raid raid : ((RaidsAccessor) world.getRaids()).ringworld$getRaidMap().values()) {
            if (!raid.isActive()) continue;
            BlockPos center = raid.getCenter();
            double distance = RingRaidSupport.periodicDistanceSquared(
                    geometry,
                    center.getX(), center.getY(), center.getZ(),
                    pos.getX(), pos.getY(), pos.getZ());
            if (distance < nearestDistance) {
                nearest = raid;
                nearestDistance = distance;
            }
        }
        cir.setReturnValue(nearest);
    }

    // TODO 1.21.1: re-port periodic particle proximity against the older sendParticles implementation.
}
