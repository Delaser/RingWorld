package dev.ringworld.mixin;

import dev.ringworld.world.RingChunkLevelContext;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.world.ChunkPosDistanceLevelPropagator;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Joins the two X edges of Overworld chunk ticket/simulation propagation. */
@Mixin(ChunkPosDistanceLevelPropagator.class)
abstract class ChunkPosDistanceLevelPropagatorMixin {
    @Redirect(
            method = {"propagateLevel", "recalculateLevel"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/ChunkPos;toLong(II)J"))
    private long ringworld$periodicNeighbor(int x, int z) {
        RingGeometry geometry = RingChunkLevelContext.activeGeometry();
        if (geometry == null) return ChunkPos.toLong(x, z);
        return ChunkPos.toLong(Math.floorMod(x, geometry.circumferenceChunks()), z);
    }
}
