package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps the finite-Z exterior-neighbour exemption after 26.2 moved readiness
 * from RenderSection into SectionUpdateTracker.
 */
@Mixin(SectionUpdateTracker.class)
abstract class ChunkBuilderBuiltChunkMixin {
    @Shadow
    private boolean doesChunkExistAt(ClientLevel level, long sectionPos) {
        throw new AssertionError();
    }

    @Redirect(method = "hasAllNeighbors", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/SectionUpdateTracker;doesChunkExistAt(Lnet/minecraft/client/multiplayer/ClientLevel;J)Z"))
    private boolean ringworld$treatExteriorVoidAsReady(SectionUpdateTracker instance,
                                                       ClientLevel level, long sectionPos) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry != null && geometry.isExteriorChunkZ(SectionPos.z(sectionPos))) return true;
        return doesChunkExistAt(level, sectionPos);
    }
}
