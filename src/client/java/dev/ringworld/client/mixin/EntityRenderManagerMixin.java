package dev.ringworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingObjectTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies the ring transform at the common entity-model translation point. */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderManagerMixin {
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0))
    private void ringworld$translateEntity(PoseStack matrices, double x, double y, double z,
                                            Entity entity,
                                            double originalX, double originalY, double originalZ,
                                            float yaw, float tickProgress,
                                            PoseStack originalMatrices, MultiBufferSource buffers,
                                            int packedLight) {
        RingGeometry geometry = ClientRingState.geometry();
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (geometry == null) {
            matrices.translate(x, y, z);
            return;
        }
        RingObjectTransform transform = RingObjectTransform.fromCameraRelative(
                geometry, camera, x, y, z);
        Vec3 localPosition = transform.cameraLocalPosition();
        matrices.translate(localPosition.x, localPosition.y, localPosition.z);
        // Entity renderers apply their normal yaw/pose transforms after this
        // common translation. Rotating here makes those transforms operate in
        // the entity's own tangent frame while the local player remains at a
        // zero-angle frame and therefore keeps vanilla controls unchanged.
        matrices.mulPose(Axis.ZP.rotation((float)transform.tangentAngleRadians()));
    }
}
