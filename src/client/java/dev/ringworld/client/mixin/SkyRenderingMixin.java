package dev.ringworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ringworld.client.RingMinecraftClientAccess;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.client.render.RingSkyTarget;
import dev.ringworld.client.render.RingSkyView;
import net.minecraft.client.Minecraft;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSkyCycle;
import dev.ringworld.world.RingSkyProfile;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
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

    @Unique private RingSkyView ringworld$centeredSunView;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void ringworld$updateFixedSky(ClientLevel world, float tickProgress, Camera camera,
                                          SkyRenderState state, CallbackInfo ci) {
        RingSkyView view = RingSkyView.capture(world, camera, tickProgress);
        ((RingSkyView.Holder)state).ringworld$view(view);
        if (view == null) return;
        RingGeometry geometry = view.geometry();
        state.sunAngle = RingSkyCycle.FIXED_SUN_ANGLE_RADIANS;
        state.moonAngle = RingSkyCycle.FIXED_SUN_ANGLE_RADIANS;
        // Stars are infinitely distant but directionally fixed in physical
        // ring space. Counter-rotate the player's local tangent frame so a
        // cluster below one side appears overhead on the opposite side.
        state.starAngle = RingSkyCycle.starFieldAngleRadians(
                geometry, camera.position().x);
        state.sunriseAndSunsetColor = 0;
        RingSkyProfile skyProfile = view.profile();
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
        // FogRenderer clears to the live backdrop. Flat sky discs would
        // reintroduce a horizontal boundary over that continuous background.
        state.shouldRenderDarkDisc = false;
    }

    @Inject(method = {"renderSkyDisc", "renderDarkDisc", "renderSunriseAndSunset"},
            at = @At("HEAD"), cancellable = true)
    private void ringworld$hideFlatSky(CallbackInfo ci) {
        if (RingSkyView.current() != null) ci.cancel();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void ringworld$hideMoon(MoonPhase phase, float alpha, PoseStack matrices,
                                    CallbackInfo ci) {
        if (RingSkyView.current() != null) ci.cancel();
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void ringworld$hideCameraRelativeSun(float alpha, PoseStack matrices,
                                                  CallbackInfo ci) {
        RingSkyView view = RingSkyView.current();
        if (view != null && view != ringworld$centeredSunView) {
            ci.cancel();
        }
    }

    @ModifyConstant(method = "renderSun", constant = @Constant(floatValue = 30.0F), require = 2)
    private float ringworld$shrinkCenteredSun(float vanillaHalfWidth) {
        RingSkyView view = RingSkyView.current();
        return view != null && view == ringworld$centeredSunView
                ? view.profile().lightSource().halfWidth() : vanillaHalfWidth;
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
        RingSkyView view = RingSkyView.current();
        if (view == null || view != ringworld$centeredSunView) {
            return vanillaColor;
        }
        return new Vector4f(
                view.sun().red(),
                view.sun().green(),
                view.sun().blue(),
                vanillaColor.w() * view.sun().brightness()
                        * (view.profile().lightSource()
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
        RingSkyView view = RingSkyView.current();
        if (view == null) return;

        var target = this instanceof RingSkyTarget sky ? sky.ringworld$skyTarget()
                : RingMinecraftClientAccess.mainRenderTarget(Minecraft.getInstance());
        RingSurfaceTextureRenderer.render(target, matrices, view, alpha);

        // Vanilla drew stars after its first sun. The ring covers those stars
        // but stays behind the central star, so redraw the fixed sun once.
        if (view.profile().lightSource() != RingSkyProfile.LightSource.NONE) {
            matrices.pushPose();
            matrices.mulPose(Axis.XP.rotation(view.starTilt()));
            matrices.mulPose(Axis.YP.rotationDegrees(-90.0F));
            RingSkyView previous = ringworld$centeredSunView;
            ringworld$centeredSunView = view;
            try {
                ringworld$invokeRenderSun(alpha, matrices);
            } finally {
                ringworld$centeredSunView = previous;
            }
            matrices.popPose();
        }
    }
}
