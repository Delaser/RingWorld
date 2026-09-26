package dev.ringworld.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Borrowed, read-only CPU pixels; ownership stays with Minecraft's resource atlas. */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
    @Accessor("byMipLevel") NativeImage[] ringworld$mipImages();
}
