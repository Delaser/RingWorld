package dev.ringworld.client.mixin;

import dev.ringworld.client.RingWorldCreationScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shows where the vanilla seed came from, directly beside its input. */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
abstract class CreateWorldSeedTabMixin extends GridLayoutTab {
    @Shadow @Final private EditBox seedEdit;

    protected CreateWorldSeedTabMixin(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ringworld$labelAppliedSeed(CreateWorldScreen screen, CallbackInfo ci) {
        layout.visitWidgets(widget -> {
            if (!(widget instanceof StringWidget label)
                    || !label.getMessage().equals(Component.translatable("selectWorld.enterSeed"))) return;
            RingWorldCreationScreen.LayoutButtonOwner owner =
                    (RingWorldCreationScreen.LayoutButtonOwner) screen;
            Runnable refresh = () -> label.setMessage(owner.ringworld$hasAppliedPreviewSeed()
                    ? Component.literal("Seed · RingWorld preview applied")
                    : Component.translatable("selectWorld.enterSeed"));
            refresh.run();
            screen.getUiState().addListener(state -> {
                if (!seedEdit.getValue().equals(state.getSeed())) seedEdit.setValue(state.getSeed());
                refresh.run();
            });
        });
    }
}
