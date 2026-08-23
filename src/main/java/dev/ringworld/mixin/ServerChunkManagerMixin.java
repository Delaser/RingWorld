package dev.ringworld.mixin;

import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingChunkLevelContext;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import dev.ringworld.server.RingWorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes every server chunk acquisition use the canonical circumference chunk.
 * This is deliberately below the entity layer so chunk tickets, chunk-status
 * dependencies, and worldgen neighbour reads use the same ring topology.
 */
@Mixin(ServerChunkCache.class)
abstract class ServerChunkManagerMixin {
    @ModifyVariable(
            method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ringworld$canonicalChunkX(int chunkX) {
        Level world = ((ServerChunkCache) (Object) this).getLevel();
        if (!(world instanceof ServerLevel serverWorld) || serverWorld.dimension() != Level.OVERWORLD) {
            return chunkX;
        }
        RingGeometry geometry = RingWorldServer.geometryFor(serverWorld);
        return RingChunkCoordinates.wrapChunkX(chunkX, geometry);
    }

    /** Canonicalize world-facing holder lookups without touching generation's internal holder graph. */
    @ModifyVariable(method = "getVisibleChunkIfPresent", at = @At("HEAD"), argsOnly = true)
    private long ringworld$canonicalHolderLookup(long packedPos) {
        Level world = ((ServerChunkCache) (Object) this).getLevel();
        if (!(world instanceof ServerLevel serverWorld) || serverWorld.dimension() != Level.OVERWORLD) {
            return packedPos;
        }
        ChunkPos pos = new ChunkPos(packedPos);
        RingGeometry geometry = RingWorldServer.geometryFor(serverWorld);
        return ChunkPos.asLong(RingChunkCoordinates.wrapChunkX(pos.x, geometry), pos.z);
    }

    /** Every external ticket source must enter the finite canonical graph. */
    @ModifyVariable(
            method = {
                    "addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V",
                    "removeRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V",
                    "updateChunkForced(Lnet/minecraft/world/level/ChunkPos;Z)V"
            },
            at = @At("HEAD"), argsOnly = true)
    private ChunkPos ringworld$canonicalTicketPosition(ChunkPos pos) {
        Level world = ((ServerChunkCache) (Object) this).getLevel();
        if (!(world instanceof ServerLevel serverWorld) || serverWorld.dimension() != Level.OVERWORLD) {
            return pos;
        }
        RingGeometry geometry = RingWorldServer.geometryFor(serverWorld);
        return new ChunkPos(RingChunkCoordinates.wrapChunkX(pos.x, geometry), pos.z);
    }

    @Redirect(
            method = "runDistanceManagerUpdates",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DistanceManager;runAllUpdates(Lnet/minecraft/server/level/ChunkMap;)Z"))
    private boolean ringworld$updatePeriodicChunkGraph(DistanceManager manager,
                                                       ChunkMap loadingManager) {
        Level world = ((ServerChunkCache) (Object) this).getLevel();
        if (!(world instanceof ServerLevel serverWorld) || serverWorld.dimension() != Level.OVERWORLD) {
            return manager.runAllUpdates(loadingManager);
        }
        RingGeometry geometry = RingWorldServer.geometryFor(serverWorld);
        return RingChunkLevelContext.run(geometry, () -> manager.runAllUpdates(loadingManager));
    }
}
