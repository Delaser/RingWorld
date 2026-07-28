package dev.ringworld.client.mixin;

import dev.ringworld.client.RingWorldCreationScreen;
import dev.ringworld.world.RingWorldConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds an explicit immutable RingWorld layout editor to world creation. */
@Mixin(CreateWorldScreen.class)
abstract class CreateWorldScreenMixin extends Screen {
    protected CreateWorldScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ringworld$addLayoutButton(CallbackInfo ci) {
        RingWorldConfig config = RingWorldConfig.load();
        addDrawableChild(ButtonWidget.builder(
                Text.literal("RingWorld %d×%d".formatted(
                        config.circumferenceBlocks(), config.widthBlocks())),
                button -> client.setScreen(new RingWorldCreationScreen(this)))
                .dimensions(8, height - 28, 150, 20)
                .build());
    }
}
