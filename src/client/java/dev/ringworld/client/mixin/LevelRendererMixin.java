package dev.ringworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.render.RingDrawableSectionView;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingObjectTransform;
import dev.ringworld.world.RingStreamingProxyCoverage;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Curves object and interaction passes that bypass the terrain shader. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin implements RingDrawableSectionView {
    @Shadow @Final private ObjectArrayList<SectionRenderDispatcher.RenderSection>
            visibleSections;
    @Shadow @Nullable private Frustum capturedFrustum;

    @Shadow
    private static void renderShape(PoseStack poseStack, VertexConsumer vertices,
                                    VoxelShape shape, double x, double y, double z,
                                    float red, float green, float blue, float alpha) {
        throw new AssertionError();
    }

    @Redirect(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0))
    private void ringworld$curveBlockEntity(
            PoseStack poseStack, double x, double y, double z) {
        applyCurvedPose(poseStack, cameraPosition(), x, y, z);
    }

    @Redirect(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 1))
    private void ringworld$curveGlobalBlockEntity(
            PoseStack poseStack, double x, double y, double z) {
        applyCurvedPose(poseStack, cameraPosition(), x, y, z);
    }

    @Redirect(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 2))
    private void ringworld$curveBlockBreaking(
            PoseStack poseStack, double x, double y, double z) {
        applyCurvedPose(poseStack, cameraPosition(), x, y, z);
    }

    @Redirect(
            method = "renderHitOutline",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V"))
    private void ringworld$curveBlockOutline(
            PoseStack poseStack, VertexConsumer vertices, VoxelShape shape,
            double x, double y, double z,
            float red, float green, float blue, float alpha) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) {
            renderShape(poseStack, vertices, shape, x, y, z, red, green, blue, alpha);
            return;
        }

        poseStack.pushPose();
        applyCurvedPose(poseStack, cameraPosition(), x, y, z);
        renderShape(poseStack, vertices, shape, 0.0, 0.0, 0.0,
                red, green, blue, alpha);
        poseStack.popPose();
    }

    private static Vec3 cameraPosition() {
        return net.minecraft.client.Minecraft.getInstance().gameRenderer
                .getMainCamera().getPosition();
    }

    private static void applyCurvedPose(
            PoseStack poseStack, Vec3 camera, double x, double y, double z) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) {
            poseStack.translate(x, y, z);
            return;
        }
        RingObjectTransform transform = RingObjectTransform.fromCameraRelative(
                geometry, camera, x, y, z);
        Vec3 local = transform.cameraLocalPosition();
        poseStack.translate(local.x, local.y, local.z);
        poseStack.mulPose(Axis.ZP.rotation((float)transform.tangentAngleRadians()));
    }

    @Override
    public boolean ringworld$hasCompiledSectionsInsideProxyHole(
            RingGeometry geometry, Vec3 cameraPosition, int effectiveChunks,
            double baseProxyOpaqueFromBlocks) {
        ClientLevel level = Minecraft.getInstance().level;
        if (capturedFrustum != null || level == null || visibleSections.isEmpty()) {
            return false;
        }

        for (SectionRenderDispatcher.RenderSection section : visibleSections) {
            BlockPos origin = section.getOrigin();
            int chunkX = origin.getX() >> 4;
            int chunkZ = origin.getZ() >> 4;
            // The finite-band view graph can retain exterior placeholders so
            // traversal reaches real rim sections. They never own live ring
            // terrain and have no LevelChunk to compile.
            if (geometry.isExteriorChunkZ(chunkZ)) {
                continue;
            }
            if (section.getCompiled()
                    != SectionRenderDispatcher.CompiledSection.UNCOMPILED) {
                continue;
            }
            var bounds = section.getBoundingBox();
            if (!RingStreamingProxyCoverage.intersectsNonOpaqueProxyRegion(
                    geometry, cameraPosition.x, cameraPosition.z,
                    bounds.minX, bounds.maxX, bounds.minZ, bounds.maxZ,
                    baseProxyOpaqueFromBlocks)) {
                continue;
            }

            LevelChunk chunk = level.getChunkSource().getChunk(
                    chunkX, chunkZ, ChunkStatus.FULL, false);
            if (chunk == null) {
                return false;
            }
            int sectionIndex = level.getSectionIndex(origin.getY());
            if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
                return false;
            }
            if (chunk.getSection(sectionIndex).hasOnlyAir()) {
                continue;
            }

            return false;
        }
        return true;
    }
}
