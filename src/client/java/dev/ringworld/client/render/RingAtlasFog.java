package dev.ringworld.client.render;

import net.minecraft.client.Camera;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;

/** Restrict Atlas fog to immersion/effects; clear-air distance haze stays authored. */
public final class RingAtlasFog {
    private RingAtlasFog() { }

    public static boolean enabled(Camera camera) {
        return camera.getFluidInCamera() != FogType.NONE
                || camera.entity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }
}
