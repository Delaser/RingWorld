package dev.ringworld.mixin;

import dev.ringworld.world.RingEntityFoldAccess;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Keeps the Eye's transient guidance target on the same chart after a seam fold. */
@Mixin(EyeOfEnder.class)
abstract class EyeOfEnderMixin implements RingEntityFoldAccess {
    @Shadow private @Nullable Vec3 target;

    @Override
    public void ringworld$onCanonicalFold(double deltaX) {
        if (target != null) target = target.add(deltaX, 0.0, 0.0);
    }
}
