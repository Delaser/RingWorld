package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingSkyProfile;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps RingWorld sky and atmospheric fog continuous at every height. */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void ringworld$matchSkyFog(Camera camera, int renderDistance,
                                            DeltaTracker tickCounter,
                                            float darkenWorldAmount, ClientLevel world,
                                            CallbackInfoReturnable<FogData> cir) {
        if (ClientRingState.geometry() == null
                || camera.getFluidInCamera() != FogType.NONE) return;
        RingSkyProfile.Backdrop backdrop = ClientRingState.skyProfile().backdrop();
        if (backdrop == RingSkyProfile.Backdrop.ATMOSPHERE) {
            // Special darkness must keep its vanilla fog colour as well as range.
            if (camera.entity() instanceof LivingEntity living
                    && (living.hasEffect(MobEffects.BLINDNESS)
                    || living.hasEffect(MobEffects.DARKNESS))) return;
            // Match both sky discs and the uncovered framebuffer at every
            // height; a cylinder has no privileged horizontal horizon.
            org.joml.Vector3fc skyColor = camera.attributeProbe().getValue(
                    EnvironmentAttributes.SKY_COLOR,
                    tickCounter.getGameTimeDeltaPartialTick(false));
            cir.getReturnValue().color.set(skyColor.x(), skyColor.y(), skyColor.z(), 1.0F);
        } else if (backdrop == RingSkyProfile.Backdrop.NIGHT) {
            cir.getReturnValue().color.set(5.0F / 255.0F, 8.0F / 255.0F,
                    16.0F / 255.0F, 1.0F);
        } else if (backdrop == RingSkyProfile.Backdrop.VOID) {
            cir.getReturnValue().color.set(1.0F / 255.0F, 1.0F / 255.0F,
                    3.0F / 255.0F, 1.0F);
        }
    }
}
