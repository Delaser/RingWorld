package dev.ringworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.Optional;
import java.util.OptionalDouble;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import com.mojang.math.Axis;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.client.render.RingSkyView;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSkyCycle;
import dev.ringworld.world.RingSkyProfile;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import org.joml.Vector4f;
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
    @Shadow @Final private RenderTarget renderTarget;
    @Invoker("renderSun")
    protected abstract void ringworld$invokeRenderSun(RenderPass pass, float alpha, PoseStack matrices);

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
        state.sunriseAndSunsetColor = new org.joml.Vector4f();
        RingSkyProfile skyProfile = view.profile();
        switch (skyProfile.backdrop()) {
            case ATMOSPHERE -> { }
            case NIGHT -> {
                state.skyColor = new org.joml.Vector3f(5/255f, 8/255f, 16/255f);
                state.starBrightness = Math.max(state.starBrightness, 0.88F);
            }
            case VOID -> {
                state.skyColor = new org.joml.Vector3f(1/255f, 1/255f, 3/255f);
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
    private void ringworld$hideMoon(RenderPass pass, MoonPhase phase, float alpha, PoseStack matrices,
                                    CallbackInfo ci) {
        if (RingSkyView.current() != null) ci.cancel();
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void ringworld$hideCameraRelativeSun(RenderPass pass, float alpha, PoseStack matrices,
                                                  CallbackInfo ci) {
        RingSkyView view = RingSkyView.current();
        if (view != null && view != ringworld$centeredSunView) {
            ci.cancel();
        }
    }

    @ModifyConstant(method = "renderSun", constant = @Constant(floatValue = 30.0F), require = 1)
    private float ringworld$shrinkCenteredSun(float vanillaHalfWidth) {
        RingSkyView view = RingSkyView.current();
        return view != null && view == ringworld$centeredSunView
                ? view.profile().lightSource().halfWidth() : vanillaHalfWidth;
    }

    @ModifyArg(method = "renderSun", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"),
            index = 1)
    private Vector4f ringworld$tintCenteredSun(Vector4f vanillaColor) {
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

    // 26.3 batches vanilla sky draws in one pass. Wait until it has closed
    // before the Atlas uploads/draws and the final centered sun pass.
    @Inject(method = "render", at = @At("TAIL"))
    private void ringworld$renderRingAndSun(GpuBufferSlice skyFog, SkyRenderState state,
                                           CallbackInfo ci) {
        PoseStack matrices = new PoseStack();
        float alpha = state.rainBrightness;
        RingSkyView view = RingSkyView.current();
        if (view == null) return;

        RingSurfaceTextureRenderer.render(renderTarget, matrices, view, alpha);

        // Vanilla drew stars after its first sun. The ring covers those stars
        // but stays behind the central star, so redraw the fixed sun once.
        if (view.profile().lightSource() != RingSkyProfile.LightSource.NONE) {
            matrices.pushPose();
            matrices.rotate(Axis.XP.rotation(view.starTilt()));
            matrices.rotate(Axis.YP.rotationDegrees(-90.0F));
            RingSkyView previous = ringworld$centeredSunView;
            ringworld$centeredSunView = view;
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(() -> "RingWorld centered sun",
                    renderTarget.getColorTextureView(), Optional.empty(),
                    renderTarget.getDepthTextureView(), OptionalDouble.empty())) {
                ringworld$invokeRenderSun(pass, alpha, matrices);
            } finally {
                ringworld$centeredSunView = previous;
            }
            encoder.submit();
            matrices.popPose();
        }
    }
}
