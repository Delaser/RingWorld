package dev.ringworld.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ringworld.client.render.RingSkyView;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
abstract class SkyPassViewMixin {
    // NeoForge can retain a delegating overload. Exactly one overload schedules the actual sky draw.
    @ModifyArg(method = "addSkyPass*", allow = 1, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/framegraph/FramePass;executes(Ljava/lang/Runnable;)V"), index = 0)
    private Runnable ringworld$bindSkyView(Runnable draw, @Local SkyRenderState state) {
        return RingSkyView.bind(((RingSkyView.Holder)state).ringworld$view(), draw);
    }
}
