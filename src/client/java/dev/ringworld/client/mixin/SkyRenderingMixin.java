package dev.ringworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSkyCycle;
import dev.ringworld.world.RingSkyProfile;
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
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fixed toned sun plus the active texture-backed complete-ring surface. */
@Mixin(SkyRenderer.class)
abstract class SkyRenderingMixin {
    @Invoker("renderSun")
    protected abstract void ringworld$invokeRenderSun(float alpha, PoseStack matrices);

    @Invoker("renderDarkDisc")
    protected abstract void ringworld$invokeRenderDarkDisc();

    @Unique private float ringworld$cameraY;
    @Unique private float ringworld$cameraZ;
    @Unique private double ringworld$cameraX;
    @Unique private float ringworld$starTiltRadians;
    @Unique private boolean ringworld$renderingCenteredSun;
    @Unique private boolean ringworld$renderingLowerSky;
    @Unique private int ringworld$skyColor;
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
        // Stars are infinitely distant but directionally fixed in physical
        // ring space. Counter-rotate the player's local tangent frame so a
        // cluster below one side appears overhead on the opposite side.
        state.starAngle = RingSkyCycle.starFieldAngleRadians(
                geometry, camera.position().x);
        state.sunriseAndSunsetColor = 0;
        RingSkyProfile skyProfile = ClientRingState.skyProfile();
        switch (skyProfile.backdrop()) {
            case ATMOSPHERE -> { }
            case NIGHT -> {
                state.skyColor = 0x050810;
                state.starBrightness = Math.max(state.starBrightness, 0.88F);
            }
            case VOID -> {
                state.skyColor = 0x010103;
                state.starBrightness = 0.0F;
            }
        }
        // A cylindrical world has no flat-world under-horizon void. The
        // matching lower sky hemisphere is drawn immediately after the upper
        // disc, so suppress vanilla's later black bottom-disc pass.
        state.shouldRenderDarkDisc = false;
        ringworld$skyColor = state.skyColor;
        ringworld$sunVisual = RingSkyCycle.sunVisual(world.getOverworldClockTime() + tickProgress);
        ringworld$cameraY = (float)camera.position().y;
        ringworld$cameraZ = (float)camera.position().z;
        ringworld$cameraX = camera.position().x;

        // Project the one physical star at the ring centre into the camera's
        // tangent frame. Crossing the finite width tilts it toward that point.
        Vec3 starDirection = geometry.directionToRingCenter(camera.position());
        ringworld$starTiltRadians = (float)Math.atan2(starDirection.z, starDirection.y);
    }

    @Inject(method = "renderSkyDisc", at = @At("TAIL"))
    private void ringworld$renderLowerAtmosphere(int skyColor, CallbackInfo ci) {
        if (ClientRingState.geometry() == null || ringworld$renderingLowerSky) return;
        ringworld$renderingLowerSky = true;
        try {
            ringworld$invokeRenderDarkDisc();
        } finally {
            ringworld$renderingLowerSky = false;
        }
    }

    @ModifyConstant(method = "renderDarkDisc", constant = @Constant(floatValue = 12.0F))
    private float ringworld$centerLowerAtmosphere(float vanillaTranslation) {
        return ringworld$renderingLowerSky ? 0.0F : vanillaTranslation;
    }

    @Group(name = "ringworldLowerSkyTint", min = 1, max = 1)
    @ModifyArg(
            method = "renderDarkDisc",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            index = 1,
            require = 0)
    private Vector4fc ringworld$tintLowerAtmosphere(Vector4fc vanillaColor) {
        return ringworld$renderingLowerSky
                ? net.minecraft.util.ARGB.vector4fFromARGB32(ringworld$skyColor)
                : vanillaColor;
    }

    @Group(name = "ringworldLowerSkyTint", min = 1, max = 1)
    @ModifyArg(
            method = "renderDarkDisc",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            index = 1,
            require = 0)
    private Vector4f ringworld$tintLowerAtmosphere26_2(Vector4f vanillaColor) {
        return new Vector4f(ringworld$tintLowerAtmosphere(vanillaColor));
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
                ? ClientRingState.skyProfile().lightSource().halfWidth() : vanillaHalfWidth;
    }

    @Group(name = "ringworldSunTint", min = 1, max = 1)
    @ModifyArg(
            method = "renderSun",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            index = 1,
            require = 0)
    private Vector4fc ringworld$tintCenteredSun(Vector4fc vanillaColor) {
        if (ClientRingState.geometry() == null || !ringworld$renderingCenteredSun) {
            return vanillaColor;
        }
        return new Vector4f(
                ringworld$sunVisual.red(),
                ringworld$sunVisual.green(),
                ringworld$sunVisual.blue(),
                vanillaColor.w() * ringworld$sunVisual.brightness()
                        * (ClientRingState.skyProfile().lightSource()
                                == RingSkyProfile.LightSource.LARGE ? 0.72F : 1.0F));
    }

    @Group(name = "ringworldSunTint", min = 1, max = 1)
    @ModifyArg(method = "renderSun", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            index = 1, require = 0)
    private Vector4f ringworld$tintCenteredSun26_2(Vector4f vanillaColor) {
        return new Vector4f(ringworld$tintCenteredSun(vanillaColor));
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
        if (ClientRingState.skyProfile().lightSource() != RingSkyProfile.LightSource.NONE) {
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
}
