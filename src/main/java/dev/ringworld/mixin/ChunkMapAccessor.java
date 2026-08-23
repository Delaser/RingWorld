package dev.ringworld.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Bridges 1.21.1 package-private ChunkMap queries used by periodic tracking. */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Invoker("isChunkTracked")
    boolean ringworld$isChunkTracked(ServerPlayer player, int chunkX, int chunkZ);

    @Invoker("getUpdatingChunkIfPresent")
    ChunkHolder ringworld$getUpdatingChunkIfPresent(long packedPos);
}
