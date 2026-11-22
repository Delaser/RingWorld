package dev.ringworld.client.mixin;

import dev.ringworld.client.render.RingSkyView;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderState.class)
abstract class SkyRenderStateMixin implements RingSkyView.Holder {
    @Unique private RingSkyView ringworld$view;

    @Inject(method = "reset", at = @At("HEAD"))
    private void ringworld$forgetView(CallbackInfo ci) {
        ringworld$view = null;
    }

    @Override public RingSkyView ringworld$view() { return ringworld$view; }
    @Override public void ringworld$view(RingSkyView view) { ringworld$view = view; }
}
