package dev.ringworld.platform.neoforge.compat.create610.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.foundation.render.RenderTypes;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610ClientDiagnostics;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610ContraptionRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps Create's backend-OFF contraption mesh on an entity-space shader after
 * RingWorld's common entity renderer has already supplied the curved parent.
 */
@Mixin(value = ContraptionEntityRenderer.class, remap = false)
abstract class ContraptionEntityRendererMixin {
    @Redirect(
            method = "render(Lcom/simibubi/create/content/contraptions/"
                    + "AbstractContraptionEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/MultiBufferSource;"
                            + "getBuffer(Lnet/minecraft/client/renderer/RenderType;)"
                            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
            require = 1, expect = 1, allow = 1)
    private VertexConsumer ringworld$useEntitySpaceContraptionLayer(
            MultiBufferSource buffers, RenderType sourceLayer,
            @Local(argsOnly = true) AbstractContraptionEntity entity,
            @Local(name = "matrices") ContraptionMatrices matrices) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null || entity.level() != level || !level.isClientSide
                || level.dimension() != Level.OVERWORLD
                || ClientRingState.geometry() == null) {
            return buffers.getBuffer(sourceLayer);
        }
        RenderType mappedLayer = entitySpaceLayer(sourceLayer);
        if (RingCreate610ClientDiagnostics.offContraptionLayerDiagnosticsEnabled()) {
            RingCreate610ClientDiagnostics.recordOffContraptionLayer(
                    entity.getId(), sourceLayer.toString(), mappedLayer.toString(),
                    entityShaderKind(sourceLayer), isChunkTerrainLayer(mappedLayer),
                    matrices.getModelViewProjection().last().pose());
        }
        return buffers.getBuffer(mappedLayer);
    }

    private static RenderType entitySpaceLayer(RenderType sourceLayer) {
        if (sourceLayer == RenderType.solid()) {
            return RenderTypes.entitySolidBlockMipped();
        }
        if (sourceLayer == RenderType.cutoutMipped()) {
            return RenderTypes.entityCutoutBlockMipped();
        }
        if (sourceLayer == RenderType.cutout()) {
            return RingCreate610ContraptionRenderTypes.entityCutoutBlock();
        }
        if (sourceLayer == RenderType.translucent()) {
            return RingCreate610ContraptionRenderTypes.entityTranslucentBlock();
        }
        if (sourceLayer == RenderType.tripwire()) {
            return RingCreate610ContraptionRenderTypes.entityTripwireBlock();
        }
        return sourceLayer;
    }

    private static String entityShaderKind(RenderType sourceLayer) {
        if (sourceLayer == RenderType.solid()) return "entity-solid";
        if (sourceLayer == RenderType.cutoutMipped()
                || sourceLayer == RenderType.cutout()) return "entity-cutout";
        if (sourceLayer == RenderType.translucent()
                || sourceLayer == RenderType.tripwire()) return "entity-translucent-cull";
        return "unknown";
    }

    private static boolean isChunkTerrainLayer(RenderType layer) {
        return layer == RenderType.solid()
                || layer == RenderType.cutoutMipped()
                || layer == RenderType.cutout()
                || layer == RenderType.translucent()
                || layer == RenderType.tripwire();
    }
}
