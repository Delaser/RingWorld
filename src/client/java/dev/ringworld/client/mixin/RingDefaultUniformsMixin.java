package dev.ringworld.client.mixin;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ringworld.client.render.RingRenderUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The standard engine binding path also serves mods using Minecraft materials. */
@Mixin(RenderSystem.class)
abstract class RingDefaultUniformsMixin {
    @Inject(method = "bindDefaultUniforms", at = @At("TAIL"))
    private static void ringworld$bindPrivateUniforms(RenderPass pass, CallbackInfo ci) {
        RingRenderUniforms.bind(pass);
    }
}
