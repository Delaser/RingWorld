package dev.ringworld.client;

import dev.ringworld.world.RingWallStyle;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** One asset/UV contract for the editor and standalone wall selector. */
final class RingWallPreviewSamples {
    private static final int WIDTH = 384, HEIGHT = 213;
    private RingWallPreviewSamples() { }

    static ResourceLocation texture(RingWallStyle.Palette palette, RingWallStyle.Pattern pattern) {
        if (!Arrays.asList(RingWallStyle.Pattern.selectableValues()).contains(pattern)) return null;
        return ResourceLocation.fromNamespaceAndPath("ringworld", "textures/gui/wall_samples/wall-"
                + palette.name().toLowerCase(Locale.ROOT) + "-"
                + pattern.name().toLowerCase(Locale.ROOT) + ".png");
    }

    static int heightForWidth(int width) { return Math.round(width * (float)HEIGHT / WIDTH); }
    static int widthForHeight(int height) { return height * WIDTH / HEIGHT; }

    static boolean draw(GuiGraphics graphics, Minecraft client, ResourceLocation texture,
                        int left, int top, int width, int height) {
        if (texture == null || client.getResourceManager().getResource(texture).isEmpty()) return false;
        RingGuiBlit.blit(graphics, texture, left, top, left + width, top + height, 0, 1, 0, 1);
        return true;
    }
}
