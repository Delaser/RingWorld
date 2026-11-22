package dev.ringworld.experiment;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import dev.ringworld.experiment.mixin.BlockPipelineAccess;
import dev.ringworld.experiment.mixin.RenderTypeAccess;

import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class PatchMaterial {
    public static final Identifier ID =
            Identifier.fromNamespaceAndPath("ringworldtrial", "pipeline/atlas_patch");
    private static final java.util.Map<Identifier, RenderType> types = new java.util.HashMap<>();
    private static RenderPipeline pipeline;
    public static float opticalRange;

    static RenderType material() {
        return textured(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
    }

    static RenderType textured(Identifier texture) {
        if (pipeline == null) {
            pipeline =
                    RenderPipeline.builder(BlockPipelineAccess.block())
                            .withLocation(ID)
                            .withCull(false)
                            .build();
            IrisApi.getInstance().assignPipeline(pipeline, IrisProgram.BLOCK);
        }
        return types.computeIfAbsent(
                texture,
                id ->
                        RenderTypeAccess.createType(
                                "ringworld_atlas_" + id.getPath(),
                                RenderSetup.builder(pipeline)
                                        .useLightmap()
                                        .withTexture("Sampler0", id)
                                        .createRenderSetup()));
    }
}
