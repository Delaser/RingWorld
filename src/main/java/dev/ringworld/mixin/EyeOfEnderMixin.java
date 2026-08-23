package dev.ringworld.mixin;

import dev.ringworld.world.RingEntityFoldAccess;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Keeps the Eye's transient guidance target on the same chart after a seam fold. */
@Mixin(EyeOfEnder.class)
abstract class EyeOfEnderMixin implements RingEntityFoldAccess {
    @Shadow private double tx;

    @Override
    public void ringworld$onCanonicalFold(double deltaX) {
        tx += deltaX;
    }
}
