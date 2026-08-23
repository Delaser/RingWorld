package dev.ringworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingObjectTransform;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Curves object and interaction passes that bypass the terrain shader. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Shadow
    private static void renderShape(PoseStack poseStack, VertexConsumer vertices,
                                    VoxelShape shape, double x, double y, double z,
                                    float red, float green, float blue, float alpha) {
        throw new AssertionError();
    }

    @Redirect(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0))
    private void ringworld$curveBlockEntity(
            PoseStack poseStack, double x, double y, double z) {
        applyCurvedPose(poseStack, cameraPosition(), x, y, z);
    }

    @Redirect(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 1))
    private void ringworld$curveGlobalBlockEntity(
            PoseStack poseStack, double x, double y, double z) {
        applyCurvedPose(poseStack, cameraPosition(), x, y, z);
    }

    @Redirect(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 2))
    private void ringworld$curveBlockBreaking(
            PoseStack poseStack, double x, double y, double z) {
        applyCurvedPose(poseStack, cameraPosition(), x, y, z);
    }

    @Redirect(
            method = "renderHitOutline",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V"))
    private void ringworld$curveBlockOutline(
            PoseStack poseStack, VertexConsumer vertices, VoxelShape shape,
            double x, double y, double z,
            float red, float green, float blue, float alpha) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) {
            renderShape(poseStack, vertices, shape, x, y, z, red, green, blue, alpha);
            return;
        }

        poseStack.pushPose();
        applyCurvedPose(poseStack, cameraPosition(), x, y, z);
        renderShape(poseStack, vertices, shape, 0.0, 0.0, 0.0,
                red, green, blue, alpha);
        poseStack.popPose();
    }

    private static Vec3 cameraPosition() {
        return net.minecraft.client.Minecraft.getInstance().gameRenderer
                .getMainCamera().getPosition();
    }

    private static void applyCurvedPose(
            PoseStack poseStack, Vec3 camera, double x, double y, double z) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) {
            poseStack.translate(x, y, z);
            return;
        }
        RingObjectTransform transform = RingObjectTransform.fromCameraRelative(
                geometry, camera, x, y, z);
        Vec3 local = transform.cameraLocalPosition();
        poseStack.translate(local.x, local.y, local.z);
        poseStack.mulPose(Axis.ZP.rotation((float)transform.tangentAngleRadians()));
    }
}
