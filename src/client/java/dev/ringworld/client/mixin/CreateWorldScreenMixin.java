package dev.ringworld.client.mixin;

import dev.ringworld.client.RingWorldCreationScreen;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingWorldCreationUiModel;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
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
    @Unique
    private String ringworld$appliedPreviewSeed;

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
                ringworld$buttonLabel(config),
                button -> dev.ringworld.client.RingMinecraftClientAccess.setScreen(minecraft,
                        new dev.ringworld.client.RingWorldEditorScreen(this)))
                .width(120)
                .build());
        ringworld$layoutButton.setTooltip(ringworld$buttonTooltip(config));
        return layout.addToFooter(footerElement);
    }

    @Override
    public void ringworld$refreshLayoutButton() {
        if (ringworld$layoutButton == null) return;
        RingWorldConfig config = RingWorldConfig.load();
        Component message = ringworld$buttonLabel(config);
        ringworld$layoutButton.setTooltip(ringworld$buttonTooltip(config));
        if (!ringworld$layoutButton.getMessage().equals(message)) {
            ringworld$layoutButton.setMessage(message);
        }
    }

    @Unique
    private static Component ringworld$buttonLabel(RingWorldConfig config) {
        String size = "Custom";
        for (RingWorldCreationUiModel.Preset preset : new RingWorldCreationUiModel.Preset[]{
                RingWorldCreationUiModel.SMALL, RingWorldCreationUiModel.MEDIUM,
                RingWorldCreationUiModel.LARGE}) {
            if (config.circumferenceBlocks() == preset.circumferenceBlocks()
                    && config.widthBlocks() == preset.widthBlocks()
                    && config.wallHeightBlocks() == preset.wallHeightBlocks()) {
                size = preset.label();
                break;
            }
        }
        return Component.literal("RingWorld: " + size);
    }

    @Unique
    private static Tooltip ringworld$buttonTooltip(RingWorldConfig config) {
        return Tooltip.create(Component.literal("RingWorld: %,d × %,d blocks; wall height %d"
                .formatted(config.circumferenceBlocks(), config.widthBlocks(),
                        config.wallHeightBlocks())));
    }

    @Override
    public String ringworld$seedText() {
        return ((CreateWorldScreen)(Object)this).getUiState().getSeed();
    }

    @Override
    public long ringworld$resolvedSeed() {
        return ((CreateWorldScreen)(Object)this).getUiState().getSettings().options().seed();
    }

    @Override
    public void ringworld$setSeedText(String seed) {
        ((CreateWorldScreen)(Object)this).getUiState().setSeed(seed);
    }

    @Override
    public void ringworld$setPreviewSeedText(String seed) {
        ringworld$appliedPreviewSeed = seed;
        ringworld$setSeedText(seed);
    }

    @Override
    public boolean ringworld$hasAppliedPreviewSeed() {
        if (ringworld$appliedPreviewSeed != null
                && !ringworld$appliedPreviewSeed.equals(ringworld$seedText())) {
            ringworld$appliedPreviewSeed = null;
        }
        return ringworld$appliedPreviewSeed != null;
    }

    @Override
    public WorldCreationContext ringworld$creationContext() {
        return ((CreateWorldScreen)(Object)this).getUiState().getSettings();
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
        dev.ringworld.client.RingMinecraftClientAccess.setScreen(minecraft, new RingWorldCreationScreen(this));
    }

    @Override
    public void ringworld$openNewEditorForAutomation() {
        if (ringworld$layoutButton == null) {
            throw new IllegalStateException("RingWorld layout footer button was not initialized");
        }
        ringworld$layoutButton.onPress(RingWorldCreationScreen.AutomationInput.INSTANCE);
    }

    @Override
    public Component ringworld$layoutButtonMessageForAutomation() {
        if (ringworld$layoutButton == null) {
            throw new IllegalStateException("RingWorld layout footer button was not initialized");
        }
        return ringworld$layoutButton.getMessage();
    }
}
