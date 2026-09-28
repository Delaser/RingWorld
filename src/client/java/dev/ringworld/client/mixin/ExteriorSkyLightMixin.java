package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The unsent finite exterior is open sky, not an unlit unloaded interior. */
@Mixin(LightEngine.class)
abstract class ExteriorSkyLightMixin {
    @Shadow @Final protected LightChunkGetter chunkSource;

    @Inject(method = "getLightValue", at = @At("HEAD"), cancellable = true)
    private void ringworld$lightExteriorSky(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        var geometry = ClientRingState.geometry();
        if ((Object)this instanceof SkyLightEngine && chunkSource.getLevel() instanceof ClientLevel
                && geometry != null && (pos.getZ() < geometry.minWidthZ() || pos.getZ() > geometry.maxWidthZ()))
            cir.setReturnValue(15);
    }
}
