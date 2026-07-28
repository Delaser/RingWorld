package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.ChunkSectionPos;
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
@Mixin(ChunkBuilder.BuiltChunk.class)
abstract class ChunkBuilderBuiltChunkMixin {
    @Shadow
    private boolean isChunkNonEmpty(long sectionPos) {
        throw new AssertionError();
    }

    @Redirect(
            method = "shouldBuild",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;isChunkNonEmpty(J)Z"))
    private boolean ringworld$treatExteriorVoidAsReady(ChunkBuilder.BuiltChunk instance,
                                                       long sectionPos) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry != null
                && geometry.isExteriorChunkZ(ChunkSectionPos.unpackZ(sectionPos))) {
            return true;
        }
        return isChunkNonEmpty(sectionPos);
    }
}
