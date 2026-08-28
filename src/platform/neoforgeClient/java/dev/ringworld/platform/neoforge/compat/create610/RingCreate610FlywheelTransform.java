package dev.ringworld.platform.neoforge.compat.create610;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingObjectTransform;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;

/** Create-free matrix composition used by the exact Flywheel adapter. */
public final class RingCreate610FlywheelTransform {
    private RingCreate610FlywheelTransform() { }

    public static void applyCurvedEmbedding(
            PoseStack matrices, Vec3 renderOriginRelativeAnchor,
            Vec3 cameraPosition, Vec3i renderOrigin, RingGeometry geometry) {
        Vec3 anchor = renderOriginRelativeAnchor.add(
                renderOrigin.getX(), renderOrigin.getY(), renderOrigin.getZ());
        RingObjectTransform transform = RingObjectTransform.fromCameraRelative(
                geometry, cameraPosition, anchor.x - cameraPosition.x,
                anchor.y - cameraPosition.y, anchor.z - cameraPosition.z);
        Vec3 renderLocal = transform.cameraLocalPosition().add(
                cameraPosition.x - renderOrigin.getX(),
                cameraPosition.y - renderOrigin.getY(),
                cameraPosition.z - renderOrigin.getZ());
        matrices.translate(renderLocal.x, renderLocal.y, renderLocal.z);
        matrices.mulPose(Axis.ZP.rotation((float) transform.tangentAngleRadians()));
    }
}
