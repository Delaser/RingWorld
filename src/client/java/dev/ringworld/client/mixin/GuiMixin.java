package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingAtlasHudProgress;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shows unobtrusive progress while the current world's terrain Atlas fills. */
@Mixin(Gui.class)
abstract class GuiMixin {
    private static final int LEFT = 6;
    private static final int TOP = 6;
    private static final int PADDING = 3;
    private static final int BACKGROUND = 0xA0000000;
    private static final int TEXT = 0xFFFFFFFF;

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "render", at = @At("TAIL"))
    private void ringworld$renderAtlasProgress(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (minecraft.options.hideGui || minecraft.level == null || ClientRingState.geometry() == null) return;

        var atlas = ClientRingState.terrainAtlas();
        if (atlas == null) return;

        RingAtlasHudProgress.label(atlas.presentCount(), atlas.cellCount()).ifPresent(label -> {
            Component text = Component.literal(label);
            int width = minecraft.font.width(text);

            graphics.fill(LEFT, TOP, LEFT + width + PADDING * 2,
                    TOP + minecraft.font.lineHeight + PADDING * 2, BACKGROUND);

            graphics.drawString(minecraft.font, text,
                    LEFT + PADDING, TOP + PADDING, TEXT);
        });
    }
}
