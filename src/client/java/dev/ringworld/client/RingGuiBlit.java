package dev.ringworld.client;

import dev.ringworld.client.mixin.GuiGraphicsInvoker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class RingGuiBlit {
    private RingGuiBlit() { }
    static void blit(GuiGraphics graphics, ResourceLocation texture, int left, int top,
                     int right, int bottom, float u0, float u1, float v0, float v1) {
        ((GuiGraphicsInvoker) (Object) graphics).ringworld$blit(texture, left, right,
                top, bottom, 0, u0, u1, v0, v1);
    }
}
