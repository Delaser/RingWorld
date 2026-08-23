package dev.ringworld.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Test-only bridge that exercises the real compass property after mixins apply. */
@Mixin(CompassItemPropertyFunction.class)
public interface CompassAngleStateFixtureAccessor {
    @Invoker("getCompassRotation")
    float ringworld$calculate(ItemStack stack, ClientLevel level, int seed, Entity owner);
}
