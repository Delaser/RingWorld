package dev.ringworld.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.client.render.RingCloudShaderState;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSkyCycle;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fixed toned centre-star plus the texture-backed complete-ring surface for 1.21.1. */
@Mixin(LevelRenderer.class)
abstract class SkyRenderingMixin {
    @Shadow @Final private static ResourceLocation SUN_LOCATION;

    @Inject(method = "close", at = @At("TAIL"))
    private void ringworld$closeSkyGeometry(CallbackInfo ci) {
        RingSurfaceTextureRenderer.clear();
    }

    /** Hide the camera-relative vanilla sun; it is redrawn after the ring. */
    @ModifyConstant(method = "renderSky", constant = @Constant(floatValue = 30.0F))
    private float ringworld$hideVanillaSun(float vanillaHalfWidth) {
        return ClientRingState.geometry() == null ? vanillaHalfWidth : 0.0F;
    }

    /** A RingWorld has one central star and no opposite-side moon. */
    @ModifyConstant(method = "renderSky", constant = @Constant(floatValue = 20.0F))
    private float ringworld$hideMoon(float vanillaHalfWidth) {
        return ClientRingState.geometry() == null ? vanillaHalfWidth : 0.0F;
    }

    @Inject(method = "renderClouds", at = @At("HEAD"))
    private void ringworld$updateCloudTransform(PoseStack poseStack,
                                                Matrix4f viewMatrix,
                                                Matrix4f projectionMatrix,
                                                float tickProgress,
                                                double cameraX,
                                                double cameraY,
                                                double cameraZ,
                                                CallbackInfo ci) {
        RingCloudShaderState.update(tickProgress, cameraX, cameraY, cameraZ);
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void ringworld$beginRingSurfaceFrame(
            DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
            GameRenderer gameRenderer, LightTexture lightTexture,
            Matrix4f viewMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (ClientRingState.geometry() != null) {
            RingSurfaceTextureRenderer.beginLegacyProxyFrame();
        }
    }

    @Redirect(
            method = "renderClouds",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getCloudHeight()F"))
    private float ringworld$attachCloudsToRim(DimensionSpecialEffects effects) {
        return ClientRingState.geometry() == null
                ? effects.getCloudHeight() : RingCloudShaderState.cloudBaseY();
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;compileSections(Lnet/minecraft/client/Camera;)V",
                    shift = At.Shift.AFTER))
    private void ringworld$renderRingAndSunAfterSectionSetup(
            DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
            GameRenderer gameRenderer, LightTexture lightTexture,
            Matrix4f viewMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        RingGeometry geometry = ClientRingState.geometry();
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        Vec3 cameraPosition = camera.getPosition();
        boolean renderBlocked = level != null
                && (level.effects().isFoggyAt(
                        Mth.floor(cameraPosition.x), Mth.floor(cameraPosition.y))
                    || client.gui.getBossOverlay().shouldCreateWorldFog());
        if (geometry == null || level == null || renderBlocked
                || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL
                || skyBlockedByCamera(camera)) return;

        float tickProgress = deltaTracker.getGameTimeDeltaPartialTick(false);
        float weatherAlpha = 1.0F - level.getRainLevel(tickProgress);
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(viewMatrix);
        RingSurfaceTextureRenderer.render(poseStack, geometry, cameraPosition, weatherAlpha);

        RingSkyCycle.SunVisual visual = RingSkyCycle.sunVisual(
                level.getGameTime() + tickProgress);
        Vec3 starDirection = geometry.directionToRingCenter(cameraPosition);
        float starTiltRadians = (float)Math.atan2(starDirection.z, starDirection.y);

        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotation(starTiltRadians));
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        Matrix4f pose = poseStack.last().pose();
        float halfWidth = RingSkyCycle.SUN_HALF_WIDTH;

        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SUN_LOCATION);
        RenderSystem.setShaderColor(visual.red(), visual.green(), visual.blue(),
                weatherAlpha * visual.brightness());
        BufferBuilder vertices = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        vertices.addVertex(pose, -halfWidth, 100.0F, -halfWidth).setUv(0.0F, 0.0F);
        vertices.addVertex(pose, halfWidth, 100.0F, -halfWidth).setUv(1.0F, 0.0F);
        vertices.addVertex(pose, halfWidth, 100.0F, halfWidth).setUv(1.0F, 1.0F);
        vertices.addVertex(pose, -halfWidth, 100.0F, halfWidth).setUv(0.0F, 1.0F);
        BufferUploader.drawWithShader(vertices.buildOrThrow());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        poseStack.popPose();
    }

    private static boolean skyBlockedByCamera(Camera camera) {
        FogType fog = camera.getFluidInCamera();
        if (fog == FogType.POWDER_SNOW || fog == FogType.LAVA) return true;
        return camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS)
                    || living.hasEffect(MobEffects.DARKNESS));
    }
}
