package dev.ringworld.client.mixin;

import dev.ringworld.client.RingWorldCreationScreen;
import dev.ringworld.world.RingWorldConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds an explicit immutable RingWorld layout editor to world creation. */
@Mixin(CreateWorldScreen.class)
abstract class CreateWorldScreenMixin extends Screen {
    protected CreateWorldScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ringworld$addLayoutButton(CallbackInfo ci) {
        RingWorldConfig config = RingWorldConfig.load();
        addRenderableWidget(Button.builder(
                Component.literal("RingWorld %d×%d".formatted(
                        config.circumferenceBlocks(), config.widthBlocks())),
                button -> minecraft.setScreen(new RingWorldCreationScreen(this)))
                .bounds(8, height - 28, 150, 20)
                .build());
    }
}
