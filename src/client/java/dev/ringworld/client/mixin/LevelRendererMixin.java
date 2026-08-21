package dev.ringworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.render.RingTerrainShaderState;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingObjectTransform;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


/**
 * Curves interaction passes that bypass terrain rendering.
 *
 * TODO 1.21.1: block entities and block-destroy overlays were moved into
 * TODO LevelRenderer#renderLevel in this version and need dedicated injections.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Redirect(
            method = "renderHitOutline",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V"))
    private static void ringworld$curveBlockOutline(PoseStack poseStack, VertexConsumer vertices,
                                                    VoxelShape shape, double x, double y, double z,
                                                    float red, float green, float blue, float alpha) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) {
            LevelRenderer.renderVoxelShape(
                    poseStack, vertices, shape, x, y, z,
                    red, green, blue, alpha, false);
            return;
        }

        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        poseStack.pushPose();
        applyCurvedPose(poseStack, camera, x, y, z);
        LevelRenderer.renderVoxelShape(
                poseStack, vertices, shape, 0.0, 0.0, 0.0,
                red, green, blue, alpha, false);
        poseStack.popPose();
    }

    private static void applyCurvedPose(PoseStack poseStack, Vec3 camera,
                                        double x, double y, double z) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) {
            poseStack.translate(x, y, z);
            return;
        }

        RingObjectTransform transform =
                RingObjectTransform.fromCameraRelative(geometry, camera, x, y, z);

        Vec3 local = transform.cameraLocalPosition();
        poseStack.translate(local.x, local.y, local.z);
        poseStack.mulPose(Axis.ZP.rotation(
                (float)transform.tangentAngleRadians()));
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void ringworld$updateTerrainShader(
            DeltaTracker deltaTracker,
            boolean renderBlockOutline,
            Camera camera,
            GameRenderer gameRenderer,
            LightTexture lightTexture,
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix,
            CallbackInfo ci) {
        RingTerrainShaderState.update(camera);
    }

}