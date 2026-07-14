package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.render.CurvedRingFrustum;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.ChunkRenderingDataPreparer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes terrain visibility use the same cylindrical space as its shader. */
@Mixin(ChunkRenderingDataPreparer.class)
abstract class ChunkRenderingDataPreparerMixin {
    @ModifyArg(
            method = "collectChunks",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/chunk/Octree;visit(Lnet/minecraft/client/render/chunk/Octree$Visitor;Lnet/minecraft/client/render/Frustum;I)V"),
            index = 1)
    private Frustum ringworld$curveCollectedChunkFrustum(Frustum frustum) {
        return ringworld$wrap(frustum);
    }

    @Redirect(
            method = "updateNow",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;offsetFrustum(Lnet/minecraft/client/render/Frustum;)Lnet/minecraft/client/render/Frustum;"))
    private Frustum ringworld$curveNewChunkFrustum(Frustum frustum) {
        return ringworld$wrap(WorldRenderer.offsetFrustum(frustum));
    }

    private static Frustum ringworld$wrap(Frustum frustum) {
        RingGeometry geometry = ClientRingState.geometry();
        MinecraftClient client = MinecraftClient.getInstance();
        if (geometry == null || client.gameRenderer == null
                || !client.gameRenderer.getCamera().isReady()) return frustum;
        Vec3d cameraPosition = client.gameRenderer.getCamera().getCameraPos();
        return new CurvedRingFrustum(frustum, geometry, cameraPosition);
    }
}
