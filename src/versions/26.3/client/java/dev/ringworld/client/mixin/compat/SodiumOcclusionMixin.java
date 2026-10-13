package dev.ringworld.client.mixin.compat;

import dev.ringworld.client.ClientRingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Newer Sodium snapshots the supplied policy into its asynchronous cull task. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
abstract class SodiumOcclusionMixin {
    @ModifyVariable(method = "prepareRenderTrees", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean ringworld$curvedVisibility(boolean original) {
        return ClientRingState.geometry() == null && original;
    }
}
