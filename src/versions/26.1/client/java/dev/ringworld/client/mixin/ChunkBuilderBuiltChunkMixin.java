package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lets finite-width boundary sections build without nonexistent exterior neighbours. */
@Mixin(SectionRenderDispatcher.RenderSection.class)
abstract class ChunkBuilderBuiltChunkMixin {
    @Shadow
    private boolean doesChunkExistAt(long sectionPos) {
        throw new AssertionError();
    }

    @Redirect(method = "hasAllNeighbors", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;doesChunkExistAt(J)Z"))
    private boolean ringworld$treatExteriorVoidAsReady(SectionRenderDispatcher.RenderSection instance,
                                                       long sectionPos) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry != null && geometry.isExteriorChunkZ(SectionPos.z(sectionPos))) return true;
        return doesChunkExistAt(sectionPos);
    }
}
