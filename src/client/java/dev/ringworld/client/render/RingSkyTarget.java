package dev.ringworld.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;

/** The sky pass supplies its own destination on game versions with per-renderer targets. */
public interface RingSkyTarget {
    RenderTarget ringworld$skyTarget();
}
