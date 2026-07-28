package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingChunkFilter;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingRegionContext;
import net.minecraft.network.packet.s2c.play.ChunkRenderDistanceCenterS2CPacket;
import net.minecraft.server.network.ChunkFilter;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.world.chunk.ChunkLoader;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.ChunkLoadingManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.entity.EntityLike;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Makes chunk visibility use the nearest periodic image for each player. */
@Mixin(ServerChunkLoadingManager.class)
abstract class ServerChunkLoadingManagerMixin {
    @Shadow @Final private ServerWorld world;

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void ringworld$auditCanonicalHolderCreation(long packedPos, int level,
                                                        @Nullable ChunkHolder holder, int previousLevel,
                                                        CallbackInfoReturnable<ChunkHolder> cir) {
        if (world.getRegistryKey() != World.OVERWORLD || !RingWorldConfig.load().testMode()) return;
        ChunkPos pos = new ChunkPos(packedPos);
        int circumferenceChunks = RingWorldServer.geometryFor(world).circumferenceChunks();
        if (pos.x < 0 || pos.x >= circumferenceChunks) {
            RingWorldServer.recordNonCanonicalHolderRequest();
        }
    }

    @ModifyVariable(method = "acquire", at = @At("HEAD"), argsOnly = true)
    private long ringworld$canonicalGenerationAcquire(long packedPos) {
        if (world.getRegistryKey() != World.OVERWORLD) return packedPos;
        ChunkPos pos = new ChunkPos(packedPos);
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        return ChunkPos.toLong(RingChunkCoordinates.wrapChunkX(pos.x, geometry), pos.z);
    }

