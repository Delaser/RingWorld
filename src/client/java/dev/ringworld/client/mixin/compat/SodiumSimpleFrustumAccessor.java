package dev.ringworld.client.mixin.compat;

import org.joml.FrustumIntersection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.viewport.frustum.SimpleFrustum", remap = false)
public interface SodiumSimpleFrustumAccessor {
    @Accessor("frustum") FrustumIntersection ringworld$planes();
}
