package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingChunkFilter;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingRegionContext;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.GeneratingChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.phys.Vec3;

/** Makes chunk visibility use the nearest periodic image for each player. */
@Mixin(ChunkMap.class)
abstract class ServerChunkLoadingManagerMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "updateChunkScheduling", at = @At("HEAD"))
    private void ringworld$auditCanonicalHolderCreation(long packedPos, int ticketLevel,
                                                        @Nullable ChunkHolder holder, int previousLevel,
                                                        CallbackInfoReturnable<ChunkHolder> cir) {
        if (level.dimension() != Level.OVERWORLD || !RingWorldConfig.load().testMode()) return;
        ChunkPos pos = new ChunkPos(packedPos);
        int circumferenceChunks = RingWorldServer.geometryFor(level).circumferenceChunks();
        if (pos.x < 0 || pos.x >= circumferenceChunks) {
            RingWorldServer.recordNonCanonicalHolderRequest();
        }
    }

    @ModifyVariable(method = "acquireGeneration", at = @At("HEAD"), argsOnly = true)
    private long ringworld$canonicalGenerationAcquire(long packedPos) {
        if (level.dimension() != Level.OVERWORLD) return packedPos;
        ChunkPos pos = new ChunkPos(packedPos);
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        return ChunkPos.asLong(RingChunkCoordinates.wrapChunkX(pos.x, geometry), pos.z);
    }

    @Redirect(
            method = "getChunkRangeFuture",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;getUpdatingChunkIfPresent(J)Lnet/minecraft/server/level/ChunkHolder;"))
    private ChunkHolder ringworld$getPeriodicRegionHolder(ChunkMap manager, long packedPos) {
        if (level.dimension() != Level.OVERWORLD) return manager.getUpdatingChunkIfPresent(packedPos);
        ChunkPos pos = new ChunkPos(packedPos);
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        long canonical = ChunkPos.asLong(RingChunkCoordinates.wrapChunkX(pos.x, geometry), pos.z);
        return manager.getUpdatingChunkIfPresent(canonical);
    }

    @Redirect(
            method = "scheduleGenerationTask",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkGenerationTask;create(Lnet/minecraft/server/level/GeneratingChunkMap;Lnet/minecraft/world/level/chunk/status/ChunkStatus;Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/server/level/ChunkGenerationTask;"))
    private ChunkGenerationTask ringworld$createPeriodicLoader(GeneratingChunkMap manager,
                                                       ChunkStatus status, ChunkPos pos) {
        if (level.dimension() != Level.OVERWORLD) return ChunkGenerationTask.create(manager, status, pos);
        int circumferenceChunks = RingWorldServer.geometryFor(level).circumferenceChunks();
        return RingRegionContext.run(circumferenceChunks, () -> ChunkGenerationTask.create(manager, status, pos));
    }

    /** Keep the player's loading/watch center in the finite canonical graph. */
    @Redirect(
            method = {"updatePlayerStatus", "updatePlayerPos", "move", "tick()V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"))
    private net.minecraft.core.SectionPos ringworld$canonicalWatchedSection(EntityAccess entity) {
        net.minecraft.core.SectionPos actual = net.minecraft.core.SectionPos.of(entity);
        if (level.dimension() != Level.OVERWORLD) return actual;
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        return net.minecraft.core.SectionPos.of(
                RingChunkCoordinates.wrapChunkX(actual.x(), geometry),
                actual.y(), actual.z());
    }

    @Redirect(
            method = "updateChunkTracking(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkTrackingView;of(Lnet/minecraft/world/level/ChunkPos;I)Lnet/minecraft/server/level/ChunkTrackingView;"))
    private ChunkTrackingView ringworld$periodicWatchFilter(ChunkPos center, int viewDistance,
                                                       ServerPlayer player) {
        if (level.dimension() != Level.OVERWORLD) return ChunkTrackingView.of(center, viewDistance);
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        // The client maps this canonical centre into whichever nearby visual
        // chart it currently occupies, so a 99 -> 0 server fold remains a
        // smooth 99 -> 100 presentation update.
        ChunkPos canonicalCenter = new ChunkPos(
                ((int)Math.floor(player.getX())) >> 4,
                center.z);
        return new RingChunkFilter(canonicalCenter, viewDistance, geometry);
    }

    @Inject(
            method = "applyChunkTrackingView(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ChunkTrackingView;)V",
            at = @At("HEAD"))
    private void ringworld$sendPeriodicWatchCenter(ServerPlayer player, ChunkTrackingView next, CallbackInfo ci) {
        if (level.dimension() != Level.OVERWORLD || !(next instanceof RingChunkFilter ring)) return;
        ChunkTrackingView previous = player.getChunkTrackingView();
        if (!(previous instanceof RingChunkFilter old) || old.logicalCenterX() != ring.logicalCenterX()
                || old.center().z != ring.center().z) {
            player.connection.send(
                    new ClientboundSetChunkCacheCenterPacket(ring.logicalCenterX(), ring.center().z));
        }
    }

    @Redirect(
            method = "applyChunkTrackingView(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ChunkTrackingView;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkTrackingView;difference(Lnet/minecraft/server/level/ChunkTrackingView;Lnet/minecraft/server/level/ChunkTrackingView;Ljava/util/function/Consumer;Ljava/util/function/Consumer;)V"))
    private void ringworld$diffPeriodicWatchFilter(ChunkTrackingView oldFilter, ChunkTrackingView newFilter,
                                                   Consumer<ChunkPos> newlyIncluded,
                                                   Consumer<ChunkPos> justRemoved) {
        if (level.dimension() == Level.OVERWORLD
                && (oldFilter instanceof RingChunkFilter || newFilter instanceof RingChunkFilter)) {
            RingChunkFilter.forEachChanged(oldFilter, newFilter, newlyIncluded, justRemoved);
        } else {
            ChunkTrackingView.difference(oldFilter, newFilter, newlyIncluded, justRemoved);
        }
    }

    @Inject(method = "isChunkTracked", at = @At("HEAD"), cancellable = true)
    private void ringworld$isTrackedPeriodically(ServerPlayer player, int chunkX, int chunkZ,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (level.dimension() != Level.OVERWORLD) return;
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        int canonicalX = RingChunkCoordinates.wrapChunkX(chunkX, geometry);
        long canonicalChunk = ChunkPos.asLong(canonicalX, chunkZ);
        cir.setReturnValue(player.getChunkTrackingView().contains(canonicalX, chunkZ)
                && !player.connection.chunkSender.isPending(canonicalChunk));
    }

    @Inject(method = "getPlayersCloseForSpawning", at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicChunkWatchers(ChunkPos pos,
                                                 CallbackInfoReturnable<List<ServerPlayer>> cir) {
        if (level.dimension() != Level.OVERWORLD) return;
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        List<ServerPlayer> result = new ArrayList<>();
        int canonicalX = RingChunkCoordinates.wrapChunkX(pos.x, geometry);
        for (ServerPlayer player : level.players()) {
            if (player.getChunkTrackingView().contains(canonicalX, pos.z)) result.add(player);
        }
        cir.setReturnValue(List.copyOf(result));
    }

    @Inject(method = "getPlayers(Lnet/minecraft/world/level/ChunkPos;Z)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicUpdateWatchers(ChunkPos pos, boolean onlyOnWatchDistanceEdge,
                                                  CallbackInfoReturnable<List<ServerPlayer>> cir) {
        if (level.dimension() != Level.OVERWORLD) return;
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        List<ServerPlayer> result = new ArrayList<>();
        int canonicalX = RingChunkCoordinates.wrapChunkX(pos.x, geometry);
        for (ServerPlayer player : level.players()) {
            if (!player.getChunkTrackingView().contains(canonicalX, pos.z)) continue;
            if (!onlyOnWatchDistanceEdge || isCanonicalTrackEdge(player, canonicalX, pos.z, geometry)) result.add(player);
        }
        cir.setReturnValue(List.copyOf(result));
    }

    @Inject(method = "playerIsCloseEnoughForSpawning", at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicTickDistance(ServerPlayer player, ChunkPos pos,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (level.dimension() != Level.OVERWORLD) return;
        if (player.isSpectator()) {
            cir.setReturnValue(false);
            return;
        }
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        double chunkCenterX = net.minecraft.core.SectionPos.sectionToBlockCoord(pos.x, 8);
        double chunkCenterZ = net.minecraft.core.SectionPos.sectionToBlockCoord(pos.z, 8);
        double dx = geometry.shortestCircumferenceDelta(player.getX(), chunkCenterX);
        double dz = chunkCenterZ - player.getZ();
        cir.setReturnValue(dx * dx + dz * dz < 16_384.0);
    }

    @Inject(method = "playerIsCloseEnoughTo", at = @At("HEAD"), cancellable = true)
    private void ringworld$periodicPlayerDistance(ServerPlayer player, Vec3 pos, int distance,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (level.dimension() != Level.OVERWORLD) return;
        if (player.isSpectator()) {
            cir.setReturnValue(false);
            return;
        }
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        double dx = geometry.shortestCircumferenceDelta(player.getX(), pos.x);
        double dy = pos.y - player.getY();
        double dz = pos.z - player.getZ();
        cir.setReturnValue(dx * dx + dy * dy + dz * dz < (double) distance * distance);
    }

    private static boolean isCanonicalTrackEdge(ServerPlayer player, int chunkX, int chunkZ,
                                                RingGeometry geometry) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int neighborX = RingChunkCoordinates.wrapChunkX(chunkX + dx, geometry);
                if ((dx != 0 || dz != 0) && !player.getChunkTrackingView().contains(neighborX, chunkZ + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

}
