package dev.ringworld.mixin;

import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.CompletableFuture;

/**
 * Owner-thread Atlas admission only: bypasses the public method's managed wait.
 * The caller retains its own non-expiring ticket until capture/cancellation.
 * Vanilla still propagates tickets and schedules the complete dependency graph.
 */
@Mixin(ServerChunkCache.class)
public interface AtlasChunkCacheInvoker {
    @Invoker("getChunkFutureMainThread")
    CompletableFuture<ChunkResult<ChunkAccess>> ringworld$requestChunkWithoutWaiting(
            int x, int z, ChunkStatus status, boolean create);
}
