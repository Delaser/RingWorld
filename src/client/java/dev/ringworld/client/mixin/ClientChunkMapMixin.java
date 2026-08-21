package dev.ringworld.client.mixin;

import dev.ringworld.client.chunk.RingClientChunkMapAccess;
import dev.ringworld.client.chunk.RingClientChunkMaps;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;

/** Allows a command-scale logical chart change to evict every stale chunk slot. */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientChunkCache$Storage")
abstract class ClientChunkMapMixin implements RingClientChunkMapAccess {
    @Shadow @Final private AtomicReferenceArray<LevelChunk> chunks;
    @Shadow private volatile int viewCenterX;
    @Shadow private volatile int viewCenterZ;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ringworld$registerChunkMap(ClientChunkCache owner, int radius, CallbackInfo ci) {
        RingClientChunkMaps.register(owner, this);
    }

    @Override
    public int ringworld$centerChunkX() {
        return viewCenterX;
    }

    @Override
    public int ringworld$centerChunkZ() {
        return viewCenterZ;
    }

    @Override
    public void ringworld$clearAllChunks() {
        for (int index = 0; index < chunks.length(); index++) {
            chunks.set(index, null);
        }
    }
}
