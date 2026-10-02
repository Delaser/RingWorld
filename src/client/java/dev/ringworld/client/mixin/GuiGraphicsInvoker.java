package dev.ringworld.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Normalized texture coordinates for scaled and panned menu previews. */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsInvoker {
    @Invoker("innerBlit") void ringworld$blit(ResourceLocation texture, int left, int right,
            int top, int bottom, int depth, float u0, float u1, float v0, float v1);
}
