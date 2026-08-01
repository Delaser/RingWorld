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
                // Keep this independent of vanilla's vertically packed menu.
                // At GUI scale 4, adding another full-width row to that stack
                // overlaps Save and Quit on a 1080p window.
                .bounds(Math.max(4, width - 108), 6, 102, 20).build());
    }
}
