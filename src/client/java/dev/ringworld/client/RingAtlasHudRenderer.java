package dev.ringworld.client;

import dev.ringworld.world.RingAtlasHudProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Compact play-screen progress and temporary staged-preview diagnostics. */
public final class RingAtlasHudRenderer {
    private static final float SCALE = 0.5F;
    private static final int LEFT = 12;
    private static final int TOP = 12;
    private static final int PADDING = 3;
    private static final int BACKGROUND = 0xA0000000;
    private static final int TEXT = 0xFFFFFFFF;

    private RingAtlasHudRenderer() { }

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        var atlas = ClientRingState.terrainAtlas();
        if (atlas == null) return;
        RingAtlasHudProgress.label(atlas.presentCount(), atlas.cellCount()).ifPresent(label -> {
            Component text = Component.literal(label);
            int width = minecraft.font.width(text);

            graphics.pose().pushMatrix();
            graphics.pose().scale(SCALE, SCALE);
            graphics.fill(LEFT, TOP, LEFT + width + PADDING * 2,
                    TOP + minecraft.font.lineHeight + PADDING * 2, BACKGROUND);
            graphics.text(minecraft.font, text, LEFT + PADDING, TOP + PADDING, TEXT);
            graphics.pose().popMatrix();
        });
    }
}
