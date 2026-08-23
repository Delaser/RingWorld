package dev.ringworld.client.mixin;

import dev.ringworld.client.RingWorldCreationScreen;
import dev.ringworld.world.RingWorldConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Adds an explicit immutable RingWorld layout editor to world creation. */
@Mixin(CreateWorldScreen.class)
abstract class CreateWorldScreenMixin extends Screen
        implements RingWorldCreationScreen.LayoutButtonOwner {
    @Unique
    private Button ringworld$layoutButton;

    protected CreateWorldScreenMixin(Component title) {
        super(title);
    }

    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;"
                            + "addToFooter(Lnet/minecraft/client/gui/layouts/LayoutElement;)"
                            + "Lnet/minecraft/client/gui/layouts/LayoutElement;"
            )
    )
    private LayoutElement ringworld$addLayoutButton(
            HeaderAndFooterLayout layout, LayoutElement footerElement) {
        RingWorldConfig config = RingWorldConfig.load();
        LinearLayout footer = (LinearLayout) footerElement;
        ringworld$layoutButton = footer.addChild(Button.builder(
                Component.literal("RingWorld %d×%d".formatted(
                        config.circumferenceBlocks(), config.widthBlocks())),
                button -> minecraft.setScreen(new RingWorldCreationScreen(this)))
                .width(120)
                .build());
        return layout.addToFooter(footerElement);
    }

    @Override
    public void ringworld$refreshLayoutButton() {
        if (ringworld$layoutButton == null) return;
        RingWorldConfig config = RingWorldConfig.load();
        Component message = Component.literal("RingWorld %d×%d".formatted(
                config.circumferenceBlocks(), config.widthBlocks()));
        if (!ringworld$layoutButton.getMessage().equals(message)) {
            ringworld$layoutButton.setMessage(message);
        }
    }

    @Override
    public boolean ringworld$layoutButtonReadyForAutomation() {
        return ringworld$layoutButton != null;
    }

    @Override
    public void ringworld$openLayoutEditorForAutomation() {
        if (ringworld$layoutButton == null) {
            throw new IllegalStateException("RingWorld layout footer button was not initialized");
        }
        ringworld$layoutButton.onPress();
    }

    @Override
    public Component ringworld$layoutButtonMessageForAutomation() {
        if (ringworld$layoutButton == null) {
            throw new IllegalStateException("RingWorld layout footer button was not initialized");
        }
        return ringworld$layoutButton.getMessage();
    }
}
