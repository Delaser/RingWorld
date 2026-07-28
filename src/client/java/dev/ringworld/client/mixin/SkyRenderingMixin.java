package dev.ringworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSkyCycle;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fixed toned sun plus the active texture-backed complete-ring surface. */
@Mixin(SkyRenderer.class)
abstract class SkyRenderingMixin {
    @Invoker("renderSun")
    protected abstract void ringworld$invokeRenderSun(float alpha, PoseStack matrices);

    @Unique private float ringworld$cameraY;
    @Unique private float ringworld$cameraZ;
    @Unique private double ringworld$cameraX;
    @Unique private float ringworld$starTiltRadians;
    @Unique private boolean ringworld$renderingCenteredSun;
    @Unique private RingSkyCycle.SunVisual ringworld$sunVisual =
            RingSkyCycle.sunVisual(6_000.0);

    @Inject(method = "close", at = @At("TAIL"))
    private void ringworld$closeSkyGeometry(CallbackInfo ci) {
        RingSurfaceTextureRenderer.clear();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void ringworld$updateFixedSky(ClientLevel world, float tickProgress, Camera camera,
                                          SkyRenderState state, CallbackInfo ci) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;
        state.sunAngle = RingSkyCycle.FIXED_SUN_ANGLE_RADIANS;
        state.moonAngle = RingSkyCycle.FIXED_SUN_ANGLE_RADIANS;
        state.starAngle = 0.0F;
        state.sunriseAndSunsetColor = 0;
        ringworld$sunVisual = RingSkyCycle.sunVisual(world.getOverworldClockTime() + tickProgress);
        ringworld$cameraY = (float)camera.position().y;
        ringworld$cameraZ = (float)camera.position().z;
        ringworld$cameraX = camera.position().x;

        // Project the one physical star at the ring centre into the camera's
        // tangent frame. Crossing the finite width tilts it toward that point.
        Vec3 starDirection = geometry.directionToRingCenter(camera.position());
        ringworld$starTiltRadians = (float)Math.atan2(starDirection.z, starDirection.y);
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void ringworld$hideMoon(MoonPhase phase, float alpha, PoseStack matrices,
                                    CallbackInfo ci) {
        if (ClientRingState.geometry() != null) ci.cancel();
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void ringworld$hideCameraRelativeSun(float alpha, PoseStack matrices,
                                                  CallbackInfo ci) {
        if (ClientRingState.geometry() != null && !ringworld$renderingCenteredSun) {
            ci.cancel();
        }
    }

    @ModifyConstant(method = "renderSun", constant = @Constant(floatValue = 30.0F), require = 2)
    private float ringworld$shrinkCenteredSun(float vanillaHalfWidth) {
        return ClientRingState.geometry() != null && ringworld$renderingCenteredSun
                ? RingSkyCycle.SUN_HALF_WIDTH : vanillaHalfWidth;
    }

    @ModifyArg(
            method = "renderSun",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            index = 1,
            require = 1)
    private Vector4fc ringworld$tintCenteredSun(Vector4fc vanillaColor) {
        if (ClientRingState.geometry() == null || !ringworld$renderingCenteredSun) {
            return vanillaColor;
        }
        return new Vector4f(
                ringworld$sunVisual.red(),
                ringworld$sunVisual.green(),
                ringworld$sunVisual.blue(),
                vanillaColor.w() * ringworld$sunVisual.brightness());
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
    private void ringworld$renderRingAndSun(PoseStack matrices, float sunAngle,
                                            float moonAngle, float starAngle,
                                            MoonPhase moonPhase, float alpha,
                                            float starBrightness, CallbackInfo ci) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;

        RingSurfaceTextureRenderer.render(matrices, geometry,
                new Vec3(ringworld$cameraX, ringworld$cameraY, ringworld$cameraZ),
                alpha);

        // Vanilla drew stars after its first sun. The ring covers those stars
        // but stays behind the central star, so redraw the fixed sun once.
        matrices.pushPose();
        matrices.mulPose(Axis.XP.rotation(ringworld$starTiltRadians));
        matrices.mulPose(Axis.YP.rotationDegrees(-90.0F));
        ringworld$renderingCenteredSun = true;
        try {
            ringworld$invokeRenderSun(alpha, matrices);
        } finally {
            ringworld$renderingCenteredSun = false;
        }
        matrices.popPose();
    }
}
