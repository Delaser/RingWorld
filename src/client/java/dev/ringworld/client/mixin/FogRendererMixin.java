package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingSkyCycle;
import dev.ringworld.world.RingSkyProfile;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps dark RingWorld backdrops and Minecraft's clear/fog colour continuous. */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void ringworld$matchDarkSkyFog(Camera camera, int renderDistance,
                                            DeltaTracker tickCounter,
                                            float darkenWorldAmount, ClientLevel world,
                                            CallbackInfoReturnable<FogData> cir) {
        if (ClientRingState.geometry() == null
                || camera.getFluidInCamera() != FogType.NONE) return;
        RingSkyProfile.Backdrop backdrop = ClientRingState.skyProfile().backdrop();
        if (backdrop == RingSkyProfile.Backdrop.ATMOSPHERE) {
            int wallTop = RingGenerationBoundary.wallTopExclusive(
                    world.getMinY(), world.getHeight(), ClientRingState.wallHeightBlocks());
            float blend = RingSkyCycle.exposedHorizonBlend(camera.position().y, wallTop);
            if (blend > 0.0F) {
                int skyColor = camera.attributeProbe().getValue(
                        EnvironmentAttributes.SKY_COLOR,
                        tickCounter.getGameTimeDeltaPartialTick(false));
                FogData fog = cir.getReturnValue();
                fog.color.set(
                        lerp(fog.color.x, ARGB.redFloat(skyColor), blend),
                        lerp(fog.color.y, ARGB.greenFloat(skyColor), blend),
                        lerp(fog.color.z, ARGB.blueFloat(skyColor), blend),
                        1.0F);
            }
        } else if (backdrop == RingSkyProfile.Backdrop.NIGHT) {
            cir.getReturnValue().color.set(5.0F / 255.0F, 8.0F / 255.0F,
                    16.0F / 255.0F, 1.0F);
        } else if (backdrop == RingSkyProfile.Backdrop.VOID) {
            cir.getReturnValue().color.set(1.0F / 255.0F, 1.0F / 255.0F,
                    3.0F / 255.0F, 1.0F);
        }
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }
}
