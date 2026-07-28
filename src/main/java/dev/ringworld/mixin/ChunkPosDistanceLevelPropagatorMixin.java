package dev.ringworld.mixin;

import dev.ringworld.world.RingChunkLevelContext;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ChunkTracker;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Joins the two X edges of Overworld chunk ticket/simulation propagation. */
@Mixin(ChunkTracker.class)
abstract class ChunkPosDistanceLevelPropagatorMixin {
    @Redirect(
            method = {"checkNeighborsAfterUpdate", "getComputedLevel"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ChunkPos;pack(II)J"))
    private long ringworld$periodicNeighbor(int x, int z) {
        RingGeometry geometry = RingChunkLevelContext.activeGeometry();
        if (geometry == null) return ChunkPos.pack(x, z);
        return ChunkPos.pack(Math.floorMod(x, geometry.circumferenceChunks()), z);
    }
}
