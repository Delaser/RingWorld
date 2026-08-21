package dev.ringworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingObjectTransform;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies the ring transform at the common entity-model translation point. */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderManagerMixin {
    @Shadow public Camera camera;

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0))
    private <E extends Entity> void ringworld$translateEntity(PoseStack matrices, double x, double y, double z,
            E entity, double originalX, double originalY, double originalZ, float yaw, float tickDelta,
            PoseStack originalMatrices, MultiBufferSource buffers, int packedLight) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null || camera == null) {
            matrices.translate(x, y, z);
            return;
        }

        Vec3 cameraPos = camera.getPosition();
        RingObjectTransform transform = RingObjectTransform.fromCameraRelative(
                geometry, cameraPos, x, y, z);
        Vec3 localPosition = transform.cameraLocalPosition();
        matrices.translate(localPosition.x, localPosition.y, localPosition.z);
        matrices.mulPose(Axis.ZP.rotation((float)transform.tangentAngleRadians()));
    }
}
