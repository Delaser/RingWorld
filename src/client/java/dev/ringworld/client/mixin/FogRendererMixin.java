package dev.ringworld.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingSkyCycle;
import dev.ringworld.world.RingSkyProfile;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Matches ordinary-air RingWorld fog colour to the installed sky profile. */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

    @Inject(
            method = "setupColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)V",
            at = @At("TAIL"))
    private static void ringworld$matchSkyFog(
            Camera camera, float tickProgress, ClientLevel level,
            int renderDistanceChunks, float darkenWorldAmount, CallbackInfo ci) {
        if (ClientRingState.geometry() == null || level.dimension() != Level.OVERWORLD
                || camera.getFluidInCamera() != FogType.NONE
                || hasSpecialFog(camera, level)) return;

        RingSkyProfile profile = ClientRingState.skyProfile();
        int wallTopY = RingGenerationBoundary.wallTopExclusive(
                level.getMinBuildHeight(), level.getHeight(),
                ClientRingState.wallHeightBlocks());
        float blend = RingSkyCycle.exposedHorizonBlend(camera.getPosition().y, wallTopY);
        Vec3 sky = level.getSkyColor(camera.getPosition(), tickProgress);
        RingSkyCycle.FogColor matched = RingSkyCycle.fogColor(
                profile,
                new RingSkyCycle.FogColor(fogRed, fogGreen, fogBlue),
                new RingSkyCycle.FogColor((float)sky.x, (float)sky.y, (float)sky.z),
                blend);
        fogRed = matched.red();
        fogGreen = matched.green();
        fogBlue = matched.blue();
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }

    private static boolean hasSpecialFog(Camera camera, ClientLevel level) {
        Vec3 position = camera.getPosition();
        if (level.effects().isFoggyAt(Mth.floor(position.x), Mth.floor(position.y))
                || Minecraft.getInstance().gui.getBossOverlay().shouldCreateWorldFog()) {
            return true;
        }
        return camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS)
                    || living.hasEffect(MobEffects.DARKNESS));
    }
}
