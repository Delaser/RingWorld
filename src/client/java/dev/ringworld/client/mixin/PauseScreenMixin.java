package dev.ringworld.client.mixin;

import dev.ringworld.client.RingWorldMapScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the map entry only after the RingWorld geometry acknowledgement is live. */
@Mixin(PauseScreen.class)
abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void ringworld$addMapButton(CallbackInfo callback) {
        if (!RingWorldMapScreen.canOpen()) return;
        addRenderableWidget(Button.builder(Component.literal("RingWorld Map"),
                        button -> minecraft.setScreen(new RingWorldMapScreen((Screen)(Object)this)))
                .bounds(width / 2 - 102, Math.max(24, height / 4 + 96), 204, 20).build());
    }
}
