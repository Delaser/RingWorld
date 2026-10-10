package dev.ringworld.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** String target also supports the package-private 26.1 tracker. */
@Mixin(targets = "net.minecraft.server.level.LoadingChunkTracker")
public interface LoadingChunkTrackerAccessor {
    @Invoker("runDistanceUpdates") int ringworld$runUpdates(int count);
}
