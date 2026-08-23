package dev.ringworld.mixin;

import dev.ringworld.RingWorldMod;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingRaidSupport;
import dev.ringworld.world.RingTickSchedulerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.DistanceManager;
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

    /**
     * The primary entity loop checks the asynchronously propagated simulation
     * graph. Canonicalize its lookup and cover the brief/stale graph state
     * observed after a player naturally crosses the joined edge. The fallback
     * exactly mirrors the configured square simulation distance, uses nearest
     * periodic chunk images, and excludes spectators; it therefore activates
     * only chunks vanilla intends the nearby player to simulate.
     */
    @Redirect(
            method = {
                    "method_31420(Lnet/minecraft/world/TickRateManager;"
                            + "Lnet/minecraft/util/profiling/ProfilerFiller;"
                            + "Lnet/minecraft/world/entity/Entity;)V",
                    "lambda$tick$2(Lnet/minecraft/world/TickRateManager;"
                            + "Lnet/minecraft/util/profiling/ProfilerFiller;"
                            + "Lnet/minecraft/world/entity/Entity;)V"
            },
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/DistanceManager;inEntityTickingRange(J)Z"))
    private boolean ringworld$periodicEntityTickEligibility(DistanceManager manager, long packedPos) {
        ServerLevel world = (ServerLevel) (Object) this;
        if (world.dimension() != Level.OVERWORLD) return manager.inEntityTickingRange(packedPos);

        ChunkPos pos = new ChunkPos(packedPos);
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        int canonicalX = RingChunkCoordinates.wrapChunkX(pos.x, geometry);
        long canonicalPos = ChunkPos.asLong(canonicalX, pos.z);
        if (manager.inEntityTickingRange(canonicalPos)) return true;

        int simulationDistance = world.getServer().getPlayerList().getSimulationDistance();
        for (var player : world.players()) {
            if (player.isSpectator()) continue;
            ChunkPos playerPos = player.chunkPosition();
            if (RingChunkCoordinates.isWithinSimulationDistance(
                    canonicalX, pos.z, playerPos.x, playerPos.z,
                    simulationDistance, geometry)) {
                return true;
            }
        }
        return false;
    }

    @ModifyVariable(
            method = "isNaturalSpawningAllowed(Lnet/minecraft/world/level/ChunkPos;)Z",
            at = @At("HEAD"), argsOnly = true)
    private ChunkPos ringworld$canonicalTickQuery(ChunkPos pos) {
        ServerLevel world = (ServerLevel) (Object) this;
        if (world.dimension() != Level.OVERWORLD) return pos;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        return new ChunkPos(RingChunkCoordinates.wrapChunkX(pos.x, geometry), pos.z);
    }

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

    /** Particle and other proximity packets must cross the joined edge too. */
    @Redirect(
            method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
    private boolean ringworld$periodicPacketDistance(BlockPos playerPos, Position eventPos, double range) {
        ServerLevel world = (ServerLevel) (Object) this;
        if (world.dimension() != Level.OVERWORLD) return playerPos.closerToCenterThan(eventPos, range);
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        double dx = geometry.shortestCircumferenceDelta(playerPos.getX(), eventPos.x());
        double dy = playerPos.getY() - eventPos.y();
        double dz = playerPos.getZ() - eventPos.z();
        return dx * dx + dy * dy + dz * dz < range * range;
    }
}
