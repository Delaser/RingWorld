package dev.ringworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies the ring transform at the common entity-model translation point. */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderManagerMixin {
    @Redirect(
            method = "submit",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0))
    private void ringworld$translateEntity(PoseStack matrices, double x, double y, double z,
                                            EntityRenderState state, CameraRenderState cameraState,
                                            double originalX, double originalY, double originalZ,
                                            PoseStack originalMatrices, SubmitNodeCollector queue) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || cameraState.pos == null) {
            matrices.translate(x, y, z);
            return;
        }
        Vec3 canonicalEntityPosition = cameraState.pos.add(x, y, z);
        Vec3 localPosition = geometry.toCameraLocal(canonicalEntityPosition, cameraState.pos);
        matrices.translate(localPosition.x, localPosition.y, localPosition.z);
        // Entity renderers apply their normal yaw/pose transforms after this
        // common translation. Rotating here makes those transforms operate in
        // the entity's own tangent frame while the local player remains at a
        // zero-angle frame and therefore keeps vanilla controls unchanged.
        double tangentAngle = geometry.tangentFrameAngle(cameraState.pos.x, canonicalEntityPosition.x);
        matrices.mulPose(Axis.ZP.rotation((float)tangentAngle));
    }
}
