package dev.ringworld.mixin;

import dev.ringworld.world.RingEntityFoldAccess;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import org.spongepowered.asm.mixin.Mixin;

/** TODO: Re-port Eye guidance target folding for Minecraft 1.21.1. */
@Mixin(EyeOfEnder.class)
abstract class EyeOfEnderMixin implements RingEntityFoldAccess {
    @Override
    public void ringworld$onCanonicalFold(double deltaX) {
        // Disabled for initial 1.21.1 backport.
    }
}