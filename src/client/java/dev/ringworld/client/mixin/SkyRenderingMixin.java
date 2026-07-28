package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSkyCycle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.MoonPhase;
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
@Mixin(SkyRendering.class)
abstract class SkyRenderingMixin {
    @Invoker("renderSun")
    protected abstract void ringworld$invokeRenderSun(float alpha, MatrixStack matrices);

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

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void ringworld$updateFixedSky(ClientWorld world, float tickProgress, Camera camera,
                                          SkyRenderState state, CallbackInfo ci) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;
        state.sunAngle = RingSkyCycle.FIXED_SUN_ANGLE_RADIANS;
        state.moonAngle = RingSkyCycle.FIXED_SUN_ANGLE_RADIANS;
        state.starAngle = 0.0F;
        state.sunriseAndSunsetColor = 0;
        ringworld$sunVisual = RingSkyCycle.sunVisual(world.getTimeOfDay() + tickProgress);
        ringworld$cameraY = (float)camera.getCameraPos().y;
        ringworld$cameraZ = (float)camera.getCameraPos().z;
        ringworld$cameraX = camera.getCameraPos().x;

        // Project the one physical star at the ring centre into the camera's
        // tangent frame. Crossing the finite width tilts it toward that point.
        Vec3d starDirection = geometry.directionToRingCenter(camera.getCameraPos());
        ringworld$starTiltRadians = (float)Math.atan2(starDirection.z, starDirection.y);
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void ringworld$hideMoon(MoonPhase phase, float alpha, MatrixStack matrices,
                                    CallbackInfo ci) {
        if (ClientRingState.geometry() != null) ci.cancel();
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void ringworld$hideCameraRelativeSun(float alpha, MatrixStack matrices,
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
                    target = "Lnet/minecraft/client/gl/DynamicUniforms;write("
                            + "Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;"
                            + "Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)"
                            + "Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
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

    @Inject(method = "renderCelestialBodies", at = @At("TAIL"))
    private void ringworld$renderRingAndSun(MatrixStack matrices, float sunAngle,
                                            float moonAngle, float starAngle,
                                            MoonPhase moonPhase, float alpha,
                                            float starBrightness, CallbackInfo ci) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return;

        RingSurfaceTextureRenderer.render(matrices, geometry,
                new Vec3d(ringworld$cameraX, ringworld$cameraY, ringworld$cameraZ),
                alpha);

        // Vanilla drew stars after its first sun. The ring covers those stars
        // but stays behind the central star, so redraw the fixed sun once.
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(ringworld$starTiltRadians));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
        ringworld$renderingCenteredSun = true;
        try {
            ringworld$invokeRenderSun(alpha, matrices);
        } finally {
            ringworld$renderingCenteredSun = false;
        }
        matrices.pop();
    }
}
