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
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
            method = "runUpdates",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private boolean ringworld$disableFlatSectionOcclusion(boolean useOcclusionCulling) {
        return ClientRingState.geometry() == null && useOcclusionCulling;
    }

    @ModifyArg(
            method = "addSectionsInFrustum",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Octree;visitNodes(Lnet/minecraft/client/renderer/Octree$OctreeVisitor;Lnet/minecraft/client/renderer/culling/Frustum;I)V"),
            index = 1)
    private Frustum ringworld$curveCollectedChunkFrustum(Frustum frustum) {
        return ringworld$wrap(frustum);
    }

    @Group(name = "ringworldOffsetFrustum", min = 1, max = 1)
    @Redirect(
            method = "runPartialUpdate",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;offsetFrustum(Lnet/minecraft/client/renderer/culling/Frustum;)Lnet/minecraft/client/renderer/culling/Frustum;"), require = 0)
    private Frustum ringworld$curveNewChunkFrustum(Frustum frustum) {
        return ringworld$wrap(new Frustum(frustum).offsetToFullyIncludeCameraCube(8));
    }

    @Group(name = "ringworldOffsetFrustum", min = 1, max = 1)
    @Inject(method = "offsetFrustum", at = @At("RETURN"), cancellable = true, require = 0)
    private static void ringworld$curveOffsetFrustum26_2(Frustum original, CallbackInfoReturnable<Frustum> cir) {
        cir.setReturnValue(ringworld$wrap(cir.getReturnValue()));
    }

    private static Frustum ringworld$wrap(Frustum frustum) {
        if (frustum instanceof CurvedRingFrustum) return frustum;
        RingGeometry geometry = ClientRingState.geometry();
        Minecraft client = Minecraft.getInstance();
        if (geometry == null || client.gameRenderer == null
                || !dev.ringworld.client.RingMinecraftClientAccess.camera(client).isInitialized()) return frustum;
        Vec3 cameraPosition = dev.ringworld.client.RingMinecraftClientAccess.camera(client).position();
        return new CurvedRingFrustum(frustum, geometry, cameraPosition);
    }
}
