package dev.ringworld.client.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Narrow test-only access to exercise ConfirmScreen's real affirmative callback. */
@Mixin(ConfirmScreen.class)
public interface ConfirmScreenAccessor {
    @Accessor("exitButtons")
    List<Button> ringworld$exitButtons();
}