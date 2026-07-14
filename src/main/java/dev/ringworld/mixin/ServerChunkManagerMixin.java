package dev.ringworld.mixin;

import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingChunkLevelContext;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.server.RingWorldServer;
import net.minecraft.server.world.ChunkLevelManager;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes every server chunk acquisition use the canonical circumference chunk.
 * This is deliberately below the entity layer so chunk tickets, chunk-status
 * dependencies, and worldgen neighbour reads use the same ring topology.
 */
@Mixin(ServerChunkManager.class)
abstract class ServerChunkManagerMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void ringworld$attachGeneratorGeometry(CallbackInfo ci) {
        World world = ((ServerChunkManager) (Object) this).getWorld();
        if (!(world instanceof ServerWorld serverWorld) || serverWorld.getRegistryKey() != World.OVERWORLD) return;
        RingWorldServer.attachBootstrapGeometry(((ServerChunkManager) (Object) this).getChunkGenerator());
    }

    @ModifyVariable(
            method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ringworld$canonicalChunkX(int chunkX) {
        World world = ((ServerChunkManager) (Object) this).getWorld();
        if (!(world instanceof ServerWorld serverWorld) || serverWorld.getRegistryKey() != World.OVERWORLD) {
            return chunkX;
        }
        RingGeometry geometry = RingWorldServer.geometryFor(serverWorld);
        return RingChunkCoordinates.wrapChunkX(chunkX, geometry);
    }

    /** Canonicalize world-facing holder lookups without touching generation's internal holder graph. */
    @ModifyVariable(method = "getChunkHolder", at = @At("HEAD"), argsOnly = true)
    private long ringworld$canonicalHolderLookup(long packedPos) {
        World world = ((ServerChunkManager) (Object) this).getWorld();
        if (!(world instanceof ServerWorld serverWorld) || serverWorld.getRegistryKey() != World.OVERWORLD) {
            return packedPos;
        }
        ChunkPos pos = new ChunkPos(packedPos);
        RingGeometry geometry = RingWorldServer.geometryFor(serverWorld);
        return ChunkPos.toLong(RingChunkCoordinates.wrapChunkX(pos.x, geometry), pos.z);
    }

    /** Every external ticket source must enter the finite canonical graph. */
    @ModifyVariable(
            method = {
                    "addTicket(Lnet/minecraft/server/world/ChunkTicket;Lnet/minecraft/util/math/ChunkPos;)V",
                    "addChunkLoadingTicket",
                    "addTicket(Lnet/minecraft/server/world/ChunkTicketType;Lnet/minecraft/util/math/ChunkPos;I)V",
                    "removeTicket",
                    "setChunkForced"
            },
            at = @At("HEAD"), argsOnly = true)
    private ChunkPos ringworld$canonicalTicketPosition(ChunkPos pos) {
        World world = ((ServerChunkManager) (Object) this).getWorld();
        if (!(world instanceof ServerWorld serverWorld) || serverWorld.getRegistryKey() != World.OVERWORLD) {
            return pos;
        }
        RingGeometry geometry = RingWorldServer.geometryFor(serverWorld);
        return new ChunkPos(RingChunkCoordinates.wrapChunkX(pos.x, geometry), pos.z);
    }

    @Redirect(
            method = "updateChunks",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ChunkLevelManager;update(Lnet/minecraft/server/world/ServerChunkLoadingManager;)Z"))
    private boolean ringworld$updatePeriodicChunkGraph(ChunkLevelManager manager,
                                                       ServerChunkLoadingManager loadingManager) {
        World world = ((ServerChunkManager) (Object) this).getWorld();
        if (!(world instanceof ServerWorld serverWorld) || serverWorld.getRegistryKey() != World.OVERWORLD) {
            return manager.update(loadingManager);
        }
        RingGeometry geometry = RingWorldServer.geometryFor(serverWorld);
        return RingChunkLevelContext.run(geometry, () -> manager.update(loadingManager));
    }
}
