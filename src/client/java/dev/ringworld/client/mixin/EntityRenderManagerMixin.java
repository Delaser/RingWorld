package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies the ring transform at the common entity-model translation point. */
@Mixin(EntityRenderManager.class)
abstract class EntityRenderManagerMixin {
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(DDD)V", ordinal = 0))
    private void ringworld$translateEntity(MatrixStack matrices, double x, double y, double z,
                                            EntityRenderState state, CameraRenderState cameraState,
                                            double originalX, double originalY, double originalZ,
                                            MatrixStack originalMatrices, OrderedRenderCommandQueue queue) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || cameraState.pos == null) {
            matrices.translate(x, y, z);
            return;
        }
        Vec3d canonicalEntityPosition = cameraState.pos.add(x, y, z);
        Vec3d localPosition = geometry.toCameraLocal(canonicalEntityPosition, cameraState.pos);
        matrices.translate(localPosition.x, localPosition.y, localPosition.z);
        // Entity renderers apply their normal yaw/pose transforms after this
        // common translation. Rotating here makes those transforms operate in
        // the entity's own tangent frame while the local player remains at a
        // zero-angle frame and therefore keeps vanilla controls unchanged.
        double tangentAngle = geometry.tangentFrameAngle(cameraState.pos.x, canonicalEntityPosition.x);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation((float)tangentAngle));
    }
}
