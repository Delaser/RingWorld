package dev.ringworld.platform.neoforge.compat.create610;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * Entity-space equivalents of chunk layers whose render-state contracts are
 * not preserved by Create's three general block-atlas entity layers.
 */
public final class RingCreate610ContraptionRenderTypes extends RenderStateShard {
    private static final RenderType ENTITY_CUTOUT_BLOCK = RenderType.create(
            "ringworld:create610_contraption_entity_cutout_block",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 786432,
            true, false, RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
                    .setTextureState(BLOCK_SHEET)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setOutputState(MAIN_TARGET)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true));

    private static final RenderType ENTITY_TRANSLUCENT_BLOCK = RenderType.create(
            "ringworld:create610_contraption_entity_translucent_block",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 786432,
            true, true, RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                    .setTextureState(BLOCK_SHEET_MIPPED)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true));

    private static final RenderType ENTITY_TRIPWIRE_BLOCK = RenderType.create(
            "ringworld:create610_contraption_entity_tripwire_block",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536,
            true, true, RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                    .setTextureState(BLOCK_SHEET_MIPPED)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setOutputState(WEATHER_TARGET)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true));

    private RingCreate610ContraptionRenderTypes() {
        super(null, null, null);
    }

    public static RenderType entityCutoutBlock() {
        return ENTITY_CUTOUT_BLOCK;
    }

    public static RenderType entityTranslucentBlock() {
        return ENTITY_TRANSLUCENT_BLOCK;
    }

    public static RenderType entityTripwireBlock() {
        return ENTITY_TRIPWIRE_BLOCK;
    }
}
