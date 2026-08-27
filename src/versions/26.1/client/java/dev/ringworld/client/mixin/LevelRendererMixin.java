package dev.ringworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingObjectTransform;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Curves object and interaction passes that bypass the terrain shader. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Redirect(
            method = "submitBlockEntities",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private void ringworld$curveBlockEntity(
            PoseStack poseStack, double x, double y, double z,
            PoseStack originalPoseStack, LevelRenderState state,
            SubmitNodeStorage submitNodes) {
        applyCurvedPose(poseStack, state.cameraRenderState.pos, x, y, z);
    }

    @Redirect(
            method = "submitBlockDestroyAnimation",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private void ringworld$curveBlockBreaking(
            PoseStack poseStack, double x, double y, double z,
            PoseStack originalPoseStack, SubmitNodeCollector submitNodes,
            LevelRenderState state) {
        applyCurvedPose(poseStack, state.cameraRenderState.pos, x, y, z);
    }

    @Redirect(
            method = "renderHitOutline",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShapeRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDIF)V"))
    private void ringworld$curveBlockOutline(
            PoseStack poseStack, VertexConsumer vertices, VoxelShape shape,
            double x, double y, double z, int color, float lineWidth) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) {
            ShapeRenderer.renderShape(poseStack, vertices, shape, x, y, z, color, lineWidth);
            return;
        }

        Vec3 camera = net.minecraft.client.Minecraft.getInstance().gameRenderer
                .getMainCamera().position();
        poseStack.pushPose();
        applyCurvedPose(poseStack, camera, x, y, z);
        ShapeRenderer.renderShape(poseStack, vertices, shape, 0.0, 0.0, 0.0, color, lineWidth);
        poseStack.popPose();
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
