package dev.ringworld.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ringworld.client.ClientRingState;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Narrow compositor adapter for Minecraft 1.21.1's opaque chunk layers.
 *
 * <p>The legacy solid and cutout render types disable blending, so shader
 * alpha alone cannot continuously hand their visible surface to the Atlas.
 * Keep depth testing and writes unchanged and enable ordinary source-alpha
 * composition only while those RingWorld section buffers draw.</p>
 */
@Mixin(LevelRenderer.class)
abstract class LegacyTerrainHandoffBlendMixin {
    @Unique
    private boolean ringworld$terrainHandoffBlendActive;

    @Inject(
            method = "renderSectionLayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;setupRenderState()V",
                    shift = At.Shift.AFTER))
    private void ringworld$beginTerrainHandoffBlend(
            RenderType renderType, double cameraX, double cameraY, double cameraZ,
            Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        ringworld$terrainHandoffBlendActive = false;
        if (ClientRingState.geometry() == null
                || !ringworld$usesLegacyHandoffBlend(renderType)) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        ringworld$terrainHandoffBlendActive = true;
    }

    // Restore before ShaderInstance.clear(). NeoForge dispatches its
    // after-layer render-stage callback later in this method but before
    // RenderType.clearRenderState(), and that callback must not inherit blend.
    @Inject(
            method = "renderSectionLayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ShaderInstance;clear()V",
                    shift = At.Shift.BEFORE))
    private void ringworld$endTerrainHandoffBlend(
            RenderType renderType, double cameraX, double cameraY, double cameraZ,
            Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        if (!ringworld$terrainHandoffBlendActive) return;
        ringworld$terrainHandoffBlendActive = false;
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    @Unique
    private static boolean ringworld$usesLegacyHandoffBlend(RenderType renderType) {
        return renderType == RenderType.solid()
                || renderType == RenderType.cutoutMipped()
                || renderType == RenderType.cutout();
    }
}
