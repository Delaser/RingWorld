package dev.ringworld.client.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Narrow test-only access to exercise ConfirmScreen's real affirmative callback. */
@Mixin(ConfirmScreen.class)
public interface ConfirmScreenAccessor {
    @Accessor("yesButton") Button ringworld$yesButton();

    @Accessor("message") Component ringworld$message();
}
