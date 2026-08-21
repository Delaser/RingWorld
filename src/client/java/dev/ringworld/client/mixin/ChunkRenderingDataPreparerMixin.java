package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.render.CurvedRingFrustum;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes terrain visibility use the same cylindrical space as its shader. */
@Mixin(SectionOcclusionGraph.class)
abstract class ChunkRenderingDataPreparerMixin {
    /**
     * Vanilla's smart section occlusion propagates visibility through a flat
     * six-face graph. A mountain can therefore stop traversal to sections
     * which the cylindrical transform later bends back into view. Use
     * vanilla's supported non-occluding traversal for the RingWorld only;
     * render distance and the curved frustum still reject irrelevant
     * sections.
     */
    @ModifyVariable(
            method = "update",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private boolean ringworld$disableFlatSectionOcclusion(boolean useOcclusionCulling) {
        return ClientRingState.geometry() == null && useOcclusionCulling;
    }
//// TODO Enable this later on
//
//    @ModifyArg(
//            method = "addSectionsInFrustum",
//            at = @At(value = "INVOKE",
//                    target = "Lnet/minecraft/client/renderer/Octree;visitNodes(Lnet/minecraft/client/renderer/Octree$OctreeVisitor;Lnet/minecraft/client/renderer/culling/Frustum;I)V"),
//            index = 1)
//    private Frustum ringworld$curveCollectedChunkFrustum(Frustum frustum) {
//        return ringworld$wrap(frustum);
//    }

    @Redirect(
            method = "runPartialUpdate",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;offsetFrustum(Lnet/minecraft/client/renderer/culling/Frustum;)Lnet/minecraft/client/renderer/culling/Frustum;"))
    private Frustum ringworld$curveNewChunkFrustum(Frustum frustum) {
        return ringworld$wrap(LevelRenderer.offsetFrustum(frustum));
    }

    private static Frustum ringworld$wrap(Frustum frustum) {
        RingGeometry geometry = ClientRingState.geometry();
        Minecraft client = Minecraft.getInstance();
        if (geometry == null || client.gameRenderer == null
                || !client.gameRenderer.getMainCamera().isInitialized()) return frustum;
        Vec3 cameraPosition = client.gameRenderer.getMainCamera().getPosition();
        return new CurvedRingFrustum(frustum, geometry, cameraPosition);
    }
}
