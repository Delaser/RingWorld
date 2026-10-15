package dev.ringworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.OffRingVisibilityTestClient;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exterior void need not have a compiled terrain section to show entity models. */
@Mixin(LevelRenderer.class)
abstract class ExteriorEntityVisibilityMixin {
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiled(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean ringworld$visibleOutsideTerrain(LevelRenderer renderer, BlockPos pos) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry != null && (pos.getZ() < geometry.minWidthZ()
                || pos.getZ() > geometry.maxWidthZ())) return true;
        return renderer.isSectionCompiled(pos);
    }

    // Dormant fixture evidence: records entry into the native model-render pass.
    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void ringworld$recordFixtureRender(Entity entity, double cameraX, double cameraY,
            double cameraZ, float partialTick, PoseStack poses, MultiBufferSource buffers,
            CallbackInfo ci) {
        OffRingVisibilityTestClient.recordExtraction(entity);
    }
}
