package dev.ringworld.client.mixin;

import dev.ringworld.client.chunk.RingClientChunkMapAccess;
import dev.ringworld.client.chunk.RingClientChunkMaps;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicReferenceArray;

/** Allows a command-scale logical chart change to evict every stale chunk slot. */
@Mixin(targets = "net.minecraft.client.world.ClientChunkManager$ClientChunkMap")
abstract class ClientChunkMapMixin implements RingClientChunkMapAccess {
    @Shadow @Final private AtomicReferenceArray<WorldChunk> chunks;
    @Shadow @Final private ClientChunkManager field_16254;
    @Shadow private volatile int centerChunkX;
    @Shadow private volatile int centerChunkZ;
    @Shadow abstract void unloadChunk(int index, WorldChunk chunk);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ringworld$registerChunkMap(CallbackInfo ci) {
        RingClientChunkMaps.register(field_16254, this);
    }

    @Override
    public int ringworld$centerChunkX() {
        return centerChunkX;
    }

    @Override
    public int ringworld$centerChunkZ() {
        return centerChunkZ;
    }

    @Override
    public void ringworld$clearAllChunks() {
        for (int index = 0; index < chunks.length(); index++) {
            WorldChunk chunk = chunks.get(index);
            if (chunk != null) unloadChunk(index, chunk);
        }
    }
}
