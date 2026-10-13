package dev.ringworld.client.mixin.compat;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.RingDistortionTuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Refresh only visibility when a stationary player's projection settings change. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
abstract class SodiumProjectionChangesMixin {
    @Shadow public abstract void markGraphDirty();
    @Unique private boolean ringworld$initialized, ringworld$enabled;
    @Unique private int ringworld$chunks;

    @Inject(method = "needsUpdate", at = @At("HEAD"))
    private void ringworld$projectionChanged(CallbackInfoReturnable<Boolean> cir) {
        if (ClientRingState.geometry() == null) return;
        boolean enabled = RingDistortionTuning.enabled();
        int chunks = RingDistortionTuning.chunks();
        if (!ringworld$initialized || enabled != ringworld$enabled || chunks != ringworld$chunks) {
            ringworld$initialized = true;
            ringworld$enabled = enabled;
            ringworld$chunks = chunks;
            markGraphDirty();
        }
    }
}
