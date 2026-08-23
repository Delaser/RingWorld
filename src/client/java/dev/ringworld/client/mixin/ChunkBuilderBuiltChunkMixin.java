package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets finite-width boundary sections build without nonexistent exterior
 * neighbours.
 *
 * <p>Vanilla waits for all eight horizontal neighbour chunks before meshing a
 * section. That is normally a useful streaming guard, but the chunks beyond a
 * RingWorld rim are intentionally absent forever. Treat only those known
 * exterior section positions as ready; every interior neighbour retains the
 * vanilla lighting/full-chunk requirement.</p>
 */
@Mixin(SectionRenderDispatcher.RenderSection.class)
abstract class ChunkBuilderBuiltChunkMixin {
    @Shadow
    private boolean doesChunkExistAt(BlockPos blockPos) {
        throw new AssertionError();
    }

    @Redirect(
            method = "hasAllNeighbors",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;doesChunkExistAt(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean ringworld$treatExteriorVoidAsReady(SectionRenderDispatcher.RenderSection instance,
                                                       BlockPos blockPos) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry != null
                && geometry.isExteriorChunkZ(SectionPos.blockToSectionCoord(blockPos.getZ()))) {
            return true;
        }
        return doesChunkExistAt(blockPos);
    }
}