    @Redirect(
            method = "getRegion",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerChunkLoadingManager;getCurrentChunkHolder(J)Lnet/minecraft/server/world/ChunkHolder;"))
    private ChunkHolder ringworld$getPeriodicRegionHolder(ServerChunkLoadingManager manager, long packedPos) {
        if (world.getRegistryKey() != World.OVERWORLD) return manager.getCurrentChunkHolder(packedPos);
        ChunkPos pos = new ChunkPos(packedPos);
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        long canonical = ChunkPos.toLong(RingChunkCoordinates.wrapChunkX(pos.x, geometry), pos.z);
        return manager.getCurrentChunkHolder(canonical);
    }

    @Redirect(
            method = "createLoader",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/ChunkLoader;create(Lnet/minecraft/world/ChunkLoadingManager;Lnet/minecraft/world/chunk/ChunkStatus;Lnet/minecraft/util/math/ChunkPos;)Lnet/minecraft/world/chunk/ChunkLoader;"))
    private ChunkLoader ringworld$createPeriodicLoader(ChunkLoadingManager manager,
                                                       ChunkStatus status, ChunkPos pos) {
        if (world.getRegistryKey() != World.OVERWORLD) return ChunkLoader.create(manager, status, pos);
        int circumferenceChunks = RingWorldServer.geometryFor(world).circumferenceChunks();
        return RingRegionContext.run(circumferenceChunks, () -> ChunkLoader.create(manager, status, pos));
    }

    /** Keep the player's loading/watch center in the finite canonical graph. */
    @Redirect(
            method = {"handlePlayerAddedOrRemoved", "updateWatchedSection", "updatePosition", "tickEntityMovement"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/ChunkSectionPos;from(Lnet/minecraft/world/entity/EntityLike;)Lnet/minecraft/util/math/ChunkSectionPos;"))
    private net.minecraft.util.math.ChunkSectionPos ringworld$canonicalWatchedSection(EntityLike entity) {
        net.minecraft.util.math.ChunkSectionPos actual = net.minecraft.util.math.ChunkSectionPos.from(entity);
        if (world.getRegistryKey() != World.OVERWORLD) return actual;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        return net.minecraft.util.math.ChunkSectionPos.from(
                RingChunkCoordinates.wrapChunkX(actual.getSectionX(), geometry),
                actual.getSectionY(), actual.getSectionZ());
    }

    @Redirect(
            method = "sendWatchPackets(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ChunkFilter;cylindrical(Lnet/minecraft/util/math/ChunkPos;I)Lnet/minecraft/server/network/ChunkFilter;"))
    private ChunkFilter ringworld$periodicWatchFilter(ChunkPos center, int viewDistance,
                                                       ServerPlayerEntity player) {
        if (world.getRegistryKey() != World.OVERWORLD) return ChunkFilter.cylindrical(center, viewDistance);
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        // The client maps this canonical centre into whichever nearby visual
        // chart it currently occupies, so a 99 -> 0 server fold remains a
        // smooth 99 -> 100 presentation update.
        ChunkPos canonicalCenter = new ChunkPos(
                ((int)Math.floor(player.getX())) >> 4,
                center.z);
        return new RingChunkFilter(canonicalCenter, viewDistance, geometry);
    }

    @Inject(
            method = "sendWatchPackets(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ChunkFilter;)V",
            at = @At("HEAD"))
    private void ringworld$sendPeriodicWatchCenter(ServerPlayerEntity player, ChunkFilter next, CallbackInfo ci) {
        if (world.getRegistryKey() != World.OVERWORLD || !(next instanceof RingChunkFilter ring)) return;
        ChunkFilter previous = player.getChunkFilter();
        if (!(previous instanceof RingChunkFilter old) || old.logicalCenterX() != ring.logicalCenterX()
                || old.center().z != ring.center().z) {
            player.networkHandler.sendPacket(
                    new ChunkRenderDistanceCenterS2CPacket(ring.logicalCenterX(), ring.center().z));
        }
    }

    @Redirect(
            method = "sendWatchPackets(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ChunkFilter;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ChunkFilter;forEachChangedChunk(Lnet/minecraft/server/network/ChunkFilter;Lnet/minecraft/server/network/ChunkFilter;Ljava/util/function/Consumer;Ljava/util/function/Consumer;)V"))
    private void ringworld$diffPeriodicWatchFilter(ChunkFilter oldFilter, ChunkFilter newFilter,
                                                   Consumer<ChunkPos> newlyIncluded,
                                                   Consumer<ChunkPos> justRemoved) {
        if (world.getRegistryKey() == World.OVERWORLD
                && (oldFilter instanceof RingChunkFilter || newFilter instanceof RingChunkFilter)) {
            RingChunkFilter.forEachChanged(oldFilter, newFilter, newlyIncluded, justRemoved);
        } else {
            ChunkFilter.forEachChangedChunk(oldFilter, newFilter, newlyIncluded, justRemoved);
        }
    }

    @Inject(method = "isTracked", at = @At("HEAD"), cancellable = true)
    private void ringworld$isTrackedPeriodically(ServerPlayerEntity player, int chunkX, int chunkZ,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (world.getRegistryKey() != World.OVERWORLD) return;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        int canonicalX = RingChunkCoordinates.wrapChunkX(chunkX, geometry);
        long canonicalChunk = ChunkPos.toLong(canonicalX, chunkZ);
        cir.setReturnValue(player.getChunkFilter().isWithinDistance(canonicalX, chunkZ)
                && !player.networkHandler.chunkDataSender.isInNextBatch(canonicalChunk));
    }

    @Inject(method = "getPlayersWatchingChunk", at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicChunkWatchers(ChunkPos pos,
                                                 CallbackInfoReturnable<List<ServerPlayerEntity>> cir) {
        if (world.getRegistryKey() != World.OVERWORLD) return;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        List<ServerPlayerEntity> result = new ArrayList<>();
        int canonicalX = RingChunkCoordinates.wrapChunkX(pos.x, geometry);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getChunkFilter().isWithinDistance(canonicalX, pos.z)) result.add(player);
        }
        cir.setReturnValue(List.copyOf(result));
    }

    @Inject(method = "getPlayersWatchingChunk(Lnet/minecraft/util/math/ChunkPos;Z)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicUpdateWatchers(ChunkPos pos, boolean onlyOnWatchDistanceEdge,
                                                  CallbackInfoReturnable<List<ServerPlayerEntity>> cir) {
        if (world.getRegistryKey() != World.OVERWORLD) return;
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        List<ServerPlayerEntity> result = new ArrayList<>();
        int canonicalX = RingChunkCoordinates.wrapChunkX(pos.x, geometry);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.getChunkFilter().isWithinDistance(canonicalX, pos.z)) continue;
            if (!onlyOnWatchDistanceEdge || isCanonicalTrackEdge(player, canonicalX, pos.z, geometry)) result.add(player);
        }
        cir.setReturnValue(List.copyOf(result));
    }

    @Inject(method = "canTickChunk", at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicTickDistance(ServerPlayerEntity player, ChunkPos pos,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (world.getRegistryKey() != World.OVERWORLD) return;
        if (player.isSpectator()) {
            cir.setReturnValue(false);
            return;
        }
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        double chunkCenterX = net.minecraft.util.math.ChunkSectionPos.getOffsetPos(pos.x, 8);
        double chunkCenterZ = net.minecraft.util.math.ChunkSectionPos.getOffsetPos(pos.z, 8);
        double dx = geometry.shortestCircumferenceDelta(player.getX(), chunkCenterX);
        double dz = chunkCenterZ - player.getZ();
        cir.setReturnValue(dx * dx + dz * dz < 16_384.0);
    }

    @Inject(method = "isNonSpectatorWithinDistance", at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicPlayerDistance(ServerPlayerEntity player, Vec3d pos, int distance,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (world.getRegistryKey() != World.OVERWORLD) return;
        if (player.isSpectator()) {
            cir.setReturnValue(false);
            return;
        }
        RingGeometry geometry = RingWorldServer.geometryFor(world);
        double dx = geometry.shortestCircumferenceDelta(player.getX(), pos.x);
        double dy = pos.y - player.getY();
        double dz = pos.z - player.getZ();
        cir.setReturnValue(dx * dx + dy * dy + dz * dz < (double) distance * distance);
    }

    private static boolean isCanonicalTrackEdge(ServerPlayerEntity player, int chunkX, int chunkZ,
                                                RingGeometry geometry) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int neighborX = RingChunkCoordinates.wrapChunkX(chunkX + dx, geometry);
                if ((dx != 0 || dz != 0) && !player.getChunkFilter().isWithinDistance(neighborX, chunkZ + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

}
