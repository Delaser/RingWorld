package dev.ringworld.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.ringworld.client.render.RingSkyTarget;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SkyRenderer.class)
public interface SkyRenderTargetAccessor extends RingSkyTarget {
    @Override
    @Accessor("renderTarget")
    RenderTarget ringworld$skyTarget();
}
